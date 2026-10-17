package com.gamedead.server.match;
public final class MatchHandle {
    final MatchStart start;
    volatile String state="RUNNING";
    MatchFinish finish;
    boolean begun;
    public MatchHandle(MatchStart start) {this.start=start;}
    public MatchStart start() {return start;}
    public String state() {return state;}
}
