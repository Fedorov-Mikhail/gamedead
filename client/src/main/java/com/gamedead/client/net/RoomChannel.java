package com.gamedead.client.net;
import com.gamedead.protocol.world.InputCommand;
public interface RoomChannel extends AutoCloseable {
    boolean input(InputCommand input);
    @Override void close();
}
