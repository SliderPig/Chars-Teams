package com.example.charteams.api;

import com.example.charteams.client.ClientState;

import java.util.List;

// PC side. Other mods call this by reflection, so keep these two methods as they are.
public final class CharTeamsClient {
    private CharTeamsClient() {}

    // Teammates except you, with where they are. Refreshed about once a second. Empty without a team or on a server without the mod.
    public static List<Mate> mates() {
        return ClientState.mates();
    }

    // Your team's name, or null.
    public static String myTeam() {
        return ClientState.teamName();
    }
}
