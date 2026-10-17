package com.gamedead.server.match;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MatchJournal {
    private static final Logger LOG=LoggerFactory.getLogger(MatchJournal.class);
    private final MatchStore store;
    private final Clock clock;
    private final Map<UUID,MatchHandle> entries=new LinkedHashMap<>();
    private final ReentrantLock flushLock=new ReentrantLock();
    private boolean ready;

    public MatchJournal(MatchStore store,Clock clock) {this.store=store;this.clock=clock;}

    public synchronized MatchHandle reserve(MatchStart start) {
        if(!ready || entries.size()>=128 || entries.containsKey(start.id()))
            throw new IllegalStateException("Match persistence is not available");
        var handle=new MatchHandle(start);
        entries.put(start.id(),handle);
        return handle;
    }

    public void begin(MatchHandle handle) {
        synchronized(this) {requireReserved(handle);}
        store.begin(handle.start);
        synchronized(this) {handle.begun=true;}
    }

    public synchronized void cancel(MatchHandle handle) {
        if(handle.finish==null) entries.remove(handle.start.id(),handle);
    }

    public synchronized void complete(MatchHandle handle,MatchFinish finish) {
        if(handle.finish!=null) return;
        requireReserved(handle);
        if(!handle.start.id().equals(finish.id())) throw new IllegalArgumentException("Match ID mismatch");
        // shortcut: pending outcomes live in memory; add a durable outbox if they must survive a process crash.
        handle.finish=finish;
        handle.state="PENDING";
    }

    @Scheduled(fixedDelay=1000)
    public void flush() {
        if(!flushLock.tryLock()) return;
        try {
            List<MatchHandle> pending;
            synchronized(this) {
                pending=entries.values().stream().filter(h->h.begun && h.finish!=null).toList();
            }
            for(var handle:pending) {
                try {store.finish(handle.start,handle.finish);}
                catch(RuntimeException unavailable) {
                    handle.state="RETRYING";
                    LOG.warn("Match {} save failed; retrying later",handle.start.id());
                    break;
                }
                synchronized(this) {
                    handle.state="SAVED";
                    entries.remove(handle.start.id(),handle);
                }
            }
        } finally {flushLock.unlock();}
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        synchronized(this) {ready=false;}
        store.interruptUnfinished(clock.instant());
        synchronized(this) {ready=true;}
    }

    private void requireReserved(MatchHandle handle) {
        if(entries.get(handle.start.id())!=handle) throw new IllegalStateException("Match is not reserved");
    }
}
