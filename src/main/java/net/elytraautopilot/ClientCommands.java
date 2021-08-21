package net.elytraautopilot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

public class ClientCommands {
    public static ElytraConfig config;

    public static void register(ElytraAutoPilot main, MinecraftClient minecraftClient) {
        ClientCommandManager.DISPATCHER.register(
                ClientCommandManager.literal("flyto")
                        .then(ClientCommandManager.argument("X", IntegerArgumentType.integer(-2000000000, 2000000000))
                                .then(ClientCommandManager.argument("Z", IntegerArgumentType.integer(-2000000000, 2000000000))
                                        .executes(context -> {
                                            if (minecraftClient.player == null) return 1;
                                            if (minecraftClient.player.isFallFlying()) { //If the player is flying
                                                if (main.groundheight > config.minHeight) { //If above required height
                                                    main.autoFlight = true;
                                                    main.argXpos = IntegerArgumentType.getInteger(context, "X");
                                                    main.argZpos = IntegerArgumentType.getInteger(context, "Z");
                                                    main.isflytoActive = true;
                                                    context.getSource().sendFeedback(new LiteralText("Flying to " + main.argXpos + ", " + main.argZpos).formatted(Formatting.GREEN));
                                                } else {
                                                    minecraftClient.player.sendMessage(new LiteralText("You're too low to activate auto-flight!").formatted(Formatting.RED), true);
                                                }
                                            } else {
                                                minecraftClient.player.sendMessage(new LiteralText("Start flying to activate fly-to").formatted(Formatting.RED), true);
                                            }
                                            return 1;
                                        }))));
        ClientCommandManager.DISPATCHER.register(
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
                        }));
    }
}
