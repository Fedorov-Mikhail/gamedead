package com.gamedead.server.room;
import com.gamedead.protocol.room.*;
import com.gamedead.protocol.world.*;
import com.gamedead.game.ArenaWorld;
import com.gamedead.game.Upgrade;
import com.gamedead.game.Difficulty;
import com.gamedead.server.match.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.*;

@Service
public final class RoomRegistry {
    private final Clock clock;
    private final Map<String,Room> rooms=new HashMap<>();
    private final Map<UUID,Room> membership=new HashMap<>();
    private final Map<UUID,Peer> peers=new HashMap<>();
    private final SecureRandom random=new SecureRandom();
    private long revision;
    private final int durationSeconds;
    private final MatchJournal journal;
    public RoomRegistry(Clock clock, @Value("${gamedead.match-duration-seconds:1800}") int durationSeconds,MatchJournal journal) {
        if(durationSeconds<1 || durationSeconds>1800)throw new IllegalArgumentException("Match duration must be 1–1800 seconds");
        this.clock=clock;this.durationSeconds=durationSeconds;this.journal=journal;
    }

    public synchronized RoomMailbox connect(UUID account,String name,UUID connection){
        if(peers.size()>=512 && !peers.containsKey(account)) throw problem(429,"SERVER_BUSY","Сервер занят. Попробуй позже.");
        Peer old=peers.put(account,new Peer(connection,name));
        if(old!=null)old.mailbox.close();
        Room room=membership.get(account);
        if(room!=null){
            Member member=room.members.get(account);member.disconnectedAt=null;
            if(room.world!=null)room.world.resetConnection(account);
            changed(room);
        } else peers.get(account).mailbox.offer(RoomEvent.room(state(account)));
        if(room!=null && room.world!=null)peers.get(account).mailbox.offer(RoomEvent.world(world(room)));
        return peers.get(account).mailbox;
    }
    public synchronized void disconnect(UUID account,UUID connection){
        Peer peer=peers.get(account);
        if(peer==null || !peer.connection.equals(connection))return;
        peers.remove(account);peer.mailbox.close();
        Room room=membership.get(account);
        if(room!=null){
            Member member=room.members.get(account);member.ready=false;member.disconnectedAt=clock.instant();
            if(room.world!=null)room.world.clearInput(account);
            changed(room);
        }
    }
    public synchronized RoomStateResponse create(UUID account){
        Peer peer=requirePeer(account);
        requireUnassigned(account);
        if(rooms.size()>=64)throw problem(429,"SERVER_BUSY","Все комнаты заняты. Попробуй позже.");
        String code;do{StringBuilder b=new StringBuilder();for(int i=0;i<6;i++)b.append("ABCDEFGHJKLMNPQRSTUVWXYZ23456789".charAt(random.nextInt(32)));code=b.toString();}while(rooms.containsKey(code));
        Room room=new Room(code,account);room.members.put(account,new Member(account,peer.name,0));
        rooms.put(code,room);membership.put(account,room);changed(room);return state(account);
    }
    public synchronized RoomStateResponse join(UUID account,String code){
        Peer peer=requirePeer(account);requireUnassigned(account);
        String normalized=code==null?"":code.strip().toUpperCase(Locale.ROOT);
        if(!normalized.matches("[A-HJ-NP-Z2-9]{6}"))throw problem(400,"INVALID_ROOM_CODE","Код комнаты: 6 букв или цифр.");
        Room room=rooms.get(normalized);
        if(room==null)throw problem(404,"ROOM_NOT_FOUND","Комната не найдена. Проверь код.");
        requireNotStarting(room);
        if(room.world!=null)throw problem(409,"ROOM_PLAYING","В комнате уже началась тренировка.");
        if(room.members.size()>=4)throw problem(409,"ROOM_FULL","В комнате уже четыре игрока.");
        int color=0;while(true){int candidate=color;if(room.members.values().stream().noneMatch(m->m.color==candidate))break;color++;}
        room.members.put(account,new Member(account,peer.name,color));membership.put(account,room);
        room.members.values().forEach(m->m.ready=false);
        changed(room);return state(account);
    }
    public synchronized RoomStateResponse state(UUID account){
        Room room=membership.get(account);
        return new RoomStateResponse(revision,room==null?null:snapshot(room));
    }
    public synchronized RoomStateResponse ready(UUID account,boolean ready){
        requirePeer(account);Room room=requireRoom(account);
        requireNotStarting(room);
        if(room.world!=null)throw problem(409,"ROOM_PLAYING","Тренировка уже началась.");
        room.members.get(account).ready=ready;changed(room);return state(account);
    }
    public synchronized RoomStateResponse difficulty(UUID account,DifficultyRequest request){
        Room room=requireOwner(account);requireNotStarting(room);
        if(room.world!=null)throw problem(409,"ROOM_PLAYING","Сложность можно менять только в комнате до старта.");
        Difficulty selected;
        try{selected=Difficulty.valueOf(request.difficulty());}
        catch(IllegalArgumentException | NullPointerException invalid){throw problem(400,"INVALID_DIFFICULTY","Выбери доступную сложность.");}
        if(selected!=room.difficulty){room.difficulty=selected;room.members.values().forEach(m->m.ready=false);changed(room);}
        return state(account);
    }
    public RoomStateResponse start(UUID account){
        Room room;MatchHandle handle;
        synchronized(this){
            room=requireOwner(account);requireNotStarting(room);
            if(room.world!=null)throw problem(409,"ROOM_PLAYING","Тренировка уже началась.");
            if(!allReady(room))throw problem(409,"ROOM_NOT_READY","Нужны хотя бы два игрока. Все должны подключиться и нажать «Готов».");
            var start=new MatchStart(UUID.randomUUID(),clock.instant(),durationSeconds,
                    room.members.values().stream().map(m->new MatchStart.Participant(m.id,m.name)).toList(),room.difficulty.name());
            try{handle=journal.reserve(start);}catch(IllegalStateException unavailable){throw saveUnavailable();}
            room.match=handle;room.starting=true;changed(room);
        }
        try{journal.begin(handle);}catch(RuntimeException unavailable){
            journal.cancel(handle);
            synchronized(this){if(rooms.get(room.code)==room && room.match==handle){room.starting=false;room.match=null;room.members.values().forEach(m->m.ready=false);changed(room);}}
            throw saveUnavailable();
        }
        synchronized(this){
            var roster=handle.start().players().stream().map(MatchStart.Participant::accountId).toList();
            if(rooms.get(room.code)!=room || room.match!=handle || !room.owner.equals(account)
                    || !new ArrayList<>(room.members.keySet()).equals(roster) || !allReady(room)){
                journal.complete(handle,new MatchFinish(handle.start().id(),"ABORTED",0,1,0,0,
                        roster.stream().map(id->new MatchFinish.PlayerResult(id,0,0,1,true,Map.of())).toList()));
                if(rooms.get(room.code)==room && room.match==handle){room.starting=false;room.match=null;room.members.values().forEach(m->m.ready=false);changed(room);}
                throw problem(409,"ROOM_NOT_READY","Состав или подключение изменились. Подготовьтесь и запустите матч снова.");
            }
            room.world=new ArenaWorld(roster,durationSeconds,Difficulty.valueOf(handle.start().difficulty()));room.worldId=handle.start().id();room.starting=false;
            changed(room);publishWorld(room);return state(account);
        }
    }
    public synchronized RoomStateResponse stop(UUID account){
        Room room=requireOwner(account);
        requireNotStarting(room);
        if(room.world!=null && room.world.phase()==ArenaWorld.Phase.RUNNING){
            room.world.abort();completeMatch(room);room.members.values().forEach(m->m.ready=false);changed(room);publishWorld(room);return state(account);
        }
        room.world=null;room.worldId=null;room.match=null;
        room.members.values().forEach(m->m.ready=false);changed(room);return state(account);
    }
    public synchronized RoomStateResponse leave(UUID account){
        Room room=membership.remove(account);
        if(room!=null){
            room.members.remove(account);
            if(room.world!=null){if(room.members.isEmpty())room.world.abort();room.world.remove(account);completeMatch(room);}
            if(room.members.isEmpty()){rooms.remove(room.code);revision++;}
            else {if(room.owner.equals(account))room.owner=room.members.keySet().iterator().next();changed(room);if(room.world!=null)publishWorld(room);}
        }
        Peer peer=peers.get(account);if(peer!=null)peer.mailbox.offer(RoomEvent.room(state(account)));
        return state(account);
    }
    public synchronized void input(UUID account,UUID connection,InputCommand command){
        Peer peer=peers.get(account);Room room=membership.get(account);
        if(peer==null || !peer.connection.equals(connection) || room==null || room.world==null)return;
        if(!command.valid())throw new IllegalArgumentException("Invalid combat command");
        room.world.input(account,command.sequence(),command.x(),command.y(),command.dash(),command.aimX(),command.aimY(),command.attack(),command.revive());
    }
    public synchronized RoomStateResponse upgrade(UUID account,UpgradeRequest request){
        requirePeer(account);Room room=requireRoom(account);
        if(request==null || room.world==null || !room.worldId.equals(request.worldId())
                || !room.world.chooseUpgrade(account,request.choiceId(),request.upgrade()))
            throw problem(409,"INVALID_UPGRADE","Предложение уже изменилось. Выбери доступное улучшение.");
        changed(room);publishWorld(room);return state(account);
    }
    public synchronized void tick(){
        Instant cutoff=clock.instant().minusSeconds(30);
        var expired=membership.keySet().stream().filter(id->{Member m=membership.get(id).members.get(id);return m.disconnectedAt!=null && !m.disconnectedAt.isAfter(cutoff);}).toList();
        expired.forEach(this::leave);
        for(Room room:rooms.values())if(room.world!=null){
            var previous=room.world.phase();long previousTick=room.world.tickNumber();room.world.tick();
            if(previous!=room.world.phase()){completeMatch(room);changed(room);publishWorld(room);}
            else if(!Objects.equals(room.lastSaveState,room.match.state())){changed(room);publishWorld(room);}
            else if(previousTick!=room.world.tickNumber() && room.world.tickNumber()%2==0)publishWorld(room);
        }
    }
    private boolean allReady(Room room){return room.members.size()>=2 && room.members.values().stream().allMatch(m->m.ready && peers.containsKey(m.id));}
    private void requireNotStarting(Room room){if(room.starting)throw problem(409,"ROOM_STARTING","Матч запускается. Подожди несколько секунд.");}
    private RoomProblem saveUnavailable(){return problem(503,"SAVE_UNAVAILABLE","Не удалось сохранить начало матча. Попробуй позже.");}
    private static Map<String,Integer> ranks(Map<Upgrade,Integer> upgrades){
        Map<String,Integer> result=new HashMap<>();upgrades.forEach((upgrade,rank)->result.put(upgrade.name(),rank));return result;
    }
    private void completeMatch(Room room){
        if(room.world.phase()==ArenaWorld.Phase.RUNNING)return;
        var world=room.world;
        journal.complete(room.match,new MatchFinish(room.worldId,world.phase().name(),world.tickNumber(),world.level(),world.totalExperience(),world.kills(),
                world.results().stream().map(p->new MatchFinish.PlayerResult(p.id(),p.kills(),p.damage(),p.level(),p.left(),ranks(p.upgrades()))).toList()));
    }
    private void requireUnassigned(UUID account){if(membership.containsKey(account))throw problem(409,"ALREADY_IN_ROOM","Ты уже состоишь в комнате.");}
    private Peer requirePeer(UUID account){Peer peer=peers.get(account);if(peer==null)throw problem(409,"NOT_CONNECTED","Сначала подключись к игровому серверу.");return peer;}
    private Room requireRoom(UUID account){Room room=membership.get(account);if(room==null)throw problem(404,"ROOM_NOT_FOUND","Ты ещё не состоишь в комнате.");return room;}
    private Room requireOwner(UUID account){requirePeer(account);Room room=requireRoom(account);if(!room.owner.equals(account))throw problem(403,"NOT_OWNER","Это действие доступно владельцу комнаты.");return room;}
    private void changed(Room room){room.version=++revision;for(UUID id:room.members.keySet()){Peer p=peers.get(id);if(p!=null)p.mailbox.offer(RoomEvent.room(state(id)));}}
    private RoomSnapshot snapshot(Room room){return new RoomSnapshot(room.code,room.version,room.owner,room.starting?"STARTING":room.world==null?"LOBBY":"PLAYING",room.members.values().stream().map(m->new RoomMember(m.id,m.name,m.ready,peers.containsKey(m.id),m.color)).toList(),room.difficulty.name());}
    private WorldSnapshot world(Room room){
        ArenaWorld world=room.world;
        return new WorldSnapshot(room.code,room.version,room.worldId,world.tickNumber(),
                world.players().stream().map(p->new PlayerSnapshot(p.id(),p.x(),p.y(),p.dashCooldown(),p.moving(),p.health(),p.reviveProgress(),
                        p.maxHealth(),p.pendingUpgrades(),p.choiceId(),p.upgradeOffers().stream().map(Enum::name).toList(),ranks(p.upgrades()))).toList(),
                world.enemies().stream().map(e->new EnemySnapshot(e.id(),e.x(),e.y(),e.health(),e.maxHealth(),e.kind().name(),e.action(),e.actionTicks(),e.aimX(),e.aimY(),e.radius())).toList(),
                world.projectiles().stream().map(p->new ProjectileSnapshot(p.id(),p.x(),p.y(),p.velocityX(),p.velocityY(),p.hostile())).toList(),
                world.phase().name(),world.remainingTicks(),world.kills(),world.level(),world.experience(),world.requiredExperience(),room.match.state(),world.wave(),world.bossSpawned());
    }
    private void publishWorld(Room room){RoomEvent event=RoomEvent.world(world(room));room.lastSaveState=event.world().saveState();for(UUID id:room.members.keySet()){Peer p=peers.get(id);if(p!=null)p.mailbox.offer(event);}}
    private RoomProblem problem(int status,String code,String message){return new RoomProblem(status,code,message);}
    private static final class Peer {final UUID connection;final String name;final RoomMailbox mailbox=new RoomMailbox();Peer(UUID id,String name){connection=id;this.name=name;}}
    private static final class Member {final UUID id;final String name;final int color;boolean ready;Instant disconnectedAt;Member(UUID id,String name,int color){this.id=id;this.name=name;this.color=color;}}
    private static final class Room {final String code;UUID owner,worldId;long version;boolean starting;String lastSaveState;MatchHandle match;Difficulty difficulty=Difficulty.NORMAL;final LinkedHashMap<UUID,Member> members=new LinkedHashMap<>();ArenaWorld world;Room(String code,UUID owner){this.code=code;this.owner=owner;}}
}
