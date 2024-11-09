/*
Copyright (c) 2024 Julian Krings

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package de.crazydev22.petz;

import de.cubbossa.cliententities.ClientEntities;
import de.cubbossa.cliententities.PlayerSpace;
import de.cubbossa.cliententities.entity.ClientArmorStand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class Display implements Listener {
    private static final Vector OFFSET = new Vector(0, -0.2, -0.75);

    private final ConcurrentHashMap<UUID, Data> pets = new ConcurrentHashMap<>();
    private final ClientEntities api;
    private final PetZ plugin;

    private PlayerSpace space;
    private Timer timer;
    private boolean rotateZ = true;
    private Vector offset = OFFSET.clone();

    Display(PetZ plugin) {
        this.plugin = plugin;
        this.api = new ClientEntities(plugin);
    }

    void load() {
        api.load();
    }

    void enable() {
        api.enable();
        Bukkit.getPluginManager().registerEvents(this, plugin);

        space = PlayerSpace.createGlobal(plugin)
                .build();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (var entry : pets.entrySet()) {
                    var player = Bukkit.getPlayer(entry.getKey());
                    if (player == null)
                        continue;

                    entry.getValue().update(player);
                }

                space.announce();
            }
        }, 0, 10);
    }

    void disable() {
        pets.values().forEach(Data::remove);
        pets.clear();

        timer.cancel();
        try {
            space.close();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close space", e);
        }

        api.disable();
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        space.addPlayer(player);

        var display = (ClientArmorStand) space.spawn(player.getLocation(), ArmorStand.class);
        display.setMarker(true);
        display.setSmall(true);
        display.setInvisible(true);

        pets.put(player.getUniqueId(), new Data(display));
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        space.removePlayer(event.getPlayer());
        var data = pets.remove(event.getPlayer().getUniqueId());
        if (data == null)
            return;
        data.remove();
    }

    /**
     * Set the display for a player.
     * <p>
     * This can be either a helmet or null.
     * <p>
     * The display is automatically set to be invisible and small.
     *
     * @param player the uuid of the player
     * @param display the display to set, or null to remove
     * @return this
     */
    @Contract("_, _ -> this")
    public Display setDisplay(@NotNull UUID player, @Nullable ItemStack display) {
        var data = pets.get(player);
        if (data != null)
            data.setHelmet(display);
        return this;
    }

    /**
     * Returns whether the armor stand will rotate according to the player's view direction.
     *
     * @return the default value
     */
    public boolean isRotateZ() {
        return rotateZ;
    }

    /**
     * Returns whether the armor stand will rotate according to the player's view direction.
     *
     * @param player the player to check
     * @return whether the armor stand will rotate according to the player's view direction
     */
    public boolean isRotateZ(@NotNull UUID player) {
        var data = pets.get(player);
        if (data == null)
            return rotateZ;
        return data.rotateZ;
    }

    /**
     * Set whether the armor stand will rotate according to the player's view direction.
     *
     * @param rotateZ whether the armor stand will rotate according to the player's view direction
     * @return this
     */
    @Contract("_ -> this")
    public Display setRotateZ(boolean rotateZ) {
        pets.values().stream()
                .filter(data -> data.rotateZ == this.rotateZ)
                .forEach(data -> {
                    data.rotateZ = rotateZ;
                    data.location = null;
                });

        this.rotateZ = rotateZ;
        return this;
    }

    /**
     * Set whether the armor stand will rotate according to the player's view direction.
     *
     * @param player the player to set
     * @param rotateZ whether the armor stand will rotate according to the player's view direction
     * @return this
     */
    @Contract("_, _ -> this")
    public Display setRotateZ(@NotNull UUID player, boolean rotateZ) {
        var data = pets.get(player);
        if (data != null) {
            data.rotateZ = rotateZ;
            data.location = null;
        }
        return this;
    }

    /**
     * Returns the offset of the armor stand.
     *
     * @return the offset of the armor stand
     */
    @NotNull
    public Vector getOffset() {
        return offset.clone();
    }

    /**
     * Set the offset of the armor stand.
     *
     * @param offset the offset of the armor stand
     * @return this
     */
    @Contract("_ -> this")
    public Display setOffset(@NotNull Vector offset) {
        pets.values().stream()
                .filter(data -> data.offset.equals(this.offset))
                .forEach(data -> {
                    data.offset = offset.clone();
                    data.location = null;
                });

        this.offset = offset.clone();
        return this;
    }

    /**
     * Sets the offset of the armor stand for the given player.
     *
     * @param player the uuid of the player
     * @param offset the offset of the armor stand
     * @return this
     */
    @Contract("_, _ -> this")
    public Display setOffset(@NotNull UUID player, @NotNull Vector offset) {
        var data = pets.get(player);
        if (data != null) {
            data.offset = offset.clone();
            data.location = null;
        }
        return this;
    }

    /**
     * Gets the offset of the armor stand for the given player.
     *
     * @param player the uuid of the player
     * @return the offset of the armor stand, or null if the player is not in the pet map
     */
    @Nullable
    public Vector getOffset(@NotNull UUID player) {
        var data = pets.get(player);
        if (data == null)
            return null;
        return data.offset.clone();
    }

    private class Data {
        private final ClientArmorStand entity;
        private Vector offset = getOffset();
        private boolean changedHelmet, rotateZ;
        private Location location = null;
        private ItemStack helmet;

        private Data(ClientArmorStand entity) {
            this.entity = entity;
            this.rotateZ = isRotateZ();
        }

        private void setHelmet(ItemStack helmet) {
            if (Objects.equals(helmet, this.helmet))
                return;
            this.helmet = helmet;
            changedHelmet = true;
        }

        private void update(Player player) {
            if (entity.isDead())
                return;

            var location = player.getEyeLocation();
            if (!location.equals(this.location)) {
                this.location = location.clone();
                location.add(offset.clone().rotateAroundY(Math.toRadians(player.getBodyYaw())));
                entity.setHeadPose(new EulerAngle(rotateZ ? location.getPitch() : 0, 0, 0));
                entity.teleport(location);
            }

            if (changedHelmet) {
                entity.setHelmet(helmet);
                changedHelmet = false;
            }
        }

        private void remove() {
            entity.remove();
        }
    }
}
