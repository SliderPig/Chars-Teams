package com.example.charteams.api;

import com.example.charteams.TeamService;

import java.util.Set;
import java.util.UUID;

// Server side. Other mods call this by reflection, so keep these three methods as they are.
public final class CharTeams {
    private CharTeams() {}

    // The player's team name, or null when they have none.
    public static String teamName(UUID player) {
        return TeamService.teamName(player);
    }

    // Everyone on the player's team including the player. Empty when they have no team.
    public static Set<UUID> teammates(UUID player) {
        return TeamService.teammates(player);
    }

    public static boolean sameTeam(UUID a, UUID b) {
        return TeamService.sameTeam(a, b);
    }
}
