package com.example.charteams;

import com.example.charteams.api.CharTeams;
import com.example.charteams.api.CharTeamsClient;
import com.example.charteams.client.TeamScreen;
import com.example.charteams.model.TeamBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

// gradlew build -Psmoke -x test, then python tools/run_real_client.py: makes a flat world, plays with teams by command, opens the menu,
// takes screenshots (run-real/screenshots) and quits. Only compiled with -Psmoke, never in the normal jar.
@Mod.EventBusSubscriber(modid = CharTeamsMod.MODID, value = Dist.CLIENT)
public final class ClientChecks {
    private static final boolean ON = Boolean.getBoolean("charteams.smokeclient");
    private static boolean titleSeen, created;
    private static int ticks = -1;
    private static final UUID LUNA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID MOO = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    private ClientChecks() {}

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Opening e) {
        if (ON && e.getNewScreen() instanceof TitleScreen) titleSeen = true;
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    private static void server(Minecraft mc, java.util.function.Consumer<ServerPlayer> what) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> what.accept(server.getPlayerList().getPlayers().get(0)));
    }

    private static void command(ServerPlayer p, String cmd) {
        p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), cmd);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (!ON || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (titleSeen && !created) {
            created = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(6);
            mc.options.guiScale().set(2);
            mc.createWorldOpenFlows().createFreshLevel("smoke", new LevelSettings("smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getSingleplayerServer().getPlayerList().getPlayers().isEmpty()) return;
        ticks++;
        if (ticks == 60) {
            server(mc, p -> {
                TeamBook b = TeamService.book();
                b.create(LUNA, "Luna", "Moon Cats", 3);
                b.invite(LUNA, "Luna", p.getUUID(), System.currentTimeMillis());
                System.out.println("[CHECK] book ready: " + (b != null));
            });
        }
        if (ticks == 120) mc.setScreen(new TeamScreen());
        if (ticks == 140) shot(mc, "t1_no_team_with_invite");
        if (ticks == 150) {
            mc.setScreen(null);
            server(mc, p -> {
                command(p, "teams create Cat Club");
                TeamBook b = TeamService.book();
                UUID team = b.teamOf(p.getUUID()).id();
                long now = System.currentTimeMillis();
                b.invite(p.getUUID(), p.getGameProfile().getName(), LUNA, now);
                b.accept(LUNA, "Luna", team, now);
                b.invite(p.getUUID(), p.getGameProfile().getName(), MOO, now);
                b.accept(MOO, "MooMooCow", team, now);
                command(p, "teams colour purple");
                command(p, "teams list");
                command(p, "teams info");
                System.out.println("[CHECK] api teamName=" + CharTeams.teamName(p.getUUID()) + " mates=" + CharTeams.teammates(p.getUUID()).size() + " same=" + CharTeams.sameTeam(p.getUUID(), LUNA));
            });
        }
        if (ticks == 200) {
            System.out.println("[CHECK] client myTeam=" + CharTeamsClient.myTeam() + " mates=" + CharTeamsClient.mates().size());
            mc.setScreen(new TeamScreen());
        }
        if (ticks == 220) shot(mc, "t2_team");
        if (ticks == 230) {
            mc.setScreen(null);
            server(mc, p -> {
                command(p, "teams leave");
                System.out.println("[CHECK] after leave owner team=" + CharTeams.teamName(p.getUUID()));
            });
        }
        if (ticks == 260) {
            System.out.println("[CHECK] done");
            mc.stop();
        }
    }
}
