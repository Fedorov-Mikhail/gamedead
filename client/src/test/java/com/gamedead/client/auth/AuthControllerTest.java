package com.gamedead.client.auth;

import com.gamedead.client.net.*;
import com.gamedead.protocol.account.PlayerProfileResponse;
import com.gamedead.protocol.auth.*;
import com.gamedead.protocol.match.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class AuthControllerTest {
    private final ControlledApi api = new ControlledApi();
    private final List<AuthState> changes = new ArrayList<>();
    private final AuthController controller = new AuthController(api, Runnable::run, changes::add);
    private final PlayerProfileResponse profile = new PlayerProfileResponse(
            UUID.fromString("c28e03ef-f924-4dbd-afc7-9f93e7b70db6"), "mage", "Ёж маг", 42, "default");

    @AfterEach void close() { controller.close(); }

    @Test void registrationReturnsToLoginWithoutCreatingSession() {
        controller.showRegister();
        controller.register("mage", "Ёж маг", "  пароль 🔥  ", "  пароль 🔥  ");
        assertThat(controller.state().busy()).isTrue();
        assertThat(api.registration.password()).isEqualTo("  пароль 🔥  ");
        api.registered.complete(new RegisterResponse(profile.accountId(), "mage", "Ёж маг"));
        assertThat(controller.state().view()).isEqualTo(AuthState.View.LOGIN);
        assertThat(controller.state().login()).isEqualTo("mage");
        assertThat(controller.state().error()).isFalse();
        assertThat(api.loginCalls).isZero();
        assertThat(api.profileCalls).isZero();
    }

    @Test void mismatchedConfirmationNeverLeavesRegistration() {
        controller.showRegister();
        controller.register("mage", "Ёж маг", "password123", "different123");
        assertThat(controller.state().error()).isTrue();
        assertThat(controller.state().fieldErrors()).containsKey("confirmation");
        assertThat(controller.state().view()).isEqualTo(AuthState.View.REGISTER);
        assertThat(api.registration).isNull();
    }

    @Test void overlongUtf8PasswordGetsLocalValidationError() {
        controller.showRegister();
        controller.register("mage", "Ёж маг", "🔥".repeat(19), "🔥".repeat(19));
        assertThat(controller.state().fieldErrors()).containsKey("password");
        assertThat(api.registration).isNull();
    }

    @Test void menuOpensOnlyAfterProfileAndDoubleClickDoesNotCreateTwoSessions() {
        controller.login("mage", "  password  ");
        controller.login("mage", "  password  ");
        assertThat(api.loginCalls).isEqualTo(1);
        assertThat(api.credentials.password()).isEqualTo("  password  ");
        finishLogin();
        assertThat(controller.state().view()).isEqualTo(AuthState.View.LOGIN);
        assertThat(controller.state().busy()).isTrue();
        assertThat(api.profileToken).isEqualTo("session-secret");
        api.loaded.complete(profile);
        assertThat(controller.state().view()).isEqualTo(AuthState.View.PROFILE);
        assertThat(controller.state().profile().accountXp()).isEqualTo(42);
        assertThat(controller.state().toString()).doesNotContain("session-secret", "password");
    }

    @Test void failedProfileAfterLoginNeverOpensMenu() {
        controller.login("mage", "password123");
        finishLogin();
        api.loaded.completeExceptionally(new ApiFailure(401, "Сессия истекла"));
        assertThat(controller.state().view()).isEqualTo(AuthState.View.LOGIN);
        assertThat(controller.state().profile()).isNull();
        assertThat(controller.state().busy()).isFalse();
        assertThat(controller.state().error()).isTrue();
    }

    @Test void unauthorizedRefreshReturnsToLogin() {
        openProfile();
        api.loaded = new CompletableFuture<>();
        controller.refreshProfile();
        api.loaded.completeExceptionally(new ApiFailure(401, "Сессия истекла"));
        assertThat(controller.state().view()).isEqualTo(AuthState.View.LOGIN);
        assertThat(controller.state().profile()).isNull();
    }

    @Test void networkErrorDuringRefreshKeepsExistingProfile() {
        openProfile();
        api.loaded = new CompletableFuture<>();
        controller.refreshProfile();
        api.loaded.completeExceptionally(new ApiFailure(0, "Нет соединения"));
        assertThat(controller.state().view()).isEqualTo(AuthState.View.PROFILE);
        assertThat(controller.state().profile()).isEqualTo(profile);
        assertThat(controller.state().busy()).isFalse();
    }

    @Test void logoutImmediatelyClearsProfileEvenIfServerIsUnavailable() {
        openProfile();
        controller.logout();
        assertThat(controller.state().view()).isEqualTo(AuthState.View.LOGIN);
        assertThat(controller.state().profile()).isNull();
        assertThat(api.logoutToken).isEqualTo("session-secret");
        api.loggedOut.completeExceptionally(new ApiFailure(0, "Нет соединения"));
        assertThat(controller.state().busy()).isFalse();
        assertThat(controller.state().message()).contains("сервер");
        assertThat(controller.state().profile()).isNull();
    }

    @Test void lateRefreshCannotRestoreProfileAfterLogout() {
        openProfile();
        api.loaded = new CompletableFuture<>() { @Override public boolean cancel(boolean ignored) { return false; } };
        controller.refreshProfile();
        controller.logout();
        api.loggedOut.complete(null);
        api.loaded.complete(profile);
        assertThat(controller.state().view()).isEqualTo(AuthState.View.LOGIN);
        assertThat(controller.state().profile()).isNull();
    }

    @Test void completionIsDispatchedToUiThreadAndIgnoredAfterClose() {
        Queue<Runnable> ui = new ArrayDeque<>();
        var other = new AuthController(api, ui::add, changes::add);
        other.login("mage", "password123");
        finishLogin();
        assertThat(other.state().busy()).isTrue();
        assertThat(ui).isNotEmpty();
        other.close();
        int count = changes.size();
        while (!ui.isEmpty()) ui.remove().run();
        assertThat(changes).hasSize(count);
        assertThat(other.state().profile()).isNull();
    }

    @Test void serverFieldErrorsAreVisibleAndFormUnlocks() {
        controller.showRegister();
        controller.register("mage", "Ёж маг", "password123", "password123");
        api.registered.completeExceptionally(new ApiFailure(400, "Проверь поля", Map.of("login", "Ошибка логина")));
        assertThat(controller.state().fieldErrors()).containsEntry("login", "Ошибка логина");
        assertThat(controller.state().busy()).isFalse();
    }

    @Test void historyUsesSessionAndBackReloadsAccountXp() {
        openProfile();
        controller.showHistory();
        assertThat(api.historyToken).isEqualTo("session-secret");
        assertThat(controller.state().view()).isEqualTo(AuthState.View.HISTORY);
        api.history.complete(new MatchHistoryResponse(List.of()));
        assertThat(controller.state().busy()).isFalse();
        assertThat(controller.state().matches()).isEmpty();
        api.loaded=new CompletableFuture<>();
        controller.backToProfile();
        assertThat(api.profileCalls).isEqualTo(2);
        api.loaded.complete(new PlayerProfileResponse(profile.accountId(),"mage","Ёж маг",120,"default"));
        assertThat(controller.state().profile().accountXp()).isEqualTo(120);
        assertThat(controller.state().view()).isEqualTo(AuthState.View.PROFILE);
    }

    @Test void lateHistoryCannotRestoreSessionAfterLogout() {
        openProfile();
        api.history=new CompletableFuture<>(){@Override public boolean cancel(boolean ignored){return false;}};
        controller.showHistory();controller.logout();api.loggedOut.complete(null);
        api.history.complete(new MatchHistoryResponse(List.of()));
        assertThat(controller.state().view()).isEqualTo(AuthState.View.LOGIN);
        assertThat(controller.state().profile()).isNull();
        assertThat(controller.state().matches()).isEmpty();
    }

    private void finishLogin() {
        api.authenticated.complete(new LoginResponse("session-secret", "Bearer", Instant.parse("2099-01-01T00:00:00Z")));
    }
    private void openProfile() {
        controller.login("mage", "password123");
        finishLogin();
        api.loaded.complete(profile);
        assertThat(controller.state().view()).isEqualTo(AuthState.View.PROFILE);
    }

    // Only the asynchronous boundary is controlled; the controller and its transitions are real.
    private static final class ControlledApi implements GameApi {
        CompletableFuture<RegisterResponse> registered = new CompletableFuture<>();
        CompletableFuture<LoginResponse> authenticated = new CompletableFuture<>();
        CompletableFuture<PlayerProfileResponse> loaded = new CompletableFuture<>();
        CompletableFuture<Void> loggedOut = new CompletableFuture<>();
        CompletableFuture<MatchHistoryResponse> history = new CompletableFuture<>();
        String historyToken;
        RegisterRequest registration;
        LoginRequest credentials;
        int loginCalls;
        int profileCalls;
        String profileToken;
        String logoutToken;
        public CompletableFuture<RegisterResponse> register(RegisterRequest request) { registration = request; return registered; }
        public CompletableFuture<LoginResponse> login(LoginRequest request) { credentials = request; loginCalls++; return authenticated; }
        public CompletableFuture<PlayerProfileResponse> profile(String token) { profileToken = token; profileCalls++; return loaded; }
        public CompletableFuture<Void> logout(String token) { logoutToken = token; return loggedOut; }
        public CompletableFuture<MatchHistoryResponse> history(String token) { historyToken=token;return history; }
        public void close() { }
    }
}
