package net.elytraautopilot;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import java.io.FileWriter;
import java.util.Scanner;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v1.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.network.MessageType;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ElytraAutoPilot implements ModInitializer, net.fabricmc.api.ClientModInitializer {
    public ElytraConfig config;

    private static KeyBinding keyBinding;
    public static ElytraAutoPilot instance;

    private boolean lastPressed = false;

    private MinecraftClient minecraftClient;

    public boolean showHud;
    private boolean autoFlight;

    private boolean startCooldown;
    private int cooldown = 0;

    private boolean onTakeoff;
    private double pitchMod = 1f;

    private Vec3d previousPosition;
    private double currentVelocity;
    private double currentVelocityHorizontal;

    public boolean isDescending;
    public boolean pullUp;
    public boolean pullDown;

    private double velHigh = 0f;
    private double velLow = 0f;

    private int argXpos;
    private int argZpos;
    private boolean isChained = false;
    private boolean isflytoActive = false;
    private boolean isLanding = false;

    private int _tick = 0;
    private int _index = -1;
    private double distance = 0f;
    private double groundheight;
    private double altitude;
    private List<Double> velocityList = new ArrayList<>();
    private List<Double> velocityListHorizontal = new ArrayList<>();

    File configFile;

    public LiteralText[] hudString;

	@Override
	public void onInitialize() {
        ClientCommandManager.DISPATCHER.register(
                ClientCommandManager.literal("flyto")
                .then(ClientCommandManager.argument("X", IntegerArgumentType.integer(-2000000000, 2000000000))
                        .then(ClientCommandManager.argument("Z", IntegerArgumentType.integer(-2000000000, 2000000000))
                                .executes(context -> {
                                    if (minecraftClient.player == null) return 1;
                                    if (minecraftClient.player.isFallFlying()) {
                                        if (groundheight > config.minHeight) {
                                            autoFlight = true;
                                            argXpos = IntegerArgumentType.getInteger(context, "X");
                                            argZpos = IntegerArgumentType.getInteger(context, "Z");
                                            isflytoActive = true;
                                            context.getSource().sendFeedback(new LiteralText("Flying to " + argXpos + ", " + argZpos).formatted(Formatting.GREEN));
                                        }
                                        else {
                                            context.getSource().sendFeedback(new LiteralText("You're too low to activate auto-flight!").formatted(Formatting.RED));
                                        }
                                    }
                                    else {
                                        context.getSource().sendFeedback(new LiteralText("Start flying to activate fly-to").formatted(Formatting.RED));
                                    }
                                    return 1;
                                }))));
        ClientCommandManager.DISPATCHER.register(
                ClientCommandManager.literal("takeoff")
                        .then(ClientCommandManager.argument("X", IntegerArgumentType.integer(-2000000000, 2000000000))
                                .then(ClientCommandManager.argument("Z", IntegerArgumentType.integer(-2000000000, 2000000000))
                                        .executes(context -> {
                                            argXpos = IntegerArgumentType.getInteger(context, "X");
                                            argZpos = IntegerArgumentType.getInteger(context, "Z");
                                            isChained = true;
                                            takeoff();
                                            return 1;
                                        })))
                        .executes(context -> {
                            takeoff();
                            return 1;
                                }));
        keyBinding = new KeyBinding(
                "key.elytraautopilot.toggle", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_R, // The keycode of the key
                "text.elytraautopilot.title" // The translation key of the keybinding's category.
        );

        KeyBindingHelper.registerKeyBinding(keyBinding);

        lastPressed = false;
        HudRenderCallback.EVENT.register((matrixStack, tickDelta) -> ElytraAutoPilot.this.onScreenTick());
        ClientTickEvents.END_CLIENT_TICK.register(e -> this.onClientTick());
        ElytraAutoPilot.instance = this;
        Path configdir = FabricLoader.getInstance().getConfigDir();
        String moddir = "/elytraautopilot/config.json";
        this.configFile = new File(configdir + moddir);
        loadSettings();
	}
	private void takeoff()
    {
        if (!onTakeoff) {
            if (minecraftClient.player != null) {
                Item itemMain = minecraftClient.player.getMainHandStack().getItem();
                Item itemOff = minecraftClient.player.getOffHandStack().getItem();
                Item itemChest = minecraftClient.player.getInventory().armor.get(2).getItem();
                //System.out.println(itemMain.toString());
                if (!itemChest.toString().equals("elytra")) {
                    minecraftClient.inGameHud.addChatMessage(MessageType.SYSTEM, new LiteralText("Equip an elytra to start flying").formatted(Formatting.RED), minecraftClient.player.getUuid());
                    return;
                }
                if (!itemMain.toString().equals("firework_rocket")){
                    if (!itemOff.toString().equals("firework_rocket")){
                        minecraftClient.inGameHud.addChatMessage(MessageType.SYSTEM, new LiteralText("Hold some fireworks in your hand to start flying").formatted(Formatting.RED), minecraftClient.player.getUuid());
                        return;
                    }
                }

                World world = minecraftClient.player.world;
                Vec3d clientPos = minecraftClient.player.getPos();
                int l = world.getTopY();
                int n = 2;
                double c = clientPos.getY();
                for (double i = c; i < l; i++) {
                    BlockPos blockPos = new BlockPos(clientPos.getX(), clientPos.getY() + n, clientPos.getZ());
                    //System.out.println(blockPos);
                    if (!world.getBlockState(blockPos).isAir()) {
                        minecraftClient.inGameHud.addChatMessage(MessageType.SYSTEM, new LiteralText("Make sure you have a clear view of the sky above you and try again").formatted(Formatting.RED), minecraftClient.player.getUuid());
                        return;
                    }
                    n++;
                }
                startCooldown = true;
                minecraftClient.options.keyJump.setPressed(true);
            }
            return;
        }
        if (minecraftClient.player != null) {
            if (groundheight > config.minHeight) {
                onTakeoff = false;
                minecraftClient.options.keyUse.setPressed(false);
                minecraftClient.options.keyJump.setPressed(false);
                autoFlight = true;
                pitchMod = 3f;
                if (isChained) {
                    isflytoActive = true;
                    isChained = false;
                    minecraftClient.inGameHud.addChatMessage(MessageType.SYSTEM, new LiteralText("Flying to " + argXpos + ", " + argZpos).formatted(Formatting.GREEN), minecraftClient.player.getUuid());
                }
                return;
            }
            if (!minecraftClient.player.isFallFlying()) minecraftClient.options.keyJump.setPressed(!minecraftClient.options.keyJump.isPressed());
            Item itemMain = minecraftClient.player.getMainHandStack().getItem();
            Item itemOff = minecraftClient.player.getOffHandStack().getItem();
            boolean hasFirework = (itemMain.toString().equals("firework_rocket") || itemOff.toString().equals("firework_rocket"));
            if (!hasFirework){
                minecraftClient.options.keyUse.setPressed(false);
                minecraftClient.options.keyJump.setPressed(false);
                onTakeoff = false;
                minecraftClient.inGameHud.addChatMessage(MessageType.SYSTEM, new LiteralText("There are no fireworks in your hand! Aborting takeoff").formatted(Formatting.RED), minecraftClient.player.getUuid());
            }
            else minecraftClient.options.keyUse.setPressed(currentVelocity < 0.75f && minecraftClient.player.getPitch() == -90f);
        }
    }
	private void createAndShowSettings()
    {
        ConfigBuilder configBuilder = ConfigBuilder.create().setTitle(new TranslatableText("text.elytraautopilot.title")).setSavingRunnable(this::saveSettings);
        ConfigCategory categoryGui = configBuilder.getOrCreateCategory(new TranslatableText("text.elytraautopilot.gui"));
        ConfigCategory categoryFlightProfile = configBuilder.getOrCreateCategory(new TranslatableText(("text.elytraautopilot.flightprofile")));

        ConfigEntryBuilder entryBuilder = ConfigEntryBuilder.create();

        categoryGui.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("text.elytraautopilot.showgui"), config.showgui).setDefaultValue(config.showgui).setSaveConsumer((x) -> config.showgui = x).build());
        categoryGui.addEntry(entryBuilder.startIntField(new TranslatableText("text.elytraautopilot.guiX"), config.guiX).setDefaultValue(config.guiX).setSaveConsumer((x) -> config.guiX = x).build());
        categoryGui.addEntry(entryBuilder.startIntField(new TranslatableText("text.elytraautopilot.guiY"), config.guiY).setDefaultValue(config.guiY).setSaveConsumer((y) -> config.guiY = y).build());

        categoryFlightProfile.addEntry(entryBuilder.startDoubleField(new TranslatableText("text.elytraautopilot.maxHeight"), config.maxHeight).setDefaultValue(config.maxHeight).setSaveConsumer((y) -> config.maxHeight = y).build());
        categoryFlightProfile.addEntry(entryBuilder.startDoubleField(new TranslatableText("text.elytraautopilot.minHeight"), config.minHeight).setDefaultValue(config.minHeight).setSaveConsumer((y) -> config.minHeight = y).build());
        categoryFlightProfile.addEntry(entryBuilder.startDoubleField(new TranslatableText("text.elytraautopilot.turningSpeed"), config.turningSpeed).setDefaultValue(config.turningSpeed).setSaveConsumer((x) -> config.turningSpeed = x).build());
        categoryFlightProfile.addEntry(entryBuilder.startDoubleField(new TranslatableText("text.elytraautopilot.takeOffPull"), config.takeOffPull).setDefaultValue(config.takeOffPull).setSaveConsumer((x) -> config.takeOffPull = x).build());
        categoryFlightProfile.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("text.elytraautopilot.autoLanding"), config.autoLanding).setDefaultValue(config.autoLanding).setSaveConsumer((x) -> config.autoLanding = x).build());
        categoryFlightProfile.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("text.elytraautopilot.riskyLanding"), config.riskyLanding).setDefaultValue(config.riskyLanding).setSaveConsumer((x) -> config.riskyLanding = x).build());
        //categoryFlightProfile.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("text.elytraautopilot.poweredFlight"), config.poweredFlight).setDefaultValue(config.poweredFlight).setSaveConsumer((x) -> config.poweredFlight = x).build());

        minecraftClient.setScreen(configBuilder.build());

    }

    private void saveSettings()
    {
        Gson gson = new Gson();
        String configString = gson.toJson(config);
        try {
            FileWriter writer = new FileWriter(configFile);
            writer.write(configString);
            writer.close();
            System.out.println("Saved settings");

        } catch (IOException e) {
            System.out.println("Error saving settings!");
        }
    }
    private void loadSettings() {

	    config = new ElytraConfig();
	    if (!configFile.exists() && configFile.getParentFile().mkdirs()){
            try {
                if (configFile.createNewFile()) {
                    System.out.println("Created new config file");
                }
            } catch (IOException e) {
                System.out.println("Unable to load ElytraAutoPilot settings! Using default config");
            }
        }
	    else {
            try {
                Scanner scanner = new Scanner(configFile);
                String output = "";
                while (scanner.hasNextLine()) {
                    scanner = new Scanner(configFile);
                    output = scanner.nextLine();
                }
                scanner.close();
                if (!output.equals("")) {
                    Gson gson = new Gson();
                    config = gson.fromJson(output, ElytraConfig.class);
                    System.out.println("Loaded Settings");
                }
                else System.out.println("Unable to load ElytraAutoPilot settings! Using default config");
            } catch (IOException e) {
                System.out.println("Unable to load ElytraAutoPilot settings! Using default config");
            }
        }
    }
	private void onScreenTick() {
        if (minecraftClient == null) minecraftClient = MinecraftClient.getInstance();
        if (config == null)loadSettings();

        //Fps adaptation
        float fps_delta = minecraftClient.getLastFrameDuration();
        float fps_result = 20/fps_delta;
        double speedMod = 60/fps_result;

        if (minecraftClient.isPaused()) return;
        _tick++;

        if (minecraftClient.player != null) {

            if (minecraftClient.player.isFallFlying())
                showHud = true;
            else {
                showHud = false;
                autoFlight = false;
            }
        }

        if(!lastPressed && keyBinding.isPressed()) {

            if (minecraftClient.player != null) {
                if (minecraftClient.player.isFallFlying()) {
                    if (!autoFlight && groundheight < config.minHeight){
                        minecraftClient.inGameHud.addChatMessage(MessageType.SYSTEM, new LiteralText("You're too low to activate auto-flight!").formatted(Formatting.RED), minecraftClient.player.getUuid());
                    }
                    else {
                        // If the player is flying an elytra, we start the auto flight
                        autoFlight = !autoFlight;
                        if (autoFlight) isDescending = true;
                    }
                }
                else {
                    // Otherwise we open the settings
                    createAndShowSettings();
                }
            }
        }
        lastPressed = keyBinding.isPressed();

        double velMod;
        if (minecraftClient.player == null){
            return;
        }

        if (onTakeoff) {
            float pitch = minecraftClient.player.getPitch();
            if (pitch > -90f) {
                minecraftClient.player.setPitch(pitch - (float) config.takeOffPull);
            }
            if (pitch <= -90f) minecraftClient.player.setPitch(-90f);
        }
        if (autoFlight) {
            altitude = minecraftClient.player.getPos().y;
        	float pitch = minecraftClient.player.getPitch();

            if (minecraftClient.player.isTouchingWater() || minecraftClient.player.isInLava()){
                isflytoActive = false;
                isLanding = false;
                autoFlight = false;
                return;
            }

            if (isDescending)
            {
                pullUp = false;
                pullDown = true;
                if (altitude > config.maxHeight) { //TODO fix this maybe
                    velHigh = 0.3f;
                }
                else if (altitude > config.maxHeight-10) {
                    velLow = 0.28475f;
                }
                velMod = Math.max(velHigh, velLow);
                if (currentVelocity >= config.pullDownMaxVelocity + velMod) {
                    isDescending = false;
                    pullDown = false;
                    pullUp = true;
                    pitchMod = 1f;
                }
            }
            else
            {
                velHigh = 0f;
                velLow = 0f;
                pullUp = true;
                pullDown = false;
                if (currentVelocity <= config.pullUpMinVelocity || altitude > config.maxHeight-10) {
                    isDescending = true;
                    pullDown = true;
                    pullUp = false;
                }        
            }
            if (isflytoActive) {
                if (isLanding) {
                    if (!config.autoLanding){
                        isflytoActive = false;
                        isLanding = false;
                        return;
                    }
                    isDescending = true;
                    if (config.riskyLanding && groundheight > 60) {
                        pitch = minecraftClient.player.getPitch();
                        minecraftClient.player.setPitch((float) (pitch + config.takeOffPull*speedMod));
                        pitch = minecraftClient.player.getPitch();
                        if (pitch > 90f) minecraftClient.player.setPitch(90f);
                    }
                    else {
                        float yaw = MathHelper.wrapDegrees(minecraftClient.player.getYaw());
                        minecraftClient.player.setYaw((float) (yaw + config.autoLandSpeed*speedMod));
                        minecraftClient.player.setPitch(30f);
                    }
                }
                else {
                    Vec3d playerPosition = minecraftClient.player.getPos();
                    double f = (double) argXpos - playerPosition.x;
                    double d = (double) argZpos - playerPosition.z;
                    float targetYaw = MathHelper.wrapDegrees((float) (MathHelper.atan2(d, f) * 57.2957763671875D) - 90.0F);
                    float yaw = MathHelper.wrapDegrees(minecraftClient.player.getYaw());
                    if (Math.abs(yaw-targetYaw) < config.turningSpeed*2*speedMod) minecraftClient.player.setYaw(targetYaw);
                    else {
                        if (yaw < targetYaw) minecraftClient.player.setYaw((float) (yaw + config.turningSpeed*speedMod));
                        if (yaw > targetYaw) minecraftClient.player.setYaw((float) (yaw - config.turningSpeed*speedMod));
                    }
                    distance = Math.sqrt(f * f + d * d);
                    if (distance < 20) {
                        isLanding = true;
                    }
                }
            }
            if (pullUp && !isLanding) { //TODO add powered flight
            	minecraftClient.player.setPitch((float) (pitch - config.pullUpSpeed*speedMod));
            	pitch = minecraftClient.player.getPitch();
                if (pitch <= config.pullUpAngle) {
                	minecraftClient.player.setPitch((float) config.pullUpAngle);
                }

            }

            if (pullDown && !isLanding) {
                minecraftClient.player.setPitch((float) (pitch + config.pullDownSpeed*pitchMod*speedMod));
                pitch = minecraftClient.player.getPitch();
                if (pitch >= config.pullDownAngle) {
                    minecraftClient.player.setPitch((float) config.pullDownAngle);
                }
            }

        }
        else
        {
            velHigh = 0f;
            velLow = 0f;
        	isLanding = false;
            isflytoActive = false;
            pullUp = false;
            pitchMod = 1f;
            pullDown = false;
        }
    }

    private void onClientTick() {
	    if (startCooldown) {
	        if (cooldown < 5) cooldown++;
	        if (cooldown == 5) {
	            cooldown = 0;
	            startCooldown = false;
	            onTakeoff = true;
            }
        }
	    if (onTakeoff) {
            takeoff();
        }
        if (showHud) {
            if (minecraftClient.player == null){
                autoFlight = false;
                onTakeoff = false;
                return;
            }
            computeVelocity();

            altitude = minecraftClient.player.getPos().y;
            double avgVelocity = 0f;
            double avgHorizontalVelocity = 0f;

            if (_tick >= 20) {
                _index++;
                if (_index >= 60) _index = 0;
                if (velocityList.size()< 60) {
                    velocityList.add(currentVelocity);
                    velocityListHorizontal.add(currentVelocityHorizontal);
                }
                else {
                    velocityList.set(_index, currentVelocity);
                    velocityListHorizontal.set(_index, currentVelocityHorizontal);
                }
                World world = minecraftClient.player.world;
                int l = world.getBottomY();
                Vec3d clientPos = minecraftClient.player.getPos();
                if (!minecraftClient.player.world.isChunkLoaded((int) clientPos.getX(), (int) clientPos.getZ())){
                    groundheight = -1f;
                }
                else {
                    for (double i = clientPos.getY(); i > l; i--) {
                        BlockPos blockPos = new BlockPos(clientPos.getX(), i, clientPos.getZ());
                        if (world.getBlockState(blockPos).isSolidBlock(world, blockPos)) {
                            groundheight = clientPos.getY() - i;
                            break;
                        }
                        else groundheight = -1f;
                    }
                }

                _tick = 0;
            }
            if (velocityList.size() >= 5) {
                avgVelocity = velocityList.stream().mapToDouble(val -> val).average().orElse(0.0);
                avgHorizontalVelocity = velocityListHorizontal.stream().mapToDouble(val -> val).average().orElse(0.0);
            }
            if (hudString == null) hudString = new LiteralText[9];
            if (!config.showgui || minecraftClient.options.debugEnabled) { //TODO make this more colorful
                hudString[0] = new LiteralText("");
                hudString[1] = new LiteralText("");
                hudString[2] = new LiteralText("");
                hudString[3] = new LiteralText("");
                hudString[4] = new LiteralText("");
                hudString[5] = new LiteralText("");
                hudString[6] = new LiteralText("");
                hudString[7] = new LiteralText("");
                hudString[8] = new LiteralText("");
                return;
            }
            if (autoFlight) {
                hudString[0] = new LiteralText("Auto flight : True");
            }
            else {
                hudString[0] = new LiteralText("Auto flight : False");
            }
            hudString[1] = new LiteralText("Altitude : " + String.format("%.2f", altitude));
            if (groundheight == -1f) {
                hudString[2] = new LiteralText("Height from ground : ???");
            }
            else hudString[2] = new LiteralText("Height from ground : " + groundheight);
            hudString[3] = new LiteralText("Speed : " + String.format("%.2f", currentVelocity * 20) + " m/s");
            if (avgVelocity == 0f) {
                hudString[4] = new LiteralText("Calculating...");
                hudString[5] = new LiteralText("");
            }
            else {
                hudString[4] = new LiteralText("Avg. Speed : " + String.format("%.2f", avgVelocity * 20) + " m/s");
                hudString[5] = new LiteralText("Avg. Horizontal Speed : " + String.format("%.2f", avgHorizontalVelocity * 20) + " m/s");
            }
            if (isflytoActive) {
                hudString[6] = new LiteralText("Flying to : " + (argXpos) + ", " + (argZpos));
                if (distance != 0f) {
                    hudString[7] = new LiteralText("ETA : " + String.format("%.1f",(distance/(avgHorizontalVelocity * 20))) + " seconds");
                }
                hudString[8] = new LiteralText("Auto land : " + (config.autoLanding ? "Enabled" : "Disabled"));
                if (isLanding) {
                    hudString[7] = new LiteralText("Landing...");
                }
            }
            else {
                hudString[6] = new LiteralText("");
                hudString[7] = new LiteralText("");
                hudString[8] = new LiteralText("");
            }
        }
        else {
            velocityList.clear();
            velocityListHorizontal.clear();
            previousPosition = null;
        }
    }

    private void computeVelocity()
    {
        Vec3d newPosition;
        if (minecraftClient.player != null && !minecraftClient.isPaused()) {
            newPosition = minecraftClient.player.getPos();
            if (previousPosition == null)
                previousPosition = newPosition;

            Vec3d difference = new Vec3d(newPosition.x - previousPosition.x, newPosition.y - previousPosition.y, newPosition.z - previousPosition.z);
            Vec3d difference_horizontal = new Vec3d(newPosition.x - previousPosition.x, 0, newPosition.z - previousPosition.z);
            previousPosition = newPosition;

            currentVelocity = difference.length();
            currentVelocityHorizontal = difference_horizontal.length();
        }
    }

	@Override
	public void onInitializeClient() {
        System.out.println("Client ElytraAutoPilot active");
	}
}
