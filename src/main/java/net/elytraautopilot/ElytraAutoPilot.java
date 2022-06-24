package net.elytraautopilot;

import com.terraformersmc.modmenu.ModMenu;
import me.lortseam.completeconfig.gui.ConfigScreenBuilder;
import me.lortseam.completeconfig.gui.cloth.ClothConfigScreenBuilder;
import net.elytraautopilot.commands.ClientCommands;
import net.elytraautopilot.config.ModConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ElytraAutoPilot implements ModInitializer, net.fabricmc.api.ClientModInitializer {
    private static final String modid = "elytraautopilot";
    public ElytraAutoPilot main = this;

    private static KeyBinding keyBinding;
    public static ElytraAutoPilot instance;

    private boolean lastPressed = false;

    public MinecraftClient minecraftClient;

    public boolean showHud;
    public boolean autoFlight;

    private boolean startCooldown;
    private int cooldown = 0;

    private boolean onTakeoff;
    public double pitchMod = 1f;

    private Vec3d previousPosition;
    private double currentVelocity;
    private double currentVelocityHorizontal;

    public boolean isDescending;
    public boolean pullUp;
    public boolean pullDown;

    private double velHigh = 0f;
    private double velLow = 0f;

    public int argXpos;
    public int argZpos;
    public boolean isChained = false;
    public boolean isflytoActive = false;
    public boolean forceLand = false;
    private boolean isLanding = false;

    private int _tick = 0;
    private int _index = -1;
    private double distance = 0f;
    public double groundheight;
    private List<Double> velocityList = new ArrayList<>();
    private List<Double> velocityListHorizontal = new ArrayList<>();


    public Text[] hudString;

	@Override
	public void onInitialize() {
        String key;
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
            key = "key." + modid + ".toggle";
        }
        else {
            key = "key." + modid + ".toggle_no_cloth";
        }

	    keyBinding = new KeyBinding(
                key, // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_R, // The keycode of the key
                "config." + modid + ".title" // The translation key of the keybinding's category.
        );

        KeyBindingHelper.registerKeyBinding(keyBinding);

        lastPressed = false;
        HudRenderCallback.EVENT.register((matrixStack, tickDelta) -> ElytraAutoPilot.this.onScreenTick());
        ClientTickEvents.END_CLIENT_TICK.register(e -> this.onClientTick());
        ElytraAutoPilot.instance = this;

        ModConfig config = new ModConfig(modid);
        config.load();
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
            ConfigScreenBuilder.setMain(modid, new ClothConfigScreenBuilder());
        }
        initCommands();
	}
	public void takeoff()
    {
        PlayerEntity player = minecraftClient.player;
        if (!onTakeoff) {
            if (player != null) {
                Item itemMain = player.getMainHandStack().getItem();
                Item itemOff = player.getOffHandStack().getItem();
                Item itemChest = player.getInventory().armor.get(2).getItem();
                int elytraDamage = player.getInventory().armor.get(2).getMaxDamage() - player.getInventory().armor.get(2).getDamage();
                if (!itemChest.toString().equals("elytra")) {
                    player.sendMessage(Text.translatable("text." + modid + ".takeoffFail.noElytraEquipped").formatted(Formatting.RED), true);
                    return;
                }
                if (elytraDamage == 1) {
                    player.sendMessage(Text.translatable("text." + modid + ".takeoffFail.elytraBroken").formatted(Formatting.RED), true);
                    return;
                }
                if (!itemMain.toString().equals("firework_rocket")){
                    if (!itemOff.toString().equals("firework_rocket")){
                        player.sendMessage(Text.translatable("text." + modid + ".takeoffFail.fireworkRequired").formatted(Formatting.RED), true);
                        return;
                    }
                }

                World world = player.world;
                Vec3d clientPos = player.getPos();
                int l = world.getTopY();
                int n = 2;
                double c = clientPos.getY();
                for (double i = c; i < l; i++) {
                    BlockPos blockPos = new BlockPos(clientPos.getX(), clientPos.getY() + n, clientPos.getZ());
                    //System.out.println(blockPos);
                    if (!world.getBlockState(blockPos).isAir()) {
                        player.sendMessage(Text.translatable("text." + modid + ".takeoffFail.clearSkyNeeded").formatted(Formatting.RED), true);
                        return;
                    }
                    n++;
                }
                startCooldown = true;
                minecraftClient.options.jumpKey.setPressed(true);
            }
            return;
        }
        if (player != null) {
            if (groundheight > ModConfig.flightprofile.minHeight) {
                onTakeoff = false;
                minecraftClient.options.useKey.setPressed(false);
                minecraftClient.options.jumpKey.setPressed(false);
                autoFlight = true;
                pitchMod = 3f;
                if (isChained) {
                    isflytoActive = true;
                    isChained = false;
                    minecraftClient.inGameHud.getChatHud().addMessage(Text.translatable("text." + modid + ".flyto", argXpos, argZpos).formatted(Formatting.GREEN));
                }
                return;
            }
            if (!player.isFallFlying()) minecraftClient.options.jumpKey.setPressed(!minecraftClient.options.jumpKey.isPressed());
            Item itemMain = player.getMainHandStack().getItem();
            Item itemOff = player.getOffHandStack().getItem();
            boolean hasFirework = (itemMain.toString().equals("firework_rocket") || itemOff.toString().equals("firework_rocket"));
            if (!hasFirework){
                if(ModConfig.flightprofile.fireworkHotswap){
                    ItemStack newFirework = null;
                    for (ItemStack itemStack : player.getInventory().main) {
                        if (itemStack.getItem().toString().equals("firework_rocket")) {
                            newFirework = itemStack;
                            break;
                        }
                    }
                    if (newFirework != null) {
                        int handSlot;
                        if (player.getOffHandStack().isEmpty()){
                            handSlot = 45; //Offhand slot refill
                        }
                        else{
                            handSlot = 36 + player.getInventory().selectedSlot; //Mainhand slot refill
                        }

                        assert minecraftClient.interactionManager != null;
                        minecraftClient.interactionManager.clickSlot(
                                player.playerScreenHandler.syncId,
                                handSlot,
                                player.getInventory().main.indexOf(newFirework),
                                SlotActionType.SWAP,
                                player
                        );
                    }
                    else{
                        minecraftClient.options.useKey.setPressed(false);
                        minecraftClient.options.jumpKey.setPressed(false);
                        onTakeoff = false;
                        player.sendMessage(Text.translatable("text." + modid + ".takeoffAbort.noFirework").formatted(Formatting.RED), true);
                    }
                }
                else{
                    minecraftClient.options.useKey.setPressed(false);
                    minecraftClient.options.jumpKey.setPressed(false);
                    onTakeoff = false;
                    player.sendMessage(Text.translatable("text." + modid + ".takeoffAbort.noFirework").formatted(Formatting.RED), true);
                }
            }
            else minecraftClient.options.useKey.setPressed(currentVelocity < 0.75f && player.getPitch() == -90f);
        }
    }
	private void onScreenTick() //Once every screen frame
    {
        //Fps adaptation (not perfect but works nicely most of the time)
        float fps_delta = minecraftClient.getLastFrameDuration();
        float fps_result = 20/fps_delta;
        double speedMod = 60/fps_result; //Adapt to base 60 FPS

        if (minecraftClient.isPaused() && minecraftClient.isInSingleplayer()) return;
        PlayerEntity player = minecraftClient.player;

        if (player == null){
            return;
        }

        if (onTakeoff) {
            float pitch = player.getPitch();
            if (pitch > -90f) {
                player.setPitch((float) (pitch - ModConfig.flightprofile.takeOffPull*speedMod));
            }
            if (pitch <= -90f) player.setPitch(-90f);
        }
        if (autoFlight) {
            int elytraDurability = player.getInventory().armor.get(2).getMaxDamage() - player.getInventory().armor.get(2).getDamage();
            if (ModConfig.flightprofile.elytraHotswap) {
                if (elytraDurability <= 5) { // Leave some leeway, so we don't stop flying
                    // Optimization: find the first elytra with sufficient durability
                    ItemStack newElytra = null;
                    int minDurability = 10;
                    for (ItemStack itemStack : player.getInventory().main) {
                        if (itemStack.getItem().toString().equals("elytra")) {
                            int itemDurability = itemStack.getMaxDamage() - itemStack.getDamage();
                            if (itemDurability >= minDurability) {
                                newElytra = itemStack;
                                break;
                            }
                        }
                    }
                    if (newElytra != null) {
                        int chestSlot = 6;
                        assert minecraftClient.interactionManager != null;
                        minecraftClient.interactionManager.clickSlot(
                            player.playerScreenHandler.syncId,
                            chestSlot,
                            player.getInventory().main.indexOf(newElytra),
                            SlotActionType.SWAP,
                            player
                        );
                        player.playSound(SoundEvents.ITEM_ARMOR_EQUIP_ELYTRA, 1.0F, 1.0F);
                        player.sendMessage(Text.translatable("text." + modid + ".swappedElytra").formatted(Formatting.GREEN), true);
                    }
                    else if (ModConfig.flightprofile.emergencyLand){
                        forceLand = true;
                    }
                }
            }
            else if (elytraDurability <= 30 && ModConfig.flightprofile.emergencyLand){
                forceLand = true;
            }
            float pitch = player.getPitch();
            if (isflytoActive || forceLand) {
                if (isLanding || forceLand) {
                    if (!forceLand && !ModConfig.flightprofile.autoLanding){
                        isflytoActive = false;
                        isLanding = false;
                        return;
                    }
                    isDescending = true;
                    if (ModConfig.flightprofile.riskyLanding && groundheight > 60) {
                        if (currentVelocityHorizontal > 0.3f || currentVelocity > 1.0f){
                            smoothLanding(player, speedMod);
                        }
                        else{
                            riskyLanding(player, speedMod);
                        }
                    }
                    else {
                        smoothLanding(player, speedMod);
                    }
                }
                else {
                    Vec3d playerPosition = player.getPos();
                    double f = (double) argXpos - playerPosition.x;
                    double d = (double) argZpos - playerPosition.z;
                    float targetYaw = MathHelper.wrapDegrees((float) (MathHelper.atan2(d, f) * 57.2957763671875D) - 90.0F);
                    float yaw = MathHelper.wrapDegrees(player.getYaw());
                    if (Math.abs(yaw-targetYaw) < ModConfig.flightprofile.turningSpeed*2*speedMod) player.setYaw(targetYaw);
                    else {
                        if (yaw < targetYaw) player.setYaw((float) (yaw + ModConfig.flightprofile.turningSpeed*speedMod));
                        if (yaw > targetYaw) player.setYaw((float) (yaw - ModConfig.flightprofile.turningSpeed*speedMod));
                    }
                    distance = Math.sqrt(f * f + d * d);
                    if (distance < 20) {
                        isLanding = true;
                    }
                }
            }
            if (pullUp && !(isLanding || forceLand)) { //TODO add powered flight
            	player.setPitch((float) (pitch - ModConfig.advanced.pullUpSpeed*speedMod));
            	pitch = player.getPitch();
                if (pitch <= ModConfig.advanced.pullUpAngle) {
                	player.setPitch((float) ModConfig.advanced.pullUpAngle);
                }
            }
            if (pullDown && !(isLanding || forceLand)) {
                player.setPitch((float) (pitch + ModConfig.advanced.pullDownSpeed*pitchMod*speedMod));
                pitch = player.getPitch();
                if (pitch >= ModConfig.advanced.pullDownAngle) {
                    player.setPitch((float) ModConfig.advanced.pullDownAngle);
                }
            }
        }
        else
        {
            velHigh = 0f;
            velLow = 0f;
        	isLanding = false;
            forceLand = false;
            isflytoActive = false;
            pullUp = false;
            pitchMod = 1f;
            pullDown = false;
        }
    }

    private void onClientTick() //20 times a second, before first screen tick
    {
        if (!(minecraftClient.isPaused() && minecraftClient.isInSingleplayer())) _tick++;
        double velMod;

        PlayerEntity player = minecraftClient.player;

        if (player == null){
            autoFlight = false;
            onTakeoff = false;
            return;
        }

        if (player.isFallFlying())
            showHud = true;
        else {
            showHud = false;
            autoFlight = false;
            groundheight = -1f;
        }

        double altitude;
        if (autoFlight) {
            altitude = player.getPos().y;

            if (player.isTouchingWater() || player.isInLava()){
                isflytoActive = false;
                isLanding = false;
                autoFlight = false;
                return;
            }

            if (isDescending)
            {
                pullUp = false;
                pullDown = true;
                if (altitude > ModConfig.flightprofile.maxHeight) { //TODO fix this maybe
                    velHigh = 0.3f;
                }
                else if (altitude > ModConfig.flightprofile.maxHeight-10) {
                    velLow = 0.28475f;
                }
                velMod = Math.max(velHigh, velLow);
                if (currentVelocity >= ModConfig.advanced.pullDownMaxVelocity + velMod) {
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
                if (currentVelocity <= ModConfig.advanced.pullUpMinVelocity || altitude > ModConfig.flightprofile.maxHeight-10) {
                    isDescending = true;
                    pullDown = true;
                    pullUp = false;
                }
            }
        }
        if(!lastPressed && keyBinding.isPressed()) {
            if (player.isFallFlying()) {
                if (!autoFlight && groundheight < ModConfig.flightprofile.minHeight){
                    player.sendMessage(Text.translatable("text." + modid + ".autoFlightFail.tooLow").formatted(Formatting.RED), true);
                }
                else {
                    // If the player is flying an elytra, we start the auto flight
                    autoFlight = !autoFlight;
                    if (autoFlight){
                        isDescending = true;
                        pitchMod = 3f;
                    }
                }
            }
            else {
                // Otherwise, we open the settings if cloth is loaded
                if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
                    Screen configScreen = ModMenu.getConfigScreen(modid, minecraftClient.currentScreen);
                    minecraftClient.setScreen(configScreen);
                }
            }
        }
        lastPressed = keyBinding.isPressed();

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
            computeVelocity();

            altitude = player.getPos().y;
            double avgVelocity = 0f;
            double avgHorizontalVelocity = 0f;
            int gticks = Math.max(1, ModConfig.advanced.groundCheckTicks);
            if (_tick >= gticks) {
                _index++;
                if (_index >= 1200/gticks) _index = 0;
                if (velocityList.size()< 1200/gticks) {
                    velocityList.add(currentVelocity);
                    velocityListHorizontal.add(currentVelocityHorizontal);
                }
                else {
                    velocityList.set(_index, currentVelocity);
                    velocityListHorizontal.set(_index, currentVelocityHorizontal);
                }
                World world = player.world;
                int l = world.getBottomY();
                Vec3d clientPos = player.getPos();
                for (double i = clientPos.getY(); i > l; i--) {
                    BlockPos blockPos = new BlockPos(clientPos.getX(), i, clientPos.getZ());
                    if (world.getBlockState(blockPos).isSolidBlock(world, blockPos)) {
                        groundheight = clientPos.getY() - i;
                        break;
                    }
                    else {
                        groundheight = clientPos.getY();
                    }
                }
                _tick = 0;
            }
            if (velocityList.size() >= 10) {
                avgVelocity = velocityList.stream().mapToDouble(val -> val).average().orElse(0.0);
                avgHorizontalVelocity = velocityListHorizontal.stream().mapToDouble(val -> val).average().orElse(0.0);
            }
            if (hudString == null) hudString = new Text[10];
            if (!ModConfig.gui.showgui || minecraftClient.options.debugEnabled) {
                hudString[0] = Text.of("");
                hudString[1] = Text.of("");
                hudString[2] = Text.of("");
                hudString[3] = Text.of("");
                hudString[4] = Text.of("");
                hudString[5] = Text.of("");
                hudString[6] = Text.of("");
                hudString[7] = Text.of("");
                hudString[8] = Text.of("");
                hudString[9] = Text.of("");
                return;
            }
            hudString[0] = Text.translatable("text." + modid + ".hud.toggleAutoFlight")
                    .append(Text.translatable(autoFlight ? "text." + modid + ".hud.true" : "text." + modid + ".hud.false")
                            .formatted(autoFlight ? Formatting.GREEN : Formatting.RED));

            hudString[1] = Text.translatable("text." + modid + ".hud.altitude", String.format("%.2f", altitude))
                    .formatted(Formatting.AQUA);
            hudString[2] = Text.translatable("text." + modid + ".hud.heightFromGround", (groundheight == -1f ? "???" : String.valueOf(Math.round(groundheight))))
                    .formatted(Formatting.AQUA);
            hudString[3] = Text.translatable("text." + modid + ".hud.neededHeight")
                    .formatted(Formatting.AQUA)
                        .append(Text.literal(groundheight > ModConfig.flightprofile.minHeight ? "Ready" : String.valueOf(Math.round(ModConfig.flightprofile.minHeight-groundheight)))
                            .formatted(groundheight > ModConfig.flightprofile.minHeight ? Formatting.GREEN : Formatting.RED));
            hudString[4] = Text.translatable("text." + modid + ".hud.speed", String.format("%.2f", currentVelocity * 20))
                    .formatted(Formatting.YELLOW);
            if (avgVelocity == 0f) {
                hudString[5] = Text.translatable("text." + modid + ".hud.calculating")
                        .formatted(Formatting.WHITE);
                hudString[6] = Text.of("");
            }
            else {
                hudString[5] = Text.translatable("text." + modid + ".hud.avgSpeed", String.format("%.2f", avgVelocity * 20))
                        .formatted(Formatting.YELLOW);
                hudString[6] = Text.translatable("text." + modid + ".hud.avgHSpeed", String.format("%.2f", avgHorizontalVelocity * 20))
                        .formatted(Formatting.YELLOW);
            }
            if (isflytoActive && !forceLand) {
                hudString[7] = Text.translatable("text." + modid + ".flyto", argXpos, argZpos)
                        .formatted(Formatting.LIGHT_PURPLE);
                if (distance != 0f) {
                    hudString[8] = Text.translatable("text." + modid + ".hud.eta", String.valueOf(Math.round(distance/(avgHorizontalVelocity * 20))))
                            .formatted(Formatting.LIGHT_PURPLE);
                }
                hudString[9] = Text.translatable("text." + modid + ".hud.autoLand")
                        .formatted(Formatting.LIGHT_PURPLE)
                            .append(Text.translatable(ModConfig.flightprofile.autoLanding ? "text." + modid + ".hud.enabled" : "text." + modid + ".hud.disabled")
                                .formatted(ModConfig.flightprofile.autoLanding ? Formatting.GREEN : Formatting.RED));
                if (isLanding) {
                    hudString[8] = Text.translatable("text." + modid + ".hud.landing")
                            .formatted(Formatting.LIGHT_PURPLE);
                }
            }
            else {
                hudString[7] = Text.of("");
                hudString[8] = Text.of("");
                hudString[9] = Text.of("");
            }
            if (forceLand) {
                hudString[7] = Text.translatable("text." + modid + ".hud.landing")
                        .formatted(Formatting.LIGHT_PURPLE);
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
        PlayerEntity player = minecraftClient.player;
        if (player != null && !(minecraftClient.isPaused() && minecraftClient.isInSingleplayer())) {
            newPosition = player.getPos();
            if (previousPosition == null)
                previousPosition = newPosition;

            Vec3d difference = new Vec3d(newPosition.x - previousPosition.x, newPosition.y - previousPosition.y, newPosition.z - previousPosition.z);
            Vec3d difference_horizontal = new Vec3d(newPosition.x - previousPosition.x, 0, newPosition.z - previousPosition.z);
            previousPosition = newPosition;

            currentVelocity = difference.length();
            currentVelocityHorizontal = difference_horizontal.length();
        }
    }

    private void initCommands()
    {
        if (minecraftClient == null) {
            minecraftClient = MinecraftClient.getInstance();
            ClientCommands.register(main, minecraftClient);
        }
    }

    private void smoothLanding(PlayerEntity player, double speedMod)
    {
        float yaw = MathHelper.wrapDegrees(player.getYaw());
        float pitch = MathHelper.wrapDegrees(player.getPitch());
        float fallPitchMax = 50f;
        float fallPitchMin = 30f;
        float fallPitch;
        if (groundheight > 50){
            fallPitch = fallPitchMax;
        }
        else if (groundheight < 20){
            fallPitch = fallPitchMin;
        }
        else {
            fallPitch = (float) ((groundheight-20)/30)*20 + fallPitchMin;
        }
        pitchMod = 3f;
        player.setYaw((float) (yaw + ModConfig.flightprofile.autoLandSpeed*speedMod));
        player.setPitch((float) (pitch + ModConfig.advanced.pullDownSpeed*pitchMod*speedMod));
        pitch = player.getPitch();
        if (pitch >= fallPitch) {
            player.setPitch(fallPitch);
        }
    }

    private void riskyLanding(PlayerEntity player, double speedMod)
    {
        float pitch = player.getPitch();
        player.setPitch((float) (pitch + ModConfig.flightprofile.takeOffPull*speedMod));
        pitch = player.getPitch();
        if (pitch > 90f) player.setPitch(90f);
    }
	@Override
	public void onInitializeClient() {
        System.out.println("Client ElytraAutoPilot active");
	}
}
