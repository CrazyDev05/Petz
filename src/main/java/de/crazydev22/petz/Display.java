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

    @Contract("_, _ -> this")
    public Display setDisplay(@NotNull UUID player, @Nullable ItemStack display) {
        var data = pets.get(player);
        if (data != null)
            data.setHelmet(display);
        return this;
    }

    public boolean isRotateZ() {
        return rotateZ;
    }

    public boolean isRotateZ(@NotNull UUID player) {
        var data = pets.get(player);
        if (data == null)
            return rotateZ;
        return data.rotateZ;
    }

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

    @Contract("_, _ -> this")
    public Display setRotateZ(@NotNull UUID player, boolean rotateZ) {
        var data = pets.get(player);
        if (data != null) {
            data.rotateZ = rotateZ;
            data.location = null;
        }
        return this;
    }

    @NotNull
    public Vector getOffset() {
        return offset.clone();
    }

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

    @Contract("_, _ -> this")
    public Display setOffset(@NotNull UUID player, @NotNull Vector offset) {
        var data = pets.get(player);
        if (data != null) {
            data.offset = offset.clone();
            data.location = null;
        }
        return this;
    }

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