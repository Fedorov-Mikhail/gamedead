package com.gamedead.client.net;

import com.gamedead.protocol.auth.*;
import com.gamedead.protocol.room.JoinRoomRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class HttpGameApiTest {
    private static final String ACCOUNT = "c28e03ef-f924-4dbd-afc7-9f93e7b70db6";
    private HttpServer server;
    private ExecutorService executor;
    private HttpGameApi api;
    private String url;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
        api = new HttpGameApi(url);
    }

    @AfterEach void stop() {
        api.close();
        server.stop(0);
        executor.shutdownNow();
    }

    @Test void registrationPreservesUnicodePasswordAndUsesApiRouteWithoutAuthorization() {
        route("/api/v1/auth/register", 201, "{\"accountId\":\"" + ACCOUNT
                + "\",\"login\":\"mage\",\"displayName\":\"Маг Ёж\"}");
        var response = api.register(new RegisterRequest("mage", "Маг Ёж", "  пароль\r\n🔥  ")).join();
        assertThat(response.displayName()).isEqualTo("Маг Ёж");
        assertThat(json.readTree(body.get()).get("password").asString()).isEqualTo("  пароль\r\n🔥  ");
        assertThat(authorization.get()).isNull();
    }

    @Test void loginReadsExpirationWithoutAuthorizingTheRequest() {
        route("/api/v1/auth/login", 200, "{\"accessToken\":\"secret-session\",\"tokenType\":\"Bearer\","
                + "\"expiresAt\":\"2099-10-15T12:00:00Z\"}");
        var response = api.login(new LoginRequest("mage", "password")).join();
        assertThat(response.accessToken()).isEqualTo("secret-session");
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2099-10-15T12:00:00Z"));
        assertThat(authorization.get()).isNull();
    }

    @Test void profileUsesBearerAndReadsSavedProgress() {
        route("/api/v1/me", 200, "{\"accountId\":\"" + ACCOUNT + "\",\"login\":\"mage\","
                + "\"displayName\":\"[gold]Маг\",\"accountXp\":42,\"appearanceId\":\"default\"}");
        var response = api.profile("session-value").join();
        assertThat(response.accountXp()).isEqualTo(42);
        assertThat(response.displayName()).isEqualTo("[gold]Маг");
        assertThat(authorization.get()).isEqualTo("Bearer session-value");
    }

    @Test void logoutAcceptsNoContent() {
        route("/api/v1/auth/logout", 204, "");
        assertThat(api.logout("session-value").join()).isNull();
        assertThat(authorization.get()).isEqualTo("Bearer session-value");
    }
    @Test void roomJoinUsesAuthenticatedJsonAndReadsVersionedState(){
        route("/api/v1/rooms/join",200,"{\"revision\":12,\"room\":{\"code\":\"ABC234\",\"version\":12,\"ownerId\":\""+ACCOUNT+"\",\"status\":\"LOBBY\",\"members\":[{\"accountId\":\""+ACCOUNT+"\",\"displayName\":\"Маг\",\"ready\":false,\"connected\":true,\"color\":0}]}}");
        var state=api.room("session-value","join",new JoinRoomRequest("ABC234")).join();
        assertThat(state.room().members().getFirst().displayName()).isEqualTo("Маг");
        assertThat(authorization.get()).isEqualTo("Bearer session-value");
        assertThat(json.readTree(body.get()).get("code").asString()).isEqualTo("ABC234");
    }
    @Test void fullRoomIsNotReportedAsTakenLogin(){
        route("/api/v1/rooms/join",409,"{\"code\":\"ROOM_FULL\",\"message\":\"internal details\",\"fieldErrors\":{}}");
        assertThat(failure(api.room("token","join",new JoinRoomRequest("ABC234"))).getMessage()).contains("четыре игрока").doesNotContain("логин","internal");
    }

    @ParameterizedTest @ValueSource(ints = {400, 401, 409, 415, 429, 500})
    void errorsHaveSafeMessagesAndRetainStatus(int status) {
        server.createContext("/api/v1/auth/login", exchange -> {
            exchange.getResponseHeaders().set("Retry-After", "17");
            reply(exchange, status, "{\"code\":\"ERROR\",\"message\":\"server internals\","
                    + "\"fieldErrors\":{\"login\":\"Логин занят\"},\"futureField\":true}");
        });
        ApiFailure failure = failure(api.login(new LoginRequest("mage", "password")));
        assertThat(failure.status()).isEqualTo(status);
        assertThat(failure.getMessage()).isNotBlank().doesNotContain("server internals");
        if (status == 400) assertThat(failure.fieldErrors()).containsEntry("login", "Логин занят");
        if (status == 429) assertThat(failure.getMessage()).contains("17");
    }

    @Test void redirectNeverForwardsTokenToAnotherRoute() {
        var forwarded = new AtomicReference<String>();
        server.createContext("/capture", exchange -> {
            forwarded.set(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, 200, "{}");
        });
        server.createContext("/api/v1/me", exchange -> {
            exchange.getResponseHeaders().set("Location", url + "/capture");
            reply(exchange, 302, "");
        });
        assertThat(failure(api.profile("must-not-forward")).status()).isEqualTo(302);
        assertThat(forwarded.get()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"not json", "{}", "{\"accessToken\":null}"})
    void malformedSuccessfulLoginCannotBecomeSession(String response) {
        route("/api/v1/auth/login", 200, response);
        assertThat(failure(api.login(new LoginRequest("mage", "password"))).getMessage()).isNotBlank();
    }

    @Test void oversizedResponseIsRejected() {
        route("/api/v1/me", 200, "x".repeat(65_537));
        assertThat(failure(api.profile("token")).getMessage()).isNotBlank();
    }

    @Test void deadlineIncludesSlowResponseBody() {
        api.close();
        api = new HttpGameApi(url, Duration.ofMillis(350));
        server.createContext("/api/v1/me", exchange -> {
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        long started = System.nanoTime();
        assertThat(failure(api.profile("token")).getMessage()).isNotBlank();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1500));
    }

    @Test void unavailableServerIsReportedWithoutRawException() {
        server.stop(0);
        ApiFailure failure = failure(api.profile("token"));
        assertThat(failure.status()).isZero();
        assertThat(failure.getMessage()).doesNotContain("Exception", "java.");
    }

    @ParameterizedTest @ValueSource(strings = {"ftp://localhost", "http://user:pass@localhost", "http://localhost?secret=x",
            "http://localhost#fragment", "http://localhost/other", "not a url"})
    void invalidServerAddressIsRejectedBeforeRequests(String address) {
        assertThatIllegalArgumentException().isThrownBy(() -> new HttpGameApi(address));
    }

    private ApiFailure failure(CompletableFuture<?> request) {
        try { request.join(); throw new AssertionError("Expected failed request"); }
        catch (CompletionException e) {
            assertThat(e.getCause()).isInstanceOf(ApiFailure.class);
            return (ApiFailure) e.getCause();
        }
    }

    private void route(String path, int status, String response) {
        server.createContext(path, exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertThat(exchange.getRequestMethod()).isEqualTo(path.endsWith("/me") ? "GET" : "POST");
            reply(exchange, status, response);
        });
    }

    private void reply(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length == 0 ? -1 : bytes.length);
        try (var stream = exchange.getResponseBody()) { if (status != 204) stream.write(bytes); }
    }
}
