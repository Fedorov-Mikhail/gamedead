package com.gamedead.client.net;

import com.gamedead.protocol.account.PlayerProfileResponse;
import com.gamedead.protocol.auth.*;
import java.util.concurrent.CompletableFuture;

public interface GameApi extends AutoCloseable {
    CompletableFuture<RegisterResponse> register(RegisterRequest request);
    CompletableFuture<LoginResponse> login(LoginRequest request);
    CompletableFuture<PlayerProfileResponse> profile(String token);
    CompletableFuture<Void> logout(String token);
    @Override void close();
}
