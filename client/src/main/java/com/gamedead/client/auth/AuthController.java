package com.gamedead.client.auth;

import com.gamedead.client.net.*;
import com.gamedead.protocol.auth.*;
import com.gamedead.protocol.account.PlayerProfileResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.gamedead.client.net.RoomChannel;
import com.gamedead.protocol.room.*;

public final class AuthController implements AutoCloseable {
    private AuthState state = new AuthState(AuthState.View.LOGIN, false, "", "", false, Map.of(), null);
    private final GameApi api;
    private final Executor ui;
    private final Consumer<AuthState> listener;
    private CompletableFuture<?> active;
    private String token;
    private long generation;
    private volatile boolean closed;

    public AuthController(GameApi api, Executor ui, Consumer<AuthState> listener) {
        this.api = api;
        this.ui = ui;
        this.listener = listener;
    }
    public AuthState state() { return state; }
    public CompletableFuture<RoomStateResponse> room(String action,Object body) {
        if(closed || token==null || state.view()!=AuthState.View.PROFILE)return CompletableFuture.failedFuture(new ApiFailure(401,"Сначала войди в аккаунт."));
        return api.room(token,action,body);
    }
    public CompletableFuture<RoomChannel> connectRooms(Consumer<RoomEvent> events,Consumer<ApiFailure> failure) {
        if(closed || token==null || state.view()!=AuthState.View.PROFILE)return CompletableFuture.failedFuture(new ApiFailure(401,"Сначала войди в аккаунт."));
        return api.connectRooms(token,events,failure);
    }
    public void showRegister() { navigate(AuthState.View.REGISTER); }
    public void showLogin() { navigate(AuthState.View.LOGIN); }

    private void navigate(AuthState.View view) {
        if (closed || state.busy() || token!=null) return;
        invalidate();
        publish(view, false, state.login(), "", false, Map.of(), null);
    }

    public void register(String login, String name, String password, String confirmation) {
        if (closed || state.busy() || state.view() != AuthState.View.REGISTER) return;
        String normalized = login.strip().toLowerCase(Locale.ROOT);
        String displayName = name.strip();
        var errors = new LinkedHashMap<String, String>();
        if (!normalized.matches("[a-z0-9_]{3,24}")) errors.put("login", "Логин: 3–24 латинских буквы, цифры или _.");
        int length = displayName.codePointCount(0, displayName.length());
        if (length < 3 || length > 24 || displayName.codePoints().anyMatch(Character::isISOControl)) {
            errors.put("displayName", "Имя: от 3 до 24 символов без управляющих знаков.");
        }
        if (password.codePointCount(0, password.length()) < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            errors.put("password", "Пароль: от 8 символов, не больше 72 байт UTF-8.");
        }
        if (!password.equals(confirmation)) errors.put("confirmation", "Пароли не совпадают.");
        if (!errors.isEmpty()) {
            publish(state.view(), false, normalized, "Проверь заполнение полей.", true, errors, null);
            return;
        }
        long operation = begin(normalized, "Создаём аккаунт…");
        watch(api.register(new RegisterRequest(normalized, displayName, password)), operation, (registered, failure) -> {
            if (failure != null) fail(failure, false);
            else publish(AuthState.View.LOGIN, false, registered.login(), "Аккаунт создан. Теперь можно войти.", false, Map.of(), null);
        });
    }

    public void login(String login, String password) {
        if (closed || state.busy() || state.view() != AuthState.View.LOGIN) return;
        if (login.isBlank() || password.isEmpty()) {
            publish(state.view(), false, login, "Введи логин и пароль.", true, Map.of(), null);
            return;
        }
        long operation = begin(login.strip(), "Входим в аккаунт…");
        watch(api.login(new LoginRequest(login.strip(), password)), operation, (response, failure) -> {
            if (failure != null) { fail(failure, true); return; }
            if (!response.expiresAt().isAfter(Instant.now())) {
                fail(new ApiFailure(401, "Срок сессии уже истёк. Проверь время на компьютере и повтори вход."), true);
                return;
            }
            token = response.accessToken();
            publish(state.view(), true, state.login(), "Загружаем профиль…", false, Map.of(), null);
            loadProfile(operation, true);
        });
    }

    public void refreshProfile() {
        if (closed || state.busy() || state.view() != AuthState.View.PROFILE || token == null) return;
        loadProfile(begin(state.login(), "Обновляем профиль…"), false);
    }

    public void showHistory() {
        if(closed || token==null || state.busy() || state.view()!=AuthState.View.PROFILE)return;
        invalidate();long operation=generation;
        publish(AuthState.View.HISTORY,true,state.login(),"Загружаем историю…",false,Map.of(),state.profile());
        watch(api.history(token),operation,(history,failure)->{
            if(failure!=null){fail(failure,failure.status()==401);return;}
            state=new AuthState(AuthState.View.HISTORY,false,state.login(),"",false,Map.of(),state.profile(),history.matches());
            listener.accept(state);
        });
    }

    public void backToProfile() {
        if(closed || token==null || state.view()!=AuthState.View.HISTORY)return;
        invalidate();long operation=generation;
        publish(AuthState.View.PROFILE,true,state.login(),"Обновляем профиль…",false,Map.of(),state.profile());
        loadProfile(operation,false);
    }

    private void loadProfile(long operation, boolean entering) {
        watch(api.profile(token), operation, (profile, failure) -> {
            if (failure != null) fail(failure, entering || failure.status() == 401);
            else publish(AuthState.View.PROFILE, false, profile.login(), "", false, Map.of(), profile);
        });
    }

    public void logout() {
        if (closed || token == null) return;
        String previous = token;
        token = null;
        invalidate();
        long operation = generation;
        publish(AuthState.View.LOGIN, true, state.login(), "Выходим из аккаунта…", false, Map.of(), null);
        watch(api.logout(previous), operation, (nothing, failure) -> {
            String message = failure == null ? "Ты вышел из аккаунта."
                    : "Ты вышел на этом компьютере. Подтвердить выход на сервере не удалось; сессия там истечёт автоматически.";
            publish(AuthState.View.LOGIN, false, state.login(), message, failure != null, Map.of(), null);
        });
    }

    private long begin(String login, String message) {
        invalidate();
        publish(state.view(), true, login, message, false, Map.of(), state.profile());
        return generation;
    }

    private <T> void watch(CompletableFuture<T> request, long operation, BiConsumer<T, ApiFailure> completion) {
        active = request;
        request.whenComplete((value, error) -> {
            if (closed) return;
            ui.execute(() -> {
                if (closed || generation != operation) return;
                active = null;
                Throwable cause = error;
                while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                ApiFailure failure = cause == null ? null : cause instanceof ApiFailure f ? f
                        : new ApiFailure(0, "Не удалось выполнить запрос. Попробуй ещё раз.");
                completion.accept(value, failure);
            });
        });
    }

    private void fail(ApiFailure failure, boolean clearSession) {
        if (clearSession) token = null;
        publish(clearSession ? AuthState.View.LOGIN : state.view(), false, state.login(), failure.getMessage(),
                true, failure.fieldErrors(), clearSession ? null : state.profile());
    }

    private void publish(AuthState.View view, boolean busy, String login, String message, boolean error,
                         Map<String, String> fields, PlayerProfileResponse profile) {
        state = new AuthState(view, busy, login, message, error, fields, profile);
        listener.accept(state);
    }

    private void invalidate() {
        generation++;
        if (active != null) { active.cancel(true); active = null; }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        invalidate();
        token = null;
        state = new AuthState(AuthState.View.LOGIN, false, state.login(), "", false, Map.of(), null);
        api.close();
    }
}
