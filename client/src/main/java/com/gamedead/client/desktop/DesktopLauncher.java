package com.gamedead.client.desktop;

import com.badlogic.gdx.backends.lwjgl3.*;
import com.gamedead.client.net.HttpGameApi;
import com.gamedead.client.ui.GameDeadClient;

public final class DesktopLauncher {
    private DesktopLauncher() { }

    public static void main(String[] args) {
        String address = "http://127.0.0.1:8080";
        for (String arg : args) {
            if (!arg.startsWith("--server-url=")) {
                System.err.println("Запуск: client.bat [--server-url=http://127.0.0.1:8080]");
                return;
            }
            address = arg.substring("--server-url=".length());
        }
        HttpGameApi api;
        try { api = new HttpGameApi(address); }
        catch (IllegalArgumentException e) { System.err.println(e.getMessage()); return; }
        var configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Печать сумрака • GameDead");
        configuration.setWindowedMode(1280, 720);
        configuration.setWindowSizeLimits(960, 640, -1, -1);
        configuration.useVsync(true);
        configuration.setForegroundFPS(60);
        try { new Lwjgl3Application(new GameDeadClient(api, address), configuration); }
        finally { api.close(); }
    }
}
