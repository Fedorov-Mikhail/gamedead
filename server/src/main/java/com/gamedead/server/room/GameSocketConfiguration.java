package com.gamedead.server.room;

import com.gamedead.server.auth.SessionService;
import com.gamedead.server.account.AccountService;
import org.springframework.context.annotation.*;

@Configuration
public class GameSocketConfiguration {
    @Bean GameSocketServer gameSocketServer(RoomRegistry rooms,SessionService sessions,AccountService accounts){
        return new GameSocketServer(rooms,p->accounts.getProfile(p.accountId()).displayName(),p->{sessions.validatePrincipal(p);return true;});
    }
}
