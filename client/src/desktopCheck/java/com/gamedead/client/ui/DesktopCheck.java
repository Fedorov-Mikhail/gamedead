package com.gamedead.client.ui;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.utils.Clipboard;
import com.gamedead.client.net.GameApi;
import com.gamedead.protocol.account.PlayerProfileResponse;
import com.gamedead.protocol.auth.*;
import com.gamedead.protocol.room.*;
import com.gamedead.protocol.world.*;
import com.gamedead.client.net.*;
import com.gamedead.game.ArenaWorld;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Explicit graphics check, deliberately excluded from normal build/test tasks. */
public final class DesktopCheck {
    private static Throwable failure;

    public static void main(String[] args) {
        for (int[] size : new int[][]{{1280, 720}, {960, 640}}) {
            var config = new Lwjgl3ApplicationConfiguration();
            config.setTitle("GameDead desktop check");
            config.setWindowedMode(size[0], size[1]);
            config.setInitialVisible(false);
            config.disableAudio(true);
            config.setForegroundFPS(60);
            FakeApi api = new FakeApi();
            GameDeadClient game = new GameDeadClient(api, "http://127.0.0.1:8081");
            new Lwjgl3Application(new ApplicationAdapter() {
                int frame;
                @Override public void create() { game.create(); }
                @Override public void resize(int w, int h) { game.resize(w, h); }
                @Override public void render() {
                    try {
                        Input actualInput=Gdx.input;
                        int current=frame;
                        if(current>=68 && current<=80)Gdx.input=(Input)java.lang.reflect.Proxy.newProxyInstance(Input.class.getClassLoader(),new Class<?>[]{Input.class},(proxy,method,arguments)->{
                            if(method.getName().equals("isKeyPressed") && (int)arguments[0]==Input.Keys.D)return true;
                            if(method.getName().equals("isKeyJustPressed") && (int)arguments[0]==Input.Keys.SHIFT_LEFT)return current==68;
                            if(method.getName().equals("isButtonPressed") && (int)arguments[0]==Input.Buttons.LEFT)return true;
                            if(method.getName().equals("getX"))return size[0]*3/4;
                            if(method.getName().equals("getY"))return size[1]/2;
                            return method.invoke(actualInput,arguments);
                        });
                        try{game.render();}finally{Gdx.input=actualInput;}
                        frame++;
                        Stage stage = (Stage) Gdx.input.getInputProcessor();
                        switch (frame) {
                            case 5 -> snapshot(size[0] + "-login");
                            case 8 -> click(stage, "navigate");
                            case 12 -> snapshot(size[0] + "-register");
                            case 15 -> {
                                field(stage, "login").setText("mage");
                                field(stage, "displayName").setText("Ёж [gold]маг");
                                paste(stage, "password", "  пароль\r\n🔥  ");
                                paste(stage, "confirmation", "  пароль\r\n🔥  ");
                                click(stage, "reveal");
                                if (!field(stage, "password").getText().equals("  пароль\r\n🔥  ")
                                        || field(stage, "password").isPasswordMode()) throw new AssertionError("Password reveal modified input");
                                click(stage, "reveal");
                                click(stage, "submit");
                            }
                            case 22 -> {
                                if (stage.getRoot().findActor("displayName") != null) throw new AssertionError("Registration did not return to login");
                                if (!field(stage, "login").getText().equals("mage")) throw new AssertionError("Login prefill missing");
                                if (!field(stage, "password").getText().isEmpty()) throw new AssertionError("Password not cleared");
                                paste(stage, "password", "  пароль\r\n🔥  ");
                                // Enter submits the form without appending a newline to the password.
                                stage.keyTyped('\r');
                            }
                            case 30 -> {
                                Label name = stage.getRoot().findActor("profile-name");
                                if (name == null || !name.getText().toString().equals("Ёж [gold]маг")) throw new AssertionError("Profile not rendered");
                                if (name.getStyle().font.getData().markupEnabled) throw new AssertionError("Player name interpreted as markup");
                                if (!api.password.equals("  пароль\r\n🔥  ")) throw new AssertionError("Password changed before API call");
                                snapshot(size[0] + "-profile");
                                click(stage, "refresh");
                            }
                            case 37 -> {
                                Label xp = stage.getRoot().findActor("profile-xp");
                                if (!xp.getText().toString().contains("84")) throw new AssertionError("Refresh did not update profile");
                                click(stage, "rooms");
                            }
                            case 44 -> {snapshot(size[0]+"-room-menu");click(stage,"room-create");}
                            case 50 -> {snapshot(size[0]+"-lobby");click(stage,"room-ready");}
                            case 60 -> click(stage,"room-start");
                            case 88 -> {
                                if(stage.getRoot().findActor("arena-stop")==null)throw new AssertionError("Arena HUD missing");
                                if(api.world.players().getFirst().x()<=80)throw new AssertionError("WASD did not move the player");
                                if(api.world.players().getFirst().dashCooldown()<=0)throw new AssertionError("Shift did not trigger dash");
                                if(!api.attacked)throw new AssertionError("Mouse aim and attack did not reach the world");
                                if(stage.getRoot().findActor("combat-status")==null)throw new AssertionError("Combat HUD missing");
                                snapshot(size[0]+"-arena");click(stage,"arena-stop");
                            }
                            case 92 -> {
                                Label result=stage.getRoot().findActor("combat-result");
                                if(result==null || !result.getText().toString().contains("прерван"))throw new AssertionError("Aborted result missing");
                                snapshot(size[0]+"-result");click(stage,"arena-stop");
                            }
                            case 96 -> click(stage,"room-ready");
                            case 100 -> click(stage,"room-start");
                            case 180 -> {
                                Label result=stage.getRoot().findActor("combat-result");
                                if(result==null || !result.getText().toString().contains("выстоял"))throw new AssertionError("Victory result missing");
                                snapshot(size[0]+"-victory");click(stage,"arena-stop");
                            }
                            case 186 -> click(stage,"room-ready");
                            case 190 -> click(stage,"room-start");
                            case 198 -> {
                                Label result=stage.getRoot().findActor("combat-result");
                                if(result==null || !result.getText().toString().contains("пал"))throw new AssertionError("Defeat result missing");
                                snapshot(size[0]+"-defeat");click(stage,"arena-stop");
                            }
                            case 204 -> click(stage,"room-leave");
                            case 210 -> click(stage,"room-back");
                            case 216 -> click(stage,"logout");
                            case 224 -> {
                                if (stage.getRoot().findActor("profile-name") != null) throw new AssertionError("Logout retained profile");
                                snapshot(size[0] + "-logout");
                                Gdx.app.exit();
                            }
                            default -> { }
                        }
                    } catch (Throwable error) { failure = error; Gdx.app.exit(); }
                }
                @Override public void dispose() { game.dispose(); }
            }, config);
            if (failure != null) throw new AssertionError("Desktop check failed", failure);
        }
        System.out.println("Accounts, rooms, combat, mouse attack, WASD, Shift and all results checked at 1280x720 and 960x640. Screenshots: client/build/reports/desktop/");
    }

    private static TextField field(Stage stage, String name) {
        TextField field = stage.getRoot().findActor(name);
        if (field == null) throw new AssertionError("Missing field: " + name);
        return field;
    }
    private static void click(Stage stage, String name) {
        TextButton button = stage.getRoot().findActor(name);
        if (button == null || button.isDisabled()) throw new AssertionError("Unavailable button: " + name);
        button.fire(new ChangeEvent());
    }
    private static void snapshot(String name) {
        Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try { PixmapIO.writePNG(Gdx.files.local("build/reports/desktop/" + name + ".png"), pixels, -1, true); }
        finally { pixels.dispose(); }
    }

    private static void paste(Stage stage, String name, String value) {
        TextField target = field(stage, name);
        // An in-memory clipboard and modifier state exercise the actual Ctrl+V handler
        // without reading or overwriting the user's system clipboard or keyboard.
        target.setClipboard(new Clipboard() {
            public boolean hasContents() { return true; }
            public String getContents() { return value; }
            public void setContents(String ignored) { }
        });
        Input previousInput = Gdx.input;
        Gdx.input = (Input) java.lang.reflect.Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                (proxy, method, args) -> method.getName().equals("isKeyPressed") && (int) args[0] == Input.Keys.CONTROL_LEFT
                        ? true : method.invoke(previousInput, args));
        stage.setKeyboardFocus(target);
        try { stage.keyDown(Input.Keys.V); }
        finally { Gdx.input = previousInput; }
        if (!target.getText().equals(value)) throw new AssertionError("Clipboard password was modified");
    }

    private static final class FakeApi implements GameApi {
        final UUID id = UUID.fromString("c28e03ef-f924-4dbd-afc7-9f93e7b70db6");
        String password;
        int reads,starts;
        Consumer<RoomEvent> events;
        long revision;
        boolean ready,playing,attacked;
        RoomSnapshot room;
        UUID worldId;
        ArenaWorld world;
        final List<UUID> roster=List.of(id,UUID.fromString("00000000-0000-0000-0000-000000000002"),UUID.fromString("00000000-0000-0000-0000-000000000003"),UUID.fromString("00000000-0000-0000-0000-000000000004"));
        final RoomChannel channel=new RoomChannel(){
            public boolean input(InputCommand input){
                if(world==null)return false;
                world.input(id,input.sequence(),input.x(),input.y(),input.dash(),input.aimX(),input.aimY(),input.attack(),input.revive());
                if(input.attack() && Math.hypot(input.aimX(),input.aimY())>0)attacked=true;
                world.tick();publishWorld();return true;
            }
            public void close(){ }
        };
        public CompletableFuture<RegisterResponse> register(RegisterRequest request) {
            return CompletableFuture.completedFuture(new RegisterResponse(id, request.login(), request.displayName()));
        }
        public CompletableFuture<LoginResponse> login(LoginRequest request) {
            password = request.password();
            return CompletableFuture.completedFuture(new LoginResponse("test-session", "Bearer", Instant.parse("2099-01-01T00:00:00Z")));
        }
        public CompletableFuture<PlayerProfileResponse> profile(String token) {
            return CompletableFuture.completedFuture(new PlayerProfileResponse(id, "mage", "Ёж [gold]маг", ++reads * 42L, "default"));
        }
        public CompletableFuture<Void> logout(String token) { return CompletableFuture.completedFuture(null); }
        public CompletableFuture<RoomChannel> connectRooms(String token,Consumer<RoomEvent> events,Consumer<ApiFailure> failure){this.events=events;events.accept(RoomEvent.room(new RoomStateResponse(revision,room)));return CompletableFuture.completedFuture(channel);}
        public CompletableFuture<RoomStateResponse> room(String token,String action,Object body){
            revision++;
            if(action.equals("ready"))ready=((ReadyRequest)body).ready();
            if(action.equals("start")){
                playing=true;starts++;world=new ArenaWorld(roster,starts==2?1:240);worldId=UUID.randomUUID();
                if(starts==1)for(int i=0;i<240;i++)world.tick();
                if(starts==3)while(world.phase()==ArenaWorld.Phase.RUNNING)world.tick();
            }
            if(action.equals("stop")){
                ready=false;
                if(world!=null && world.phase()==ArenaWorld.Phase.RUNNING)world.abort();
                else {playing=false;world=null;}
            }
            room=action.equals("leave")?null:new RoomSnapshot("ABC234",revision,id,playing?"PLAYING":"LOBBY",List.of(
                    new RoomMember(id,"Ёж [gold]маг",ready,true,0),new RoomMember(roster.get(1),"Маг сумрака",ready,true,1),
                    new RoomMember(roster.get(2),"Лесной страж",ready,true,2),new RoomMember(roster.get(3),"Хранитель огня",ready,true,3)));
            events.accept(RoomEvent.room(new RoomStateResponse(revision,room)));
            if(playing)publishWorld();
            return CompletableFuture.completedFuture(new RoomStateResponse(revision,room));
        }
        private void publishWorld(){events.accept(RoomEvent.world(new WorldSnapshot("ABC234",revision,worldId,world.tickNumber(),
                world.players().stream().map(p->new PlayerSnapshot(p.id(),p.x(),p.y(),p.dashCooldown(),p.moving(),p.health(),p.reviveProgress())).toList(),
                world.enemies().stream().map(e->new EnemySnapshot(e.id(),e.x(),e.y(),e.health())).toList(),
                world.projectiles().stream().map(p->new ProjectileSnapshot(p.id(),p.x(),p.y())).toList(),world.phase().name(),world.remainingTicks(),world.kills())));}
        public void close() { }
    }
}
