package com.gamedead.server.auth;
import com.gamedead.protocol.auth.*;
import com.gamedead.server.account.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final AccountService accounts; private final SessionService sessions;
    public AuthController(AccountService accounts,SessionService sessions){this.accounts=accounts;this.sessions=sessions;}
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@RequestBody RegisterRequest request){return accounts.register(request);}
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request){return sessions.login(request);}
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AccountPrincipal principal){sessions.logout(principal.sessionId());}
}
