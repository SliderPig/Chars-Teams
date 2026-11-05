package com.example.charteams;

import com.example.charteams.api.Mate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

// Three small messages. acceptMissingOr: PCs without the mod can join a server with it, and the other way round.
public final class Net {
    private Net() {}

    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(CharTeamsMod.MODID, "main"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    public static final int CREATE = 0, INVITE = 1, ACCEPT = 2, DENY = 3, LEAVE = 4, KICK = 5, DISBAND = 6, RENAME = 7, COLOUR = 8, OWNER = 9;

    // PC to server: something the screen's buttons did. text = a name, number = a colour, id = a player or a team.
    public record Action(int kind, String text, int number, UUID id) {}

    public record Member(UUID id, String name) {}

    public record Inv(UUID team, String teamName, String inviter) {}

    // Server to PC: what the screen shows. team == null means no team.
    public record State(String team, int colour, UUID owner, int max, List<Member> members, List<Inv> invites) {}

    // Server to PC: where the teammates are right now.
    public record Mates(List<Mate> list) {}

    static void register() {
        CHANNEL.registerMessage(0, Action.class, (a, b) -> {
            b.writeVarInt(a.kind());
            b.writeUtf(a.text() == null ? "" : a.text(), 64);
            b.writeVarInt(a.number());
            b.writeBoolean(a.id() != null);
            if (a.id() != null) b.writeUUID(a.id());
        }, b -> new Action(b.readVarInt(), b.readUtf(64), b.readVarInt(), b.readBoolean() ? b.readUUID() : null), Net::onAction, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, State.class, Net::writeState, Net::readState, Net::onState, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Mates.class, (m, b) -> {
            b.writeVarInt(m.list().size());
            for (Mate x : m.list()) {
                b.writeUUID(x.id());
                b.writeUtf(x.name(), 64);
                b.writeDouble(x.x());
                b.writeDouble(x.y());
                b.writeDouble(x.z());
                b.writeUtf(x.dimension(), 128);
                b.writeFloat(x.health());
                b.writeFloat(x.maxHealth());
            }
        }, b -> {
            int n = Math.min(b.readVarInt(), 512);
            List<Mate> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(new Mate(b.readUUID(), b.readUtf(64), b.readDouble(), b.readDouble(), b.readDouble(), b.readUtf(128), b.readFloat(), b.readFloat()));
            return new Mates(list);
        }, Net::onMates, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static void writeState(State s, FriendlyByteBuf b) {
        b.writeBoolean(s.team() != null);
        if (s.team() != null) {
            b.writeUtf(s.team(), 64);
            b.writeVarInt(s.colour());
            b.writeUUID(s.owner());
            b.writeVarInt(s.max());
            b.writeVarInt(s.members().size());
            for (Member m : s.members()) {
                b.writeUUID(m.id());
                b.writeUtf(m.name(), 64);
            }
        }
        b.writeVarInt(s.invites().size());
        for (Inv i : s.invites()) {
            b.writeUUID(i.team());
            b.writeUtf(i.teamName(), 64);
            b.writeUtf(i.inviter(), 64);
        }
    }

    private static State readState(FriendlyByteBuf b) {
        String team = null;
        int colour = 0, max = 0;
        UUID owner = null;
        List<Member> members = new ArrayList<>();
        if (b.readBoolean()) {
            team = b.readUtf(64);
            colour = b.readVarInt();
            owner = b.readUUID();
            max = b.readVarInt();
            int n = Math.min(b.readVarInt(), 512);
            for (int i = 0; i < n; i++) members.add(new Member(b.readUUID(), b.readUtf(64)));
        }
        List<Inv> invites = new ArrayList<>();
        int k = Math.min(b.readVarInt(), 64);
        for (int i = 0; i < k; i++) invites.add(new Inv(b.readUUID(), b.readUtf(64), b.readUtf(64)));
        return new State(team, colour, owner, max, members, invites);
    }

    static boolean hasMod(ServerPlayer p) {
        return p.connection != null && CHANNEL.isRemotePresent(p.connection.connection);
    }

    static void toClient(ServerPlayer p, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), message);
    }

    public static void toServer(Action action) {
        CHANNEL.sendToServer(action);
    }

    private static void onAction(Action a, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer p = c.getSender();
        c.enqueueWork(() -> {
            if (p != null) TeamActions.fromScreen(p, a);
        });
        c.setPacketHandled(true);
    }

    private static void onState(State s, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.charteams.client.ClientState.set(s)));
        c.setPacketHandled(true);
    }

    private static void onMates(Mates m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.charteams.client.ClientState.set(m.list())));
        c.setPacketHandled(true);
    }
}
