package com.example.charteams.api;

import java.util.UUID;

// A teammate as seen from a PC: where they are and how healthy. dimension is like "minecraft:overworld".
public record Mate(UUID id, String name, double x, double y, double z, String dimension, float health, float maxHealth) {}
