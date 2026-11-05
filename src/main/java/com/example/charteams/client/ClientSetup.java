package com.example.charteams.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

// PC side: the U key (change it in Controls), clearing on leaving a world, and copying the server's answers into the open menu.
public final class ClientSetup {
    private ClientSetup() {}

    static final KeyMapping OPEN = new KeyMapping("key.charteams.open", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.categories.charteams");

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN));
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onTick);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientState.clear());
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onChat);
    }

    private static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (mc.player != null && mc.screen == null) mc.setScreen(new TeamScreen());
        }
    }

    private static void onChat(ClientChatReceivedEvent e) {
        if (Minecraft.getInstance().screen instanceof TeamScreen screen) {
            String text = e.getMessage().getString();
            if (text.startsWith("[Teams] ")) screen.heard(text.substring(8));
        }
    }
}
