package com.gamedead.client.net;

import com.gamedead.protocol.account.PlayerProfileResponse;
import com.gamedead.protocol.auth.*;
import com.gamedead.protocol.match.MatchHistoryResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import com.gamedead.protocol.room.*;

public interface GameApi extends AutoCloseable {
    CompletableFuture<RegisterResponse> register(RegisterRequest request);
    CompletableFuture<LoginResponse> login(LoginRequest request);
    CompletableFuture<PlayerProfileResponse> profile(String token);
    CompletableFuture<Void> logout(String token);
    default CompletableFuture<MatchHistoryResponse> history(String token){return CompletableFuture.failedFuture(new ApiFailure(0,"История недоступна."));}
    default CompletableFuture<RoomStateResponse> room(String token,String action,Object body){return CompletableFuture.failedFuture(new ApiFailure(0,"Комнаты недоступны."));}
    default CompletableFuture<RoomChannel> connectRooms(String token,Consumer<RoomEvent> events,Consumer<ApiFailure> failure){return CompletableFuture.failedFuture(new ApiFailure(0,"Комнаты недоступны."));}
    @Override void close();
}
