package net.elytraautopilot;

import com.terraformersmc.modmenu.ModMenu;
import me.lortseam.completeconfig.gui.ConfigScreenBuilder;
import me.lortseam.completeconfig.gui.cloth.ClothConfigScreenBuilder;
import net.elytraautopilot.commands.ClientCommands;
import net.elytraautopilot.config.ModConfig;
import net.elytraautopilot.utils.Hud;
import net.elytraautopilot.utils.Util;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
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

public class ElytraAutoPilot implements ClientModInitializer {
    private static final String modid = "elytraautopilot";
    private static boolean lastPressed = false;
    public static MinecraftClient minecraftClient;
    public static boolean calculateHud;
    public static boolean autoFlight;
    private static boolean startCooldown;
    private static int cooldown = 0;
    private static boolean onTakeoff;
    public static double pitchMod = 1f;

    public static Vec3d previousPosition;
    public static double currentVelocity;
    public static double currentVelocityHorizontal;

    public static boolean isDescending;
    public static boolean pullUp;
    public static boolean pullDown;

    private static double velHigh = 0f;
    private static double velLow = 0f;

    public static int argXpos;
    public static int argZpos;
    public static boolean isChained = false;
    public static boolean isflytoActive = false;
    public static boolean forceLand = false;
    public static boolean isLanding = false;
    public static double distance = 0f;
    public static double groundheight;

	@Override
	public void onInitializeClient() {
        minecraftClient = MinecraftClient.getInstance();

        Util.init();
        HudRenderCallback.EVENT.register((matrixStack, tickDelta) -> ElytraAutoPilot.this.onScreenTick());
        ClientTickEvents.END_CLIENT_TICK.register(e -> this.onClientTick());

        ModConfig config = new ModConfig(modid);
        config.load();
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
            ConfigScreenBuilder.setMain(modid, new ClothConfigScreenBuilder());
        }
        ClientCommands.register(minecraftClient);
    }

    public static String getModId() {
        return modid;
    }
	public static void takeoff()
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
        if (!(minecraftClient.isPaused() && minecraftClient.isInSingleplayer())) Hud.tick();
        double velMod;

        PlayerEntity player = minecraftClient.player;

        if (player == null){
            autoFlight = false;
            onTakeoff = false;
            return;
        }

        if (player.isFallFlying())
            calculateHud = true;
        else {
            calculateHud = false;
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
        if(!lastPressed && Util.keyBinding.isPressed()) {
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
        lastPressed = Util.keyBinding.isPressed();

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
        if (calculateHud) {
            computeVelocity();
            Hud.drawHud(player);
        }
        else {
            previousPosition = null;
            Hud.clearHud();
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
}
