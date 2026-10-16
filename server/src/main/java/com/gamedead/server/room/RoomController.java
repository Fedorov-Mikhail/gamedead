package com.gamedead.server.room;

import com.gamedead.protocol.room.*;
import com.gamedead.server.auth.AccountPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/rooms")
public class RoomController {
    private final RoomRegistry rooms;
    public RoomController(RoomRegistry rooms){this.rooms=rooms;}
    @GetMapping("/me") public RoomStateResponse state(@AuthenticationPrincipal AccountPrincipal p){return rooms.state(p.accountId());}
    @PostMapping("/create") @ResponseStatus(HttpStatus.CREATED)
    public RoomStateResponse create(@AuthenticationPrincipal AccountPrincipal p){return rooms.create(p.accountId());}
    @PostMapping("/join") public RoomStateResponse join(@AuthenticationPrincipal AccountPrincipal p,@RequestBody JoinRoomRequest request){return rooms.join(p.accountId(),request.code());}
    @PostMapping("/ready") public RoomStateResponse ready(@AuthenticationPrincipal AccountPrincipal p,@RequestBody ReadyRequest request){return rooms.ready(p.accountId(),request.ready());}
    @PostMapping("/start") public RoomStateResponse start(@AuthenticationPrincipal AccountPrincipal p){return rooms.start(p.accountId());}
    @PostMapping("/stop") public RoomStateResponse stop(@AuthenticationPrincipal AccountPrincipal p){return rooms.stop(p.accountId());}
    @PostMapping("/leave") public RoomStateResponse leave(@AuthenticationPrincipal AccountPrincipal p){return rooms.leave(p.accountId());}
}
