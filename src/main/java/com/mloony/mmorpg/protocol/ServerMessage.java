package com.mloony.mmorpg.protocol;

public record ServerMessage(String type, String message, Object data) {
    public static ServerMessage ok(String type, String message, Object data) {
        return new ServerMessage(type, message, data);
    }

    public static ServerMessage error(String message) {
        return new ServerMessage("ERROR", message, null);
    }
}
