package com.example.charteams.client;

import com.example.charteams.Net;
import com.example.charteams.api.Mate;

import java.util.List;

// What the server last told this PC. Cleared when you leave the world.
public final class ClientState {
    private ClientState() {}

    private static volatile Net.State state = new Net.State(null, 0, null, 0, List.of(), List.of());
    private static volatile List<Mate> mates = List.of();

    public static void set(Net.State s) {
        state = s;
    }

    public static void set(List<Mate> list) {
        mates = List.copyOf(list);
    }

    public static void clear() {
        state = new Net.State(null, 0, null, 0, List.of(), List.of());
        mates = List.of();
    }

    public static Net.State state() {
        return state;
    }

    public static List<Mate> mates() {
        return mates;
    }

    public static String teamName() {
        return state.team();
    }
}
