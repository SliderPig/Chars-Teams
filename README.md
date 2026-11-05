# Char's Teams (`charteams`)

A small team mod for Forge 1.20.1. Nobody has to have it: PCs without it can join a server that has it, and PCs with it can join a server without it.

## What it does
- One team per player. Make one, rename it, pick a colour, invite friends (they must accept), remove a member (owner only), leave, disband, hand over ownership.
- The U key (change it in Controls) opens a small pink menu: your team, members with heads, online dot and health, an invite box that suggests online players, Remove / Owner / Leave / Disband, colours, and waiting invites with Accept / No.
- `/teams` works for everyone, also for players whose PC lacks the mod:
  `/teams`, `create <name>`, `invite <player>`, `accept [team]`, `deny [team]`, `leave`, `kick <member>`, `disband`, `list`, `info [team]`, `rename <name>`, `colour <name>`, `owner <member>`.
  Invites show clickable [Accept] / [No thanks] in chat.
- About once a second the server sends each teammate's position, dimension and health to teammates whose PC has the mod (so a map mod can show them).
- Teams are saved in the overworld's `data/charteams.dat`. Invites last 5 minutes and are not saved. Up to 16 members.

## Using it from another mod (by reflection, so it is never required)
Server side, `com.example.charteams.api.CharTeams` (static):
`String teamName(UUID)` (null = none), `Set<UUID> teammates(UUID)` (includes the player, empty when none), `boolean sameTeam(UUID, UUID)`.

PC side, `com.example.charteams.api.CharTeamsClient` (static):
`List<Mate> mates()` (teammates except you), `String myTeam()`.
`com.example.charteams.api.Mate` is a record: `id, name, x, y, z, dimension, health, maxHealth` (accessors of those names).

```java
try {
    Class<?> c = Class.forName("com.example.charteams.api.CharTeams");
    boolean same = (boolean) c.getMethod("sameTeam", UUID.class, UUID.class).invoke(null, a, b);
} catch (ReflectiveOperationException | LinkageError e) {
    // the mod is not installed: nobody is on a team
}
```

## Building and testing
`gradlew test` runs the rules tests. `gradlew build -Psmoke -x test` then `python tools/run_real_client.py` starts the real game in `run-real/`,
plays with teams by command, opens the menu and saves screenshots to `run-real/screenshots`. The normal jar has no test code.
