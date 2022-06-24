package net.elytraautopilot.utils;

import net.elytraautopilot.ElytraAutoPilot;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class Util {
    public static KeyBinding keyBinding;
    public static void init() {
        String key;
        String modid = ElytraAutoPilot.getModId();
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
    }
}
