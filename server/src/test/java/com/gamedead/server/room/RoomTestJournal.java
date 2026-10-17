package com.gamedead.server.room;
import com.gamedead.server.match.*;
import java.time.Clock;
import static org.mockito.Mockito.mock;
final class RoomTestJournal {
    static MatchJournal create(Clock clock){
        var journal=new MatchJournal(mock(MatchStore.class),clock);journal.recover();return journal;
    }
}
