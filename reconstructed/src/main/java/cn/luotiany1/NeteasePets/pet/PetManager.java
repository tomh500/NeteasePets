package cn.luotiany1.NeteasePets.pet;

import cn.luotiany1.NeteasePets.inventory.PetInventoryService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PetManager implements Listener, CommandExecutor {
    private static final double FAR2 = 81.0;

    private final JavaPlugin pl;
    private final PetInventoryService bag;
    private final NamespacedKey ownerKey, petKey;
    private final Map<UUID, Fox> fox = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();

    public PetManager(JavaPlugin pl, PetInventoryService bag) {
        this.pl = pl;
        this.bag = bag;
        ownerKey = new NamespacedKey(pl, "fox_owner");
        petKey = new NamespacedKey(pl, "summoned_by_plugin");
    }

    public void start() {
        Bukkit.getScheduler().runTask(pl, () -> {
            purgeOrphans();
            Bukkit.getOnlinePlayers().forEach(p -> show(p, false));
        });
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("此命令仅限玩家使用。");
            return true;
        }
        if (a.length != 1) {
            p.sendMessage("用法: /npets show 或 /npets hide");
            return true;
        }
        if (a[0].equalsIgnoreCase("show")) show(p, true);
        else if (a[0].equalsIgnoreCase("hide")) hide(p);
        else p.sendMessage("用法: /npets show 或 /npets hide");
        return true;
    }

    private void show(Player p, boolean msg) {
        UUID id = p.getUniqueId();
        hidden.remove(id);
        Fox cur = fox.get(id);
        if (ok(cur)) {
            purge(id, cur);
            if (msg) p.sendMessage("你已经召唤了一只狐狸！");
            return;
        }
        fox.remove(id);
        purge(id, null);
        if (!p.isOnline()) return;

        Location at = p.getLocation();
        Fox f = at.getWorld().spawn(at, Fox.class);
        f.setCustomName("狐狸");
        f.setCustomNameVisible(true);
        f.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, id.toString());
        f.getPersistentDataContainer().set(petKey, PersistentDataType.BYTE, (byte)1);
        var speed = f.getAttribute(speedAttribute());
        if (speed != null) speed.setBaseValue(.15);
        fox.put(id, f);
        follow(f, p);
        if (msg) p.sendMessage("你的狐狸已召唤！");
    }

    private void hide(Player p) {
        UUID id = p.getUniqueId();
        hidden.add(id);
        Fox f = fox.remove(id);
        if (f != null) f.remove();
        purge(id, null);
        p.sendMessage(f == null ? "你还没有召唤狐狸！" : "你的狐狸已隐藏！");
    }

    private void follow(Fox f, Player p) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (fox.get(p.getUniqueId()) != f || !ok(f) || !p.isOnline()) {
                    cancel();
                    return;
                }
                if (f.getWorld() != p.getWorld() || f.getLocation().distanceSquared(p.getLocation()) > FAR2) {
                    f.teleport(p.getLocation().add(1, 0, 1));
                }
                var at = p.getLocation();
                f.setRotation(at.getYaw(), at.getPitch());
            }
        }.runTaskTimer(pl, 0L, 20L);
    }

    private boolean marked(Fox f) {
        var pdc = f.getPersistentDataContainer();
        return pdc.has(petKey, PersistentDataType.BYTE) && pdc.has(ownerKey, PersistentDataType.STRING);
    }

    private UUID owner(Fox f) {
        String s = f.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean ok(Fox f) {
        return f != null && f.isValid() && !f.isDead();
    }

    private static Attribute speedAttribute() {
        for (String s : new String[]{"GENERIC_MOVEMENT_SPEED", "MOVEMENT_SPEED"}) {
            try {
                return (Attribute)Attribute.class.getField(s).get(null);
            } catch (ReflectiveOperationException ignored) {}
        }
        throw new IllegalStateException("服务端没有移动速度属性");
    }

    private void purge(UUID id, Fox keep) {
        Bukkit.getWorlds().forEach(w -> w.getEntitiesByClass(Fox.class).forEach(f -> {
            if (f != keep && marked(f) && id.equals(owner(f))) f.remove();
        }));
    }

    private void purgeOrphans() {
        Set<Fox> live = new HashSet<>(fox.values());
        Bukkit.getWorlds().forEach(w -> w.getEntitiesByClass(Fox.class).forEach(f -> {
            if (marked(f) && !live.contains(f)) f.remove();
        }));
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Fox f && marked(f)) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Fox f) || !marked(f)) return;
        UUID id = owner(f);
        if (id == null) {
            f.remove();
            return;
        }
        if (!id.equals(e.getPlayer().getUniqueId())) {
            e.getPlayer().sendMessage("这不是你的狐狸！");
            return;
        }
        e.setCancelled(true);
        bag.open(e.getPlayer());
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        for (var x : e.getEntities()) {
            if (!(x instanceof Fox f) || !marked(f)) continue;
            UUID id = owner(f);
            Player p = id == null ? null : Bukkit.getPlayer(id);
            Fox cur = id == null ? null : fox.get(id);
            if (p == null || hidden.contains(id)) f.remove();
            else if (!ok(cur)) {
                fox.put(id, f);
                follow(f, p);
                purge(id, f);
            } else if (cur != f) f.remove();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        purge(p.getUniqueId(), null);
        Bukkit.getScheduler().runTaskLater(pl, () -> show(p, false), 100L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        hidden.remove(id);
        Fox f = fox.remove(id);
        if (f != null) f.remove();
    }

    public void close() {
        new HashSet<>(fox.values()).forEach(Fox::remove);
        fox.clear();
    }
}
