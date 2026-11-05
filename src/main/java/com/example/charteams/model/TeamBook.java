package com.example.charteams.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// All the team rules, with no Minecraft in here so they can be tested on their own. One team per player.
// Other mods may ask from another thread (a singleplayer game runs the server beside the PC), so everything locks.
public final class TeamBook {
    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, Team> byPlayer = new HashMap<>();
    private final Map<UUID, List<Invite>> invites = new HashMap<>();
    private final Map<String, Long> saidNo = new HashMap<>();
    private int maxMembers = 16;
    private long inviteMs = 5 * 60_000L;
    private Runnable onChange = () -> {};

    public synchronized void limits(int maxMembers, long inviteMs) {
        this.maxMembers = Math.max(2, maxMembers);
        this.inviteMs = Math.max(1000L, inviteMs);
    }

    public synchronized int maxMembers() {
        return maxMembers;
    }

    // Called when something worth saving changed. Invites aren't saved, so they don't count.
    public synchronized void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public synchronized Team teamOf(UUID player) {
        return player == null ? null : byPlayer.get(player);
    }

    public synchronized Team teamById(UUID id) {
        return id == null ? null : teams.get(id);
    }

    public synchronized Team teamNamed(String name) {
        String wanted = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        for (Team team : teams.values()) {
            if (team.name().toLowerCase(Locale.ROOT).equals(wanted)) return team;
        }
        return null;
    }

    public synchronized List<Team> teams() {
        return new ArrayList<>(teams.values());
    }

    public synchronized Result create(UUID owner, String ownerName, String rawName, int colour) {
        if (byPlayer.containsKey(owner)) return Result.ALREADY_ON_TEAM;
        String name = TeamRules.cleanName(rawName);
        if (name == null) return Result.BAD_NAME;
        if (teamNamed(name) != null) return Result.NAME_TAKEN;
        if (!TeamRules.validColour(colour)) return Result.BAD_COLOUR;
        UUID id = UUID.randomUUID();
        while (teams.containsKey(id)) id = UUID.randomUUID();
        Team team = new Team(id, name, colour, owner);
        team.put(owner, ownerName);
        teams.put(id, team);
        byPlayer.put(owner, team);
        invites.remove(owner);
        onChange.run();
        return Result.OK;
    }

    public synchronized Result rename(UUID by, String rawName) {
        Team team = byPlayer.get(by);
        if (team == null) return Result.NOT_ON_TEAM;
        if (!team.isOwner(by)) return Result.NOT_OWNER;
        String name = TeamRules.cleanName(rawName);
        if (name == null) return Result.BAD_NAME;
        Team other = teamNamed(name);
        if (other != null && other != team) return Result.NAME_TAKEN;
        team.setName(name);
        onChange.run();
        return Result.OK;
    }

    public synchronized Result colour(UUID by, int colour) {
        Team team = byPlayer.get(by);
        if (team == null) return Result.NOT_ON_TEAM;
        if (!team.isOwner(by)) return Result.NOT_OWNER;
        if (!TeamRules.validColour(colour)) return Result.BAD_COLOUR;
        team.setColour(colour);
        onChange.run();
        return Result.OK;
    }

    // Any member can ask someone. They have to say yes, and they can't already be on a team.
    public synchronized Result invite(UUID by, String byName, UUID target, long now) {
        Team team = byPlayer.get(by);
        if (team == null) return Result.NOT_ON_TEAM;
        if (by.equals(target)) return Result.SELF;
        if (team.has(target)) return Result.ALREADY_MEMBER;
        if (byPlayer.containsKey(target)) return Result.TARGET_HAS_TEAM;
        if (team.size() >= maxMembers) return Result.TEAM_FULL;
        List<Invite> theirs = live(target, now);
        for (Invite invite : theirs) {
            if (invite.team().equals(team.id())) return Result.ALREADY_INVITED;
        }
        Long until = saidNo.get(key(team.id(), target));
        if (until != null && now < until) return Result.ASK_LATER;
        if (theirs.size() >= TeamRules.MAX_INVITES_PER_PLAYER) return Result.TOO_MANY_INVITES;
        invites.computeIfAbsent(target, x -> new ArrayList<>()).add(new Invite(team.id(), target, by, byName, now + inviteMs));
        return Result.OK;
    }

    // team = null: the newest invite.
    public synchronized Result accept(UUID player, String name, UUID team, long now) {
        if (byPlayer.containsKey(player)) return Result.ALREADY_ON_TEAM;
        Invite invite = find(player, team, now);
        if (invite == null) return Result.NO_INVITE;
        Team joining = teams.get(invite.team());
        if (joining == null) return Result.NO_INVITE;
        if (joining.size() >= maxMembers) return Result.TEAM_FULL;
        joining.put(player, name);
        byPlayer.put(player, joining);
        invites.remove(player);
        onChange.run();
        return Result.OK;
    }

    // team = null: the newest invite. The same team can't ask again for a minute, so nobody gets pestered.
    public synchronized Result deny(UUID player, UUID team, long now) {
        Invite invite = find(player, team, now);
        if (invite == null) return Result.NO_INVITE;
        List<Invite> theirs = invites.get(player);
        theirs.remove(invite);
        if (theirs.isEmpty()) invites.remove(player);
        saidNo.put(key(invite.team(), player), now + TeamRules.ASK_AGAIN_MS);
        return Result.OK;
    }

    // If the owner leaves, whoever has been on the team longest takes over. The last one out ends the team.
    public synchronized Result leave(UUID player) {
        Team team = byPlayer.get(player);
        if (team == null) return Result.NOT_ON_TEAM;
        team.remove(player);
        byPlayer.remove(player);
        if (team.size() == 0) {
            forget(team);
        } else if (team.isOwner(player)) {
            team.setOwner(team.memberIds().iterator().next());
        }
        onChange.run();
        return Result.OK;
    }

    public synchronized Result kick(UUID by, UUID target) {
        Team team = byPlayer.get(by);
        if (team == null) return Result.NOT_ON_TEAM;
        if (!team.isOwner(by)) return Result.NOT_OWNER;
        if (by.equals(target)) return Result.SELF;
        if (!team.has(target)) return Result.NOT_A_MEMBER;
        team.remove(target);
        byPlayer.remove(target);
        onChange.run();
        return Result.OK;
    }

    public synchronized Result disband(UUID by) {
        Team team = byPlayer.get(by);
        if (team == null) return Result.NOT_ON_TEAM;
        if (!team.isOwner(by)) return Result.NOT_OWNER;
        for (UUID member : team.memberIds()) {
            byPlayer.remove(member);
            team.remove(member);
        }
        forget(team);
        onChange.run();
        return Result.OK;
    }

    public synchronized Result transfer(UUID by, UUID target) {
        Team team = byPlayer.get(by);
        if (team == null) return Result.NOT_ON_TEAM;
        if (!team.isOwner(by)) return Result.NOT_OWNER;
        if (by.equals(target)) return Result.SELF;
        if (!team.has(target)) return Result.NOT_A_MEMBER;
        team.setOwner(target);
        onChange.run();
        return Result.OK;
    }

    // Players can change their name, so it's refreshed when they log in. True if it was different.
    public synchronized boolean updateName(UUID player, String name) {
        Team team = byPlayer.get(player);
        if (team == null || name.equals(team.memberName(player))) return false;
        team.put(player, name);
        onChange.run();
        return true;
    }

    public synchronized List<Invite> invitesFor(UUID player, long now) {
        return new ArrayList<>(live(player, now));
    }

    // Who's on this player's team, including them. Empty when they aren't on one.
    public synchronized Set<UUID> teammates(UUID player) {
        Team team = teamOf(player);
        return team == null ? Set.of() : team.memberIds();
    }

    public synchronized boolean sameTeam(UUID a, UUID b) {
        Team team = teamOf(a);
        return team != null && team == teamOf(b);
    }

    // Drops invites nobody answered. Gives back whose list got shorter.
    public synchronized Set<UUID> dropOld(long now) {
        Set<UUID> changed = new LinkedHashSet<>();
        for (Iterator<Map.Entry<UUID, List<Invite>>> it = invites.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, List<Invite>> e = it.next();
            if (e.getValue().removeIf(invite -> invite.expired(now))) changed.add(e.getKey());
            if (e.getValue().isEmpty()) it.remove();
        }
        saidNo.values().removeIf(until -> now >= until);
        return changed;
    }

    // Puts a saved team back. Bad data is mended rather than trusted: a player already on another team is left out, an owner
    // who isn't a member is replaced, and a team with nobody on it or a name that's gone is dropped.
    public synchronized boolean restore(UUID id, String savedName, int colour, UUID owner, Map<UUID, String> members) {
        String name = TeamRules.cleanName(savedName);
        if (id == null || name == null || teams.containsKey(id) || teamNamed(name) != null) return false;
        Map<UUID, String> kept = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> e : members.entrySet()) {
            if (!byPlayer.containsKey(e.getKey())) kept.put(e.getKey(), e.getValue());
        }
        if (kept.isEmpty()) return false;
        UUID boss = kept.containsKey(owner) ? owner : kept.keySet().iterator().next();
        Team team = new Team(id, name, TeamRules.validColour(colour) ? colour : 0, boss);
        kept.forEach(team::put);
        teams.put(id, team);
        for (UUID member : kept.keySet()) byPlayer.put(member, team);
        return true;
    }

    private void forget(Team team) {
        teams.remove(team.id());
        for (Iterator<List<Invite>> it = invites.values().iterator(); it.hasNext(); ) {
            List<Invite> list = it.next();
            list.removeIf(invite -> invite.team().equals(team.id()));
            if (list.isEmpty()) it.remove();
        }
    }

    private List<Invite> live(UUID player, long now) {
        List<Invite> theirs = invites.get(player);
        if (theirs == null) return List.of();
        theirs.removeIf(invite -> invite.expired(now));
        if (theirs.isEmpty()) {
            invites.remove(player);
            return List.of();
        }
        return theirs;
    }

    private Invite find(UUID player, UUID team, long now) {
        List<Invite> theirs = live(player, now);
        for (int i = theirs.size() - 1; i >= 0; i--) {
            if (team == null || theirs.get(i).team().equals(team)) return theirs.get(i);
        }
        return null;
    }

    private static String key(UUID team, UUID player) {
        return team + "/" + player;
    }
}
