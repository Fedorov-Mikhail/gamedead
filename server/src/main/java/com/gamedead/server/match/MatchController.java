package com.gamedead.server.match;
import com.gamedead.protocol.match.MatchHistoryResponse;
import com.gamedead.server.auth.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class MatchController {
    private final MatchStore store;
    public MatchController(MatchStore store) {this.store=store;}
    @GetMapping("/api/v1/matches/me")
    public MatchHistoryResponse me(@AuthenticationPrincipal AccountPrincipal account) {return store.history(account.accountId());}
}
