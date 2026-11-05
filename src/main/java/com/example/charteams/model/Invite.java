package com.example.charteams.model;

import java.util.UUID;

// An open question: will this player join that team? Only ever kept in memory, so a restart forgets them.
public record Invite(UUID team, UUID target, UUID inviter, String inviterName, long expiresAt) {
    public boolean expired(long now) {
        return now >= expiresAt;
    }
}
