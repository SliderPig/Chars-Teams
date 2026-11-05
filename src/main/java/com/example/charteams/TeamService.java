package com.example.charteams;

import com.example.charteams.api.Mate;
import com.example.charteams.model.Invite;
import com.example.charteams.model.Team;
import com.example.charteams.model.TeamBook;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// The server's team list, and the once-a-second sending of positions to teammates whose PC has the mod.
public class TeamService {
    private static volatile TeamData data;
    private static MinecraftServer server;
    private static final Map<UUID, Net.State> lastState = new HashMap<>();
    private static final Map<UUID, List<Mate>> lastMates = new HashMap<>();
    private static int ticks;

    static TeamBook book() {
        TeamData d = data;
        return d == null ? null : d.book;
    }

    public static String teamName(UUID player) {
        TeamBook b = book();
        Team t = b == null || player == null ? null : b.teamOf(player);
        return t == null ? null : t.name();
    }

    public static Set<UUID> teammates(UUID player) {
        TeamBook b = book();
        return b == null || player == null ? Set.of() : b.teammates(player);
    }

    public static boolean sameTeam(UUID a, UUID b) {
        TeamBook book = book();
        return book != null && a != null && b != null && book.sameTeam(a, b);
    }

    static MinecraftServer server() {
        return server;
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        TeamCommands.register(e.getDispatcher());
    }

    @SubscribeEvent
    public void onStarted(ServerStartedEvent e) {
        server = e.getServer();
        data = server.overworld().getDataStorage().computeIfAbsent(TeamData::load, TeamData::new, TeamData.NAME);
        lastState.clear();
        lastMates.clear();
    }

    @SubscribeEvent
    public void onStopped(ServerStoppedEvent e) {
        data = null;
        server = null;
        lastState.clear();
        lastMates.clear();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || book() == null) return;
        book().updateName(p.getUUID(), p.getGameProfile().getName());
        lastState.remove(p.getUUID());
        lastMates.remove(p.getUUID());
        List<Invite> waiting = book().invitesFor(p.getUUID(), System.currentTimeMillis());
        if (!waiting.isEmpty()) TeamActions.say(p, "You have " + waiting.size() + " team invite(s). Type /teams to see them.");
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        lastState.remove(e.getEntity().getUUID());
        lastMates.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        TeamBook book = book();
        if (e.phase != TickEvent.Phase.END || book == null || server == null) return;
        ticks++;
        if (ticks % 5 != 0) return;
        long now = System.currentTimeMillis();
        if (ticks % 100 == 0) book.dropOld(now);
        boolean mates = ticks % 20 == 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!Net.hasMod(p)) continue;
            Net.State state = stateFor(book, p, now);
            if (!state.equals(lastState.get(p.getUUID()))) {
                lastState.put(p.getUUID(), state);
                Net.toClient(p, state);
            }
            if (mates) sendMates(book, p);
        }
    }

    private static Net.State stateFor(TeamBook book, ServerPlayer p, long now) {
        Team team = book.teamOf(p.getUUID());
        List<Net.Inv> invites = new ArrayList<>();
        for (Invite i : book.invitesFor(p.getUUID(), now)) {
            Team t = book.teamById(i.team());
            if (t != null) invites.add(new Net.Inv(t.id(), t.name(), i.inviterName()));
        }
        if (team == null) return new Net.State(null, 0, null, 0, List.of(), invites);
        List<Net.Member> members = new ArrayList<>();
        team.members().forEach((id, name) -> members.add(new Net.Member(id, name)));
        return new Net.State(team.name(), team.colour(), team.owner(), book.maxMembers(), members, invites);
    }

    private static void sendMates(TeamBook book, ServerPlayer p) {
        List<Mate> list = new ArrayList<>();
        for (UUID id : book.teammates(p.getUUID())) {
            if (id.equals(p.getUUID())) continue;
            ServerPlayer q = server.getPlayerList().getPlayer(id);
            if (q == null || q.isSpectator()) continue;
            list.add(new Mate(id, q.getGameProfile().getName(), q.getX(), q.getY(), q.getZ(), q.level().dimension().location().toString(), q.getHealth(), q.getMaxHealth()));
        }
        List<Mate> before = lastMates.get(p.getUUID());
        if (list.isEmpty() && (before == null || before.isEmpty())) return;
        lastMates.put(p.getUUID(), list);
        Net.toClient(p, new Net.Mates(list));
    }
}
