package com.gamedead.server.room;
public final class RoomProblem extends RuntimeException {
    private final int status;
    private final String code;
    public RoomProblem(int status,String code,String message){super(message);this.status=status;this.code=code;}
    public int status(){return status;}
    public String code(){return code;}
}
