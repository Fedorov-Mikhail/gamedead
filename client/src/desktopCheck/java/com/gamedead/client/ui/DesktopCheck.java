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
import com.gamedead.game.ProgressionFixture;
import com.gamedead.game.Upgrade;
import com.gamedead.game.Difficulty;
import com.gamedead.protocol.match.*;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Explicit graphics check, deliberately excluded from normal build/test tasks. */
public final class DesktopCheck {
    private static Throwable failure;

    public static void main(String[] args) {
        ProjectileMotionCheck.main(args);
        for (int[] size : new int[][]{{1280, 720}, {960, 640}}) {
            var config = new Lwjgl3ApplicationConfiguration();
            config.setTitle("GameDead desktop check");
            config.setWindowedMode(size[0], size[1]);
            config.setInitialVisible(false);
            config.disableAudio(true);
            config.setForegroundFPS(60);
            FakeApi api = new FakeApi();
            GameDeadClient game = new GameDeadClient(api);
            new Lwjgl3Application(new ApplicationAdapter() {
                int frame;
                @Override public void create() { game.create(); }
                @Override public void resize(int w, int h) { game.resize(w, h); }
                @Override public void render() {
                    try {
                        Input actualInput=Gdx.input;
                        int current=frame;
                        if(current>=68 && current<=86)Gdx.input=(Input)java.lang.reflect.Proxy.newProxyInstance(Input.class.getClassLoader(),new Class<?>[]{Input.class},(proxy,method,arguments)->{
                            if(method.getName().equals("isKeyPressed") && (int)arguments[0]==Input.Keys.D)return true;
                            if(method.getName().equals("isKeyJustPressed"))return (int)arguments[0]==Input.Keys.SHIFT_LEFT && current==68
                                    || (int)arguments[0]==Input.Keys.NUM_2 && current==85;
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
                            case 48 -> click(stage,"difficulty-EASY");
                            case 50 -> {
                                if(!api.difficulty.equals("EASY"))throw new AssertionError("Difficulty button did not reach the room");
                                snapshot(size[0]+"-lobby");click(stage,"room-ready");
                            }
                            case 60 -> click(stage,"room-start");
                            case 70 -> {api.visualMode=1;api.revision++;api.publishWorld();}
                            case 74 -> snapshot(size[0]+"-enemy-windups");
                            case 75 -> {api.visualMode=2;api.revision++;api.publishWorld();}
                            case 79 -> snapshot(size[0]+"-enemy-attacks");
                            case 80 -> {api.visualMode=0;ProgressionFixture.gainExperience(api.world,500);api.revision++;api.publishWorld();}
                            case 83 -> {
                                if(stage.getRoot().findActor("upgrade-panel")==null)throw new AssertionError("Upgrade panel missing");
                                Label xp=stage.getRoot().findActor("combat-status");
                                if(!xp.getText().toString().contains("Уровень отряда: 3"))throw new AssertionError("Shared XP missing");
                                snapshot(size[0]+"-upgrades");click(stage,"upgrade-0");
                            }
                            case 88 -> {
                                if(stage.getRoot().findActor("arena-stop")==null)throw new AssertionError("Arena HUD missing");
                                if(api.world.players().getFirst().x()<=80)throw new AssertionError("WASD did not move the player");
                                if(api.world.players().getFirst().dashCooldown()<=0)throw new AssertionError("Shift did not trigger dash");
                                if(!api.attacked)throw new AssertionError("Mouse aim and attack did not reach the world");
                                if(api.world.difficulty()!=Difficulty.EASY)throw new AssertionError("Room difficulty did not reach the world");
                                if(stage.getRoot().findActor("combat-status")==null)throw new AssertionError("Combat HUD missing");
                                if(api.world.players().getFirst().upgrades().values().stream().mapToInt(Integer::intValue).sum()!=2)throw new AssertionError("Button and number key did not choose both upgrades");
                                snapshot(size[0]+"-arena");click(stage,"arena-stop");
                            }
                            case 90 -> {
                                Label save=stage.getRoot().findActor("result-save");
                                if(save==null || !save.getText().toString().contains("Сохраняем"))throw new AssertionError("Pending save state missing");
                                snapshot(size[0]+"-save-pending");api.saveState="RETRYING";api.revision++;api.publishWorld();
                            }
                            case 92 -> {
                                Label result=stage.getRoot().findActor("combat-result");
                                if(result==null || !result.getText().toString().contains("прерван"))throw new AssertionError("Aborted result missing");
                                Label save=stage.getRoot().findActor("result-save");
                                if(save==null || !save.getText().toString().contains("повторяет"))throw new AssertionError("Retry save state missing");
                                snapshot(size[0]+"-result-retrying");api.saveState="SAVED";api.revision++;api.publishWorld();
                            }
                            case 94 -> {
                                Label save=stage.getRoot().findActor("result-save");
                                if(save==null || !save.getText().toString().contains("сохранён"))throw new AssertionError("Saved result missing");
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
                            case 216 -> click(stage,"history");
                            case 222 -> {
                                Label match=stage.getRoot().findActor("history-match-0");
                                if(match==null || !match.getText().toString().contains("Урон:"))throw new AssertionError("Match history missing");
                                snapshot(size[0]+"-history");click(stage,"history-back");
                            }
                            case 228 -> {
                                Label xp=stage.getRoot().findActor("profile-xp");
                                if(xp==null || !xp.getText().toString().contains("126"))throw new AssertionError("Returning from history did not reload profile XP");
                                click(stage,"logout");
                            }
                            case 236 -> {
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
        System.out.println("Accounts, rooms, difficulty, enemy windups/attacks, boss charge, projectile interpolation, movement, upgrades, save states and history checked at 1280x720 and 960x640. Screenshots: client/build/reports/desktop/");
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
        int reads,starts,visualMode;
        String difficulty="NORMAL";
        Consumer<RoomEvent> events;
        long revision;
        boolean ready,playing,attacked;
        RoomSnapshot room;
        UUID worldId;
        ArenaWorld world;
        String saveState="RUNNING";
        final List<MatchSummary> matches=new ArrayList<>();
        UUID recorded;
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
        public CompletableFuture<MatchHistoryResponse> history(String token){return CompletableFuture.completedFuture(new MatchHistoryResponse(matches.reversed()));}
        public CompletableFuture<RoomChannel> connectRooms(String token,Consumer<RoomEvent> events,Consumer<ApiFailure> failure){this.events=events;events.accept(RoomEvent.room(new RoomStateResponse(revision,room)));return CompletableFuture.completedFuture(channel);}
        public CompletableFuture<RoomStateResponse> room(String token,String action,Object body){
            revision++;
            if(action.equals("ready"))ready=((ReadyRequest)body).ready();
            if(action.equals("difficulty"))difficulty=((DifficultyRequest)body).difficulty();
            if(action.equals("start")){
                playing=true;starts++;world=new ArenaWorld(roster,starts==2?1:240,Difficulty.valueOf(difficulty));worldId=UUID.randomUUID();saveState="RUNNING";
                if(starts==1)for(int i=0;i<240;i++)world.tick();
                if(starts==2){for(int i=0;i<18;i++)world.tick();ProgressionFixture.defeatBoss(world,id);}
                if(starts==3)while(world.phase()==ArenaWorld.Phase.RUNNING)world.tick();
            }
            if(action.equals("stop")){
                ready=false;
                if(world!=null && world.phase()==ArenaWorld.Phase.RUNNING){world.abort();saveState="PENDING";}
                else {playing=false;world=null;}
            }
            if(action.equals("upgrade")){
                UpgradeRequest upgrade=(UpgradeRequest)body;
                if(!worldId.equals(upgrade.worldId()) || !world.chooseUpgrade(id,upgrade.choiceId(),upgrade.upgrade()))throw new AssertionError("World rejected client upgrade");
            }
            room=action.equals("leave")?null:new RoomSnapshot("ABC234",revision,id,playing?"PLAYING":"LOBBY",List.of(
                    new RoomMember(id,"Ёж [gold]маг",ready,true,0),new RoomMember(roster.get(1),"Маг сумрака",ready,true,1),
                    new RoomMember(roster.get(2),"Лесной страж",ready,true,2),new RoomMember(roster.get(3),"Хранитель огня",ready,true,3)),difficulty);
            events.accept(RoomEvent.room(new RoomStateResponse(revision,room)));
            if(playing)publishWorld();
            return CompletableFuture.completedFuture(new RoomStateResponse(revision,room));
        }
        private void publishWorld(){
            if(world.phase()!=ArenaWorld.Phase.RUNNING && saveState.equals("RUNNING"))saveState="SAVED";
            if(world.phase()!=ArenaWorld.Phase.RUNNING && !worldId.equals(recorded)){
                recorded=worldId;var self=world.results().stream().filter(p->p.id().equals(id)).findFirst().orElseThrow();
                Instant start=Instant.parse("2026-10-17T10:00:00Z");
                matches.add(new MatchSummary(worldId,world.phase().name(),start,start.plusMillis(world.tickNumber()*50),
                        (int)(world.tickNumber()/20),world.level(),world.kills(),self.kills(),self.damage(),world.phase()==ArenaWorld.Phase.WON?100:0,ranks(self.upgrades()),difficulty));
            }
            events.accept(RoomEvent.world(new WorldSnapshot("ABC234",revision,worldId,world.tickNumber(),
                world.players().stream().map(p->new PlayerSnapshot(p.id(),p.x(),p.y(),p.dashCooldown(),p.moving(),p.health(),p.reviveProgress(),p.maxHealth(),p.pendingUpgrades(),p.choiceId(),p.upgradeOffers().stream().map(Enum::name).toList(),ranks(p.upgrades()))).toList(),
                visualMode==0?world.enemies().stream().map(e->new EnemySnapshot(e.id(),e.x(),e.y(),e.health(),e.maxHealth(),e.kind().name(),e.action(),e.actionTicks(),e.aimX(),e.aimY(),e.radius())).toList():visualEnemies(),
                visualMode==0?world.projectiles().stream().map(p->new ProjectileSnapshot(p.id(),p.x(),p.y(),p.velocityX(),p.velocityY(),p.hostile())).toList():List.of(
                        new ProjectileSnapshot(910,420-visualMode*6,120,-120,0,true),new ProjectileSnapshot(911,365+visualMode*18,280,360,0,false)),
                world.phase().name(),world.remainingTicks(),world.kills(),world.level(),world.experience(),world.requiredExperience(),saveState,visualMode==0?world.wave():29,visualMode!=0 || world.bossSpawned())));}
        private List<EnemySnapshot> visualEnemies(){
            boolean windup=visualMode==1;
            return List.of(new EnemySnapshot(901,180,240,45,60,"ZOMBIE",windup?"WINDUP":"ATTACK",windup?12:4,1,0,5),
                    new EnemySnapshot(902,355+(windup?0:18),240,30,45,"RUNNER",windup?"WINDUP":"CHARGE",windup?16:8,1,0,5),
                    new EnemySnapshot(903,440,120,60,90,"SHAMAN",windup?"WINDUP":"ATTACK",windup?28:8,-1,0,5),
                    new EnemySnapshot(904,220+(windup?0:24),100,18000,30000,"BOSS",windup?"WINDUP":"CHARGE",windup?40:28,1,0,10));
        }
        private Map<String,Integer> ranks(Map<Upgrade,Integer> ranks){return ranks.entrySet().stream().collect(java.util.stream.Collectors.toMap(e->e.getKey().name(),Map.Entry::getValue));}
        public void close() { }
    }
}
