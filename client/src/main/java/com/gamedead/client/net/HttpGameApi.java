package com.gamedead.client.net;

import com.gamedead.protocol.account.PlayerProfileResponse;
import com.gamedead.protocol.auth.*;
import com.gamedead.protocol.error.ApiError;
import com.gamedead.protocol.room.*;
import java.util.function.Consumer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class HttpGameApi implements GameApi {
    private final URI base;
    private final Duration timeout;
    private final ExecutorService executor;
    private final HttpClient client;
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final Set<SocketRoomChannel> channels=ConcurrentHashMap.newKeySet();
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private volatile boolean closed;

    public HttpGameApi(String serverUrl) { this(serverUrl, Duration.ofSeconds(10)); }
    public HttpGameApi(String serverUrl, Duration timeout) {
        try {
            base = URI.create(serverUrl);
            if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                    || base.getHost() == null || base.getUserInfo() != null || base.getRawQuery() != null
                    || base.getRawFragment() != null || !(base.getPath().isEmpty() || base.getPath().equals("/"))
                    || base.getPort() > 65535 || base.getPort() == 0) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Укажи адрес сервера, например http://127.0.0.1:8080");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Invalid timeout");
        this.timeout = timeout;
        executor = Executors.newVirtualThreadPerTaskExecutor();
        client = HttpClient.newBuilder().executor(executor).connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public CompletableFuture<RegisterResponse> register(RegisterRequest request) {
        return send("/api/v1/auth/register", request, null, 201, RegisterResponse.class);
    }
    @Override public CompletableFuture<LoginResponse> login(LoginRequest request) {
        return send("/api/v1/auth/login", request, null, 200, LoginResponse.class);
    }
    @Override public CompletableFuture<PlayerProfileResponse> profile(String token) {
        return send("/api/v1/me", null, token, 200, PlayerProfileResponse.class);
    }
    @Override public CompletableFuture<Void> logout(String token) {
        return send("/api/v1/auth/logout", "", token, 204, Void.class);
    }
    @Override public CompletableFuture<RoomStateResponse> room(String token,String action,Object body) {
        if(!Set.of("me","create","join","ready","start","stop","leave").contains(action))throw new IllegalArgumentException("Unknown room operation");
        return send("/api/v1/rooms/"+action,action.equals("me")?null:body==null?"":body,token,action.equals("create")?201:200,RoomStateResponse.class);
    }
    @Override public CompletableFuture<RoomChannel> connectRooms(String token,Consumer<RoomEvent> events,Consumer<ApiFailure> failure) {
        if(closed)return CompletableFuture.failedFuture(new ApiFailure(0,"Клиент закрыт."));
        URI uri=URI.create((base.getScheme().equals("https")?"wss":"ws")+"://"+base.getRawAuthority()+"/ws/game");
        SocketRoomChannel channel=new SocketRoomChannel(json,events,failure);
        channel.onClosed(()->channels.remove(channel));
        channels.add(channel);
        var wire=client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).buildAsync(uri,channel);
        wire.whenComplete((socket,error)->{if(error!=null)channel.failed(error);});
        var result=channel.ready().orTimeout(10,TimeUnit.SECONDS);
        result.whenComplete((socket,error)->{if(error!=null){wire.cancel(true);channel.close();channels.remove(channel);}if(closed)channel.close();});
        return result;
    }

    private <T> CompletableFuture<T> send(String path, Object body, String token, int expected, Class<T> type) {
        if (closed) return CompletableFuture.failedFuture(new ApiFailure(0, "Клиент закрыт."));
        var builder = HttpRequest.newBuilder(base.resolve(path)).timeout(timeout).header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body == null) builder.GET();
        else builder.header("Content-Type", "application/json; charset=UTF-8").POST(
                HttpRequest.BodyPublishers.ofString(body instanceof String ? "" : json.writeValueAsString(body), StandardCharsets.UTF_8));
        var wire = client.sendAsync(builder.build(), info -> new LimitedBodySubscriber(65_536));
        pending.add(wire);
        wire.whenComplete((response, error) -> pending.remove(wire));
        // HttpRequest's timeout alone need not cover a response body that has already started.
        var deadline = wire.copy().orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        var result = deadline.handle((response, error) -> {
            if (error != null) {
                wire.cancel(true);
                throw safeFailure(error);
            }
            if (response.statusCode() != expected) throw responseFailure(response);
            if (type == Void.class) return null;
            try {
                T value = json.readValue(response.body(), type);
                validate(value);
                return value;
            } catch (RuntimeException e) {
                throw new ApiFailure(0, "Сервер прислал некорректный ответ. Попробуй позже.");
            }
        });
        result.whenComplete((response, error) -> { if (result.isCancelled()) wire.cancel(true); });
        return result;
    }

    private void validate(Object value) {
        boolean valid = switch (value) {
            case LoginResponse r -> r.accessToken() != null && !r.accessToken().isBlank()
                    && r.accessToken().length() <= 1024 && r.accessToken().chars().allMatch(c -> c > 32 && c < 127)
                    && "Bearer".equals(r.tokenType()) && r.expiresAt() != null;
            case RegisterResponse r -> r.accountId() != null && r.login() != null && r.displayName() != null;
            case PlayerProfileResponse r -> r.accountId() != null && r.login() != null && r.displayName() != null && r.accountXp() >= 0;
            case RoomStateResponse r -> RoomMessages.valid(r);
            case null, default -> false;
        };
        if (!valid) throw new IllegalArgumentException("Incomplete response");
    }

    private ApiFailure responseFailure(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        String message = switch (status) {
            case 400 -> "Проверь заполнение полей.";
            case 401 -> "Неверный логин или пароль, либо срок сессии истёк.";
            case 409 -> "Этот логин уже занят. Выбери другой.";
            case 429 -> "Слишком много попыток. " + retryMessage(response);
            default -> status >= 500 ? "Сервер временно недоступен. Попробуй позже."
                    : "Неожиданный ответ сервера. Проверь его адрес и версию.";
        };
        Map<String, String> fields = new LinkedHashMap<>();
        try {
            var error=json.readValue(response.body(),ApiError.class);
            message=switch(error.code()) {
                case "ROOM_FULL" -> "В комнате уже четыре игрока.";
                case "ROOM_NOT_FOUND" -> "Комната не найдена. Проверь код.";
                case "ALREADY_IN_ROOM" -> "Ты уже состоишь в комнате.";
                case "ROOM_PLAYING" -> "В комнате уже началась тренировка.";
                case "ROOM_NOT_READY" -> "Нужны хотя бы два подключённых игрока. Все должны нажать «Готов».";
                case "NOT_OWNER" -> "Это действие доступно владельцу комнаты.";
                case "NOT_CONNECTED" -> "Игровое соединение ещё не установлено. Нажми «Подключиться».";
                case "INVALID_ROOM_CODE" -> "Введи код комнаты: 6 букв или цифр.";
                case "SERVER_BUSY" -> "Сервер занят. Попробуй позже.";
                default -> message;
            };
        } catch(RuntimeException ignored) { }
        if (status == 400) {
            try {
                var error = json.readValue(response.body(), ApiError.class);
                error.fieldErrors().forEach((key, text) -> {
                    if (Set.of("login", "displayName", "password").contains(key) && text.length() <= 256) {
                        fields.put(key, text);
                    }
                });
            } catch (RuntimeException ignored) { /* Malformed error bodies still yield a safe status message. */ }
        }
        return new ApiFailure(status, message, fields);
    }

    private String retryMessage(HttpResponse<?> response) {
        String retry = response.headers().firstValue("Retry-After").orElse("");
        try {
            long seconds = Long.parseLong(retry);
            if (seconds > 0 && seconds <= 86400) return "Повтори через " + seconds + " сек.";
        } catch (NumberFormatException ignored) { }
        return "Подожди немного и повтори.";
    }

    private ApiFailure safeFailure(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) error = error.getCause();
        if (error instanceof ApiFailure failure) return failure;
        if (error instanceof TimeoutException || error instanceof HttpTimeoutException) {
            return new ApiFailure(0, "Сервер не ответил вовремя. Повтори попытку.");
        }
        return new ApiFailure(0, "Не удалось связаться с сервером. Проверь, что он запущен и адрес указан верно.");
    }

    @Override public void close() {
        closed = true;
        pending.forEach(request -> request.cancel(true));
        channels.forEach(SocketRoomChannel::close);channels.clear();
        client.shutdownNow();
        executor.shutdownNow();
    }
}
