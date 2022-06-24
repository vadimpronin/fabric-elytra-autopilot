package net.elytraautopilot.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.elytraautopilot.ElytraAutoPilot;
import net.elytraautopilot.config.ModConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ClientCommands {
    public static void register(ElytraAutoPilot main, MinecraftClient minecraftClient) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("flyto")
                    .then(ClientCommandManager.argument("X", IntegerArgumentType.integer(-2000000000, 2000000000))
                            .then(ClientCommandManager.argument("Z", IntegerArgumentType.integer(-2000000000, 2000000000))
                                    .executes(context -> {
                                        if (minecraftClient.player == null) return 1;
                                        if (minecraftClient.player.isFallFlying()) { //If the player is flying
                                            if (main.groundheight > ModConfig.flightprofile.minHeight) { //If above required height
                                                main.autoFlight = true;
                                                main.argXpos = IntegerArgumentType.getInteger(context, "X");
                                                main.argZpos = IntegerArgumentType.getInteger(context, "Z");
                                                main.isflytoActive = true;
                                                main.pitchMod = 3f;
                                                context.getSource().sendFeedback(Text.translatable("text.elytraautopilot.flyto", main.argXpos, main.argZpos).formatted(Formatting.GREEN));
                                            }
                                            else {
                                                minecraftClient.player.sendMessage(Text.translatable("text.elytraautopilot.autoFlightFail.tooLow").formatted(Formatting.RED), true);
                                            }
                                        }
                                        else {
                                            minecraftClient.player.sendMessage(Text.translatable("text.elytraautopilot.flytoFail.flyingRequired").formatted(Formatting.RED), true);
                                        }
                                        return 1;
                                    })))));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("takeoff")
                    .then(ClientCommandManager.argument("X", IntegerArgumentType.integer(-2000000000, 2000000000))
                            .then(ClientCommandManager.argument("Z", IntegerArgumentType.integer(-2000000000, 2000000000))
                                    .executes(context -> { //With coordinates
                                        main.argXpos = IntegerArgumentType.getInteger(context, "X");
                                        main.argZpos = IntegerArgumentType.getInteger(context, "Z");
                                        main.isChained = true; //Chains fly-to command
                                        main.takeoff();
                                        return 1;
                                    })))
                    .executes(context -> { //Without coordinates
                        main.takeoff();
                        return 1;
                    })));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("land")
                    .executes(context -> {
                        main.forceLand = true;
                        return 1;
                    })));

    }
}
