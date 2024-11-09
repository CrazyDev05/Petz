package de.crazydev22.petz;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class PetZ extends JavaPlugin {
    private final Display display = new Display(this);

    @Override
    public void onLoad() {
        display.load();
    }

    @Override
    public void onEnable() {
        display.enable();

        getServer().getScheduler().runTaskTimer(this, () -> {
            for (var player : Bukkit.getOnlinePlayers()) {
                display.setDisplay(player.getUniqueId(), player.getInventory().getItemInMainHand())
                        .setRotateZ(player.getUniqueId(), player.getInventory().getItemInOffHand().isEmpty());
            }
        },0, 1);
    }

    @Override
    public void onDisable() {
        display.disable();
    }

    @NotNull
    public Display getDisplay() {
        return display;
    }
}
