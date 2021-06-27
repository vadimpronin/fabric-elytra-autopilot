package net.elytraautopilot;

import com.google.gson.Gson;
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
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.io.File;
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

    private Vec3d previousPosition;
    private double currentVelocity;

    public boolean isDescending;
    public boolean pullUp;
    public boolean pullDown;

    private double velHigh = 0f;
    private double velLow = 0f;

    private int argXpos;
    private int argZpos;
    private boolean isflytoActive = false;
    private boolean isLanding = false;

    private int _tick = 0;
    private int _index = -1;
    private List<Double> velocityList = new ArrayList<>();

    File configFile;

    public String[] hudString;

	@Override
	public void onInitialize() {
        System.out.println("Hello Fabric world client ElytraAutoPilot.onInitialize!");
        ClientCommandManager.DISPATCHER.register(
                ClientCommandManager.literal("flyto")
                .then(ClientCommandManager.argument("X", IntegerArgumentType.integer(-2000000000, 2000000000))
                        .then(ClientCommandManager.argument("Z", IntegerArgumentType.integer(-2000000000, 2000000000))
                                .executes(context -> {
                                    if (autoFlight) {
                                        argXpos = IntegerArgumentType.getInteger(context, "X");
                                        argZpos = IntegerArgumentType.getInteger(context, "Z");
                                        isflytoActive = true;
                                        context.getSource().sendFeedback(new LiteralText("Flying to " + argXpos + ", " + argZpos).formatted(Formatting.GREEN));
                                        return 1;
                                    }
                                    context.getSource().sendFeedback(new LiteralText("You need to have autoflight activated to use this command").formatted(Formatting.RED));
                                    return 1;
                                }
                                ))));
        keyBinding = new KeyBinding(
                "key.elytraautopilot.toggle", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_R, // The keycode of the key
                "text.elytraautopilot.title" // The translation key of the keybinding's category.
        );

        KeyBindingHelper.registerKeyBinding(keyBinding);

        lastPressed = false;
        System.out.println("Registering client tick");
        ClientTickEvents.END_CLIENT_TICK.register(e -> this.onTick()); //TODO FPS tick event or similar for smoother movements

        ElytraAutoPilot.instance = this;
        Path configdir = FabricLoader.getInstance().getConfigDir();
        String moddir = "/elytraautopilot/config.json";
        this.configFile = new File(configdir + moddir);
        loadSettings();
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

        categoryFlightProfile.addEntry(entryBuilder.startDoubleField(new TranslatableText("text.elytraautopilot.pullUpAngle"), config.pullUpAngle).setDefaultValue(config.pullUpAngle).setSaveConsumer((x) -> config.pullUpAngle = x).build());
        categoryFlightProfile.addEntry(entryBuilder.startDoubleField(new TranslatableText("text.elytraautopilot.pullDownAngle"), config.pullDownAngle).setDefaultValue(config.pullDownAngle).setSaveConsumer((x) -> config.pullDownAngle = x).build());
        categoryFlightProfile.addEntry(entryBuilder.startDoubleField(new TranslatableText("text.elytraautopilot.pullUpMinVelocity"), config.pullUpMinVelocity).setDefaultValue(config.pullUpMinVelocity).setSaveConsumer((x) -> config.pullUpMinVelocity = x).build());
        categoryFlightProfile.addEntry(entryBuilder.startDoubleField(new TranslatableText("text.elytraautopilot.pullDownMaxVelocity"), config.pullDownMaxVelocity).setDefaultValue(config.pullDownMaxVelocity).setSaveConsumer((x) -> config.pullDownMaxVelocity = x).build());
        categoryFlightProfile.addEntry(entryBuilder.startDoubleField(new TranslatableText("text.elytraautopilot.pullUpSpeed"), config.pullUpSpeed).setDefaultValue(config.pullUpSpeed).setSaveConsumer((x) -> config.pullUpSpeed = x).build());
        categoryFlightProfile.addEntry(entryBuilder.startDoubleField(new TranslatableText("text.elytraautopilot.pullDownSpeed"), config.pullDownSpeed).setDefaultValue(config.pullDownSpeed).setSaveConsumer((x) -> config.pullDownSpeed = x).build());
        categoryFlightProfile.addEntry(entryBuilder.startDoubleField(new TranslatableText("text.elytraautopilot.maxHeight"), config.maxHeight).setDefaultValue(config.maxHeight).setSaveConsumer((y) -> config.maxHeight = y).build());
        categoryFlightProfile.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("text.elytraautopilot.autoLanding"), config.autoLanding).setDefaultValue(config.autoLanding).setSaveConsumer((x) -> config.autoLanding = x).build());
        categoryFlightProfile.addEntry(entryBuilder.startDoubleField(new TranslatableText("text.elytraautopilot.autoLandSpeed"), config.autoLandSpeed).setDefaultValue(config.autoLandSpeed).setSaveConsumer((x) -> config.autoLandSpeed = x).build());
        categoryFlightProfile.addEntry(entryBuilder.startDoubleField(new TranslatableText("text.elytraautopilot.turningSpeed"), config.turningSpeed).setDefaultValue(config.turningSpeed).setSaveConsumer((x) -> config.turningSpeed = x).build());




        minecraftClient.openScreen(configBuilder.build());

    }

    private void saveSettings()
    {
        Gson gson = new Gson();
        String configString = gson.toJson(config);
        System.out.println(configString);

    }

    private void loadSettings() {

	    config = new ElytraConfig();

    }
	private void onTick() {
		double altitude;
		_tick++;
		
        if (minecraftClient == null) minecraftClient = MinecraftClient.getInstance();
        if (config == null) loadSettings();

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
                    // If the player is flying an elytra, we start the auto flight
                    autoFlight = !autoFlight;
                    if (autoFlight) isDescending = true;
                }
                else {
                    // Otherwise we open the settings
                    createAndShowSettings();
                }
            }
        }
        lastPressed = keyBinding.isPressed();

        double velMod;
        double distance = 0f;
        if (autoFlight) {
        	if (minecraftClient.player == null){
        	    autoFlight = false;
        	    return;
            }
            altitude = minecraftClient.player.getPos().y;
        	float pitch = minecraftClient.player.getPitch();
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
                    //TODO check for liquids below player
                    if (!config.autoLanding){
                        isflytoActive = false;
                        isLanding = false;
                        return;
                    }
                    isDescending = true;
                    float yaw = MathHelper.wrapDegrees(minecraftClient.player.getYaw());
                    minecraftClient.player.setYaw((float) (yaw + config.autoLandSpeed));
                }
                else {
                    Vec3d playerPosition = minecraftClient.player.getPos();
                    double f = (double) argXpos - playerPosition.x;
                    double d = (double) argZpos - playerPosition.z;
                    float targetYaw = MathHelper.wrapDegrees((float) (MathHelper.atan2(d, f) * 57.2957763671875D) - 90.0F);
                    float yaw = MathHelper.wrapDegrees(minecraftClient.player.getYaw());
                    if (Math.abs(yaw-targetYaw) < config.turningSpeed*2) minecraftClient.player.setYaw(targetYaw);
                    else {
                        if (yaw < targetYaw) minecraftClient.player.setYaw((float) (yaw + config.turningSpeed));
                        if (yaw > targetYaw) minecraftClient.player.setYaw((float) (yaw - config.turningSpeed));
                    }
                    distance = Math.sqrt(f * f + d * d);
                    if (distance < 20) {
                        isLanding = true;
                    }
                }
            }
            if (pullUp) {
            	minecraftClient.player.setPitch((float) (pitch - config.pullUpSpeed));
            	pitch = minecraftClient.player.getPitch();
            	
                if (pitch <= config.pullUpAngle) {
                	minecraftClient.player.setPitch((float) config.pullUpAngle);
                }
            }

            if (pullDown) {
                minecraftClient.player.setPitch((float) (pitch + config.pullDownSpeed));
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
            pullDown = false;
        }


        if (showHud) {
            if (minecraftClient.player == null){
                autoFlight = false;
                return;
            }
            computeVelocity();

            altitude = minecraftClient.player.getPos().y;
            double avgVelocity = 0f;

            if (_tick >= 20) {
                _index++;
                if (_index >= 60) _index = 0;
                if (velocityList.size()< 60) {
                    velocityList.add(currentVelocity);
                }
                else {
                    velocityList.set(_index, currentVelocity);
                }
                _tick = 0;
            }
            if (velocityList.size() >= 5) {
                avgVelocity = velocityList.stream().mapToDouble(val -> val).average().orElse(0.0);
            }
            if (hudString == null) hudString = new String[7];
            if (!config.showgui) {
                hudString[0] = "";
                hudString[1] = "";
                hudString[2] = "";
                hudString[3] = "";
                hudString[4] = "";
                hudString[5] = "";
                hudString[6] = "";
                return;
            }

            hudString[0] = "Auto flight : " + (autoFlight ? "Enabled" : "Disabled");
            hudString[1] = "Altitude : " + String.format("%.2f", altitude);
            hudString[2] = "Speed : " + String.format("%.2f", currentVelocity * 20) + " m/s";
            if (avgVelocity == 0f) {
                hudString[3] = "Calculating...";
            }
            else {
                hudString[3] = "Avg. Speed : " + String.format("%.2f", avgVelocity * 20) + " m/s";
            }
            if (isflytoActive) {
                hudString[4] = "Flying to : " + (argXpos) + ", " + (argZpos);
                if (distance != 0f) {
                    hudString[5] = "ETA : " + String.format("%.1f",(distance/(avgVelocity * 20))) + " seconds";
                }
                hudString[6] = "Auto land : " + (config.autoLanding ? "Enabled" : "Disabled");
                if (isLanding) {
                    hudString[5] = "Landing...";
                }
            }
            else {
                hudString[4] = "";
                hudString[5] = "";
                hudString[6] = "";
            }
        }
        else {
            velocityList.clear();
        }
    }

    private void computeVelocity()
    {
        Vec3d newPosition;
        if (minecraftClient.player != null) {
            newPosition = minecraftClient.player.getPos();
            if (previousPosition == null)
                previousPosition = newPosition;

            Vec3d difference = new Vec3d(newPosition.x - previousPosition.x, newPosition.y - previousPosition.y, newPosition.z - previousPosition.z);

            previousPosition = newPosition;

            currentVelocity = difference.length();
        }
    }

	@Override
	public void onInitializeClient() {
        System.out.println("Hello Fabric world client ElytraAutoPilot!");
	}
}
