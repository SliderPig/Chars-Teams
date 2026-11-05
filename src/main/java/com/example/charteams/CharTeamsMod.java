package com.example.charteams;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

// Char's Teams: one team per player, a small menu, and /teams for everyone. Nobody has to have it.
@Mod(CharTeamsMod.MODID)
public class CharTeamsMod {
    public static final String MODID = "charteams";

    public CharTeamsMod() {
        Net.register();
        MinecraftForge.EVENT_BUS.register(new TeamService());
        if (FMLEnvironment.dist == Dist.CLIENT) com.example.charteams.client.ClientSetup.init();
    }
}
