package com.gamedead.server.match;

import com.gamedead.game.Upgrade;
import com.gamedead.protocol.match.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MatchStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public MatchStore(JdbcTemplate jdbc,JsonMapper json,Clock clock) {
        this.jdbc=jdbc;this.json=json;this.clock=clock;
    }

    @Transactional
    public void begin(MatchStart start) {
        validateStart(start);
        int inserted=jdbc.update("""
            INSERT INTO game.matches(id,started_at,desired_duration_seconds,difficulty) VALUES (?,?,?,?)
            ON CONFLICT(id) DO NOTHING
            """,start.id(),Timestamp.from(start.startedAt()),start.durationSeconds(),start.difficulty());
        if(inserted==1) {
            for(var player:start.players()) jdbc.update("""
                INSERT INTO game.match_players(match_id,account_id,display_name) VALUES (?,?,?)
                """,start.id(),player.accountId(),player.displayName());
        } else {
            requireStart(start);
            requireRoster(start);
        }
    }

    @Transactional
    public void finish(MatchStart start,MatchFinish finish) {
        validateFinish(start,finish);
        requireStart(start);
        requireRoster(start);
        Instant now=clock.instant();
        if(now.isBefore(start.startedAt())) now=start.startedAt();
        int changed=jdbc.update("""
            UPDATE game.matches SET status=?,finished_at=?,elapsed_ticks=?,team_level=?,team_xp=?,team_kills=?
            WHERE id=? AND status='RUNNING'
            """,finish.status(),Timestamp.from(now),finish.elapsedTicks(),finish.teamLevel(),finish.teamExperience(),finish.teamKills(),start.id());
        if(changed==0) return;
        for(var player:finish.players()) {
            long reward=!player.left() && Set.of("WON","LOST").contains(finish.status())
                ?(long)finish.teamKills()*5+(finish.status().equals("WON")?100:0):0;
            int participant=jdbc.update("""
                UPDATE game.match_players SET kills=?,damage=?,level=?,left_match=?,upgrades=?::jsonb,account_xp=?
                WHERE match_id=? AND account_id=?
                """,player.kills(),player.damage(),player.level(),player.left(),json.writeValueAsString(player.upgrades()),reward,start.id(),player.accountId());
            if(participant!=1) throw new IllegalStateException("Missing match participant");
            int profile=jdbc.update("""
                UPDATE game.player_profiles SET account_xp=account_xp+?,updated_at=? WHERE account_id=?
                """,reward,Timestamp.from(now),player.accountId());
            if(profile!=1) throw new IllegalStateException("Missing player profile");
        }
    }

    @Transactional
    public void interruptUnfinished(Instant now) {
        jdbc.update("""
            UPDATE game.matches SET status='INTERRUPTED',finished_at=GREATEST(started_at,?)
            WHERE status='RUNNING'
            """,Timestamp.from(now));
    }

    @Transactional(readOnly=true)
    public MatchHistoryResponse history(UUID account) {
        Objects.requireNonNull(account);
        var matches=jdbc.query("""
            SELECT m.id,m.status,m.started_at,m.finished_at,m.elapsed_ticks,m.team_kills,m.difficulty,
                   p.level,p.kills,p.damage,p.account_xp,p.upgrades::text AS upgrades
            FROM game.match_players p JOIN game.matches m ON m.id=p.match_id
            WHERE p.account_id=? AND m.status<>'RUNNING'
            ORDER BY m.finished_at DESC,m.id DESC LIMIT 20
            """,(row,index)->new MatchSummary(row.getObject("id",UUID.class),row.getString("status"),
                row.getTimestamp("started_at").toInstant(),row.getTimestamp("finished_at").toInstant(),
                (row.getInt("elapsed_ticks")+19)/20,row.getInt("level"),row.getInt("team_kills"),
                row.getInt("kills"),row.getInt("damage"),row.getLong("account_xp"),
                json.readValue(row.getString("upgrades"),json.getTypeFactory().constructMapType(Map.class,String.class,Integer.class)),row.getString("difficulty")),account);
        return new MatchHistoryResponse(matches);
    }

    private void requireStart(MatchStart start) {
        Integer count=jdbc.queryForObject("""
            SELECT count(*) FROM game.matches WHERE id=? AND started_at=? AND desired_duration_seconds=? AND difficulty=? AND config_version=3
            """,Integer.class,start.id(),Timestamp.from(start.startedAt()),start.durationSeconds(),start.difficulty());
        if(count==null || count!=1) throw new IllegalStateException("Match start does not match durable record");
    }

    private void requireRoster(MatchStart start) {
        var stored=jdbc.query("SELECT account_id,display_name FROM game.match_players WHERE match_id=?",
            (row,index)->new MatchStart.Participant(row.getObject("account_id",UUID.class),row.getString("display_name")),start.id());
        if(stored.size()!=start.players().size() || !new HashSet<>(stored).equals(new HashSet<>(start.players())))
            throw new IllegalStateException("Match roster does not match durable record");
    }

    static void validateStart(MatchStart start) {
        if(start==null || start.id()==null || start.startedAt()==null || start.durationSeconds()<1 || start.durationSeconds()>1800
                || start.difficulty()==null || !Set.of("EASY","NORMAL","HARD").contains(start.difficulty())
                || start.players().size()<2 || start.players().size()>4) throw new IllegalArgumentException("Invalid match start");
        var ids=new HashSet<UUID>();
        for(var player:start.players()) {
            if(player.accountId()==null || !ids.add(player.accountId()) || player.displayName()==null)
                throw new IllegalArgumentException("Invalid match participant");
            String name=player.displayName(),stripped=name.strip();
            if(name.codePointCount(0,name.length())>24 || stripped.codePointCount(0,stripped.length())<3)
                throw new IllegalArgumentException("Invalid match participant");
        }
    }

    private static void validateFinish(MatchStart start,MatchFinish finish) {
        validateStart(start);
        if(finish==null || !start.id().equals(finish.id()) || finish.status()==null || !Set.of("WON","LOST","ABORTED","INTERRUPTED").contains(finish.status())
                || finish.elapsedTicks()<0 || finish.elapsedTicks()>start.durationSeconds()*20L
                || finish.teamLevel()<1 || finish.teamLevel()>10 || finish.teamExperience()<0 || finish.teamKills()<0
                || finish.players().size()!=start.players().size()) throw new IllegalArgumentException("Invalid match result");
        var expected=new HashSet<UUID>();
        start.players().forEach(player->expected.add(player.accountId()));
        for(var player:finish.players()) {
            if(!expected.remove(player.accountId()) || player.kills()<0 || player.damage()<0 || player.level()<1 || player.level()>10)
                throw new IllegalArgumentException("Invalid player result");
            for(var upgrade:player.upgrades().entrySet()) {
                int max=Upgrade.valueOf(upgrade.getKey()).maxRank();
                if(upgrade.getValue()<0 || upgrade.getValue()>max) throw new IllegalArgumentException("Invalid upgrade rank");
            }
        }
        if(!expected.isEmpty()) throw new IllegalArgumentException("Incomplete match result");
    }
}
