package com.example.charteams.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// One team. Members keep the order they joined in, so the longest-serving one takes over if the owner leaves.
// Only TeamBook changes it. memberIds() is a fresh copy after each change, so it's safe to hand out and keep.
public final class Team {
    private final UUID id;
    private String name;
    private int colour;
    private UUID owner;
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private Set<UUID> ids = Set.of();

    Team(UUID id, String name, int colour, UUID owner) {
        this.id = id;
        this.name = name;
        this.colour = colour;
        this.owner = owner;
    }

    public UUID id() {
        return id;
    }

    public synchronized String name() {
        return name;
    }

    public synchronized int colour() {
        return colour;
    }

    public synchronized UUID owner() {
        return owner;
    }

    public synchronized boolean isOwner(UUID player) {
        return owner.equals(player);
    }

    public synchronized boolean has(UUID player) {
        return members.containsKey(player);
    }

    public synchronized int size() {
        return members.size();
    }

    public synchronized Set<UUID> memberIds() {
        return ids;
    }

    public synchronized String memberName(UUID player) {
        return members.get(player);
    }

    public synchronized UUID memberNamed(String name) {
        for (Map.Entry<UUID, String> e : members.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        }
        return null;
    }

    // A copy in joining order.
    public synchronized Map<UUID, String> members() {
        return new LinkedHashMap<>(members);
    }

    synchronized void setName(String name) {
        this.name = name;
    }

    synchronized void setColour(int colour) {
        this.colour = colour;
    }

    synchronized void setOwner(UUID owner) {
        this.owner = owner;
    }

    synchronized void put(UUID player, String name) {
        members.put(player, name);
        ids = Collections.unmodifiableSet(new LinkedHashSet<>(members.keySet()));
    }

    synchronized void remove(UUID player) {
        members.remove(player);
        ids = Collections.unmodifiableSet(new LinkedHashSet<>(members.keySet()));
    }
}
