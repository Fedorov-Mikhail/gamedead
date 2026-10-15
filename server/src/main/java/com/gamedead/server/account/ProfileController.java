package com.gamedead.server.account;
import com.gamedead.protocol.account.PlayerProfileResponse;
import com.gamedead.server.auth.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class ProfileController {
    private final AccountService accounts;
    public ProfileController(AccountService accounts){this.accounts=accounts;}
    @GetMapping("/api/v1/me")
    public PlayerProfileResponse me(@AuthenticationPrincipal AccountPrincipal principal){return accounts.getProfile(principal.accountId());}
}
