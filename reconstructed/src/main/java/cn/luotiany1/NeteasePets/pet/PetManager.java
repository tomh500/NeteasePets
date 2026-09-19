package cn.luotiany1.NeteasePets.pet;

import cn.luotiany1.NeteasePets.inventory.PetInventoryService;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public final class PetManager implements Listener, CommandExecutor, TabCompleter {
    private static final List<String> SUB = List.of("menu", "show", "hide", "follow", "stay", "sleep",
            "spin", "happy", "red", "snow", "beacon", "music", "share", "others");
    private static final List<Sound> MUSIC = List.of(
            Sound.MUSIC_DISC_13, Sound.MUSIC_DISC_CAT, Sound.MUSIC_DISC_BLOCKS,
            Sound.MUSIC_DISC_CHIRP, Sound.MUSIC_DISC_FAR, Sound.MUSIC_DISC_MALL,
            Sound.MUSIC_DISC_MELLOHI, Sound.MUSIC_DISC_STAL, Sound.MUSIC_DISC_STRAD,
            Sound.MUSIC_DISC_WARD, Sound.MUSIC_DISC_11, Sound.MUSIC_DISC_WAIT,
            Sound.MUSIC_DISC_OTHERSIDE, Sound.MUSIC_DISC_RELIC, Sound.MUSIC_DISC_5,
            Sound.MUSIC_DISC_PIGSTEP);

    private final JavaPlugin pl;
    private final PetInventoryService bag;
    private final PetMenu menu;
    private final NamespacedKey ownerKey, petKey, shareKey, othersKey;
    private final Map<UUID, Fox> fox = new HashMap<>();
    private final Map<UUID, PetMode> mode = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();
    private final Random rng = new Random();
    private final double near, far, speed;

    public PetManager(JavaPlugin pl, PetInventoryService bag) {
        this.pl = pl;
        this.bag = bag;
        menu = new PetMenu(this);
        ownerKey = new NamespacedKey(pl, "fox_owner");
        petKey = new NamespacedKey(pl, "summoned_by_plugin");
        shareKey = new NamespacedKey(pl, "show_pet_to_others");
        othersKey = new NamespacedKey(pl, "show_other_pets");
        near = Math.max(1.5, pl.getConfig().getDouble("pet.follow-distance", 2.8));
        far = Math.max(near + 2, pl.getConfig().getDouble("pet.teleport-distance", 18));
        speed = Math.max(.12, pl.getConfig().getDouble("pet.max-speed", .42));
    }

    JavaPlugin plugin() { return pl; }

    void openBag(Player p) { bag.open(p); }

    void openMenu(Player p) { menu.open(p); }

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
        if (a.length == 0 || a[0].equalsIgnoreCase("menu")) menu.open(p);
        else if (a[0].equalsIgnoreCase("show")) show(p, true);
        else if (a[0].equalsIgnoreCase("hide")) hide(p);
        else if (a[0].equalsIgnoreCase("follow")) setMode(p, PetMode.FOLLOW);
        else if (a[0].equalsIgnoreCase("stay")) setMode(p, PetMode.STAY);
        else if (a[0].equalsIgnoreCase("sleep")) setMode(p, PetMode.SLEEP);
        else if (a[0].equalsIgnoreCase("spin")) spin(p);
        else if (a[0].equalsIgnoreCase("happy")) happy(p);
        else if (a[0].equalsIgnoreCase("red")) type(p, Fox.Type.RED);
        else if (a[0].equalsIgnoreCase("snow")) type(p, Fox.Type.SNOW);
        else if (a[0].equalsIgnoreCase("beacon")) beacon(p);
        else if (a[0].equalsIgnoreCase("music")) music(p);
        else if (a[0].equalsIgnoreCase("share")) toggleShare(p);
        else if (a[0].equalsIgnoreCase("others")) toggleOthers(p);
        else p.sendMessage("用法: /npets [menu|show|hide|follow|stay|sleep|spin|happy|red|snow|beacon|music|share|others]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length != 1) return List.of();
        String q = a[0].toLowerCase(Locale.ROOT);
        return SUB.stream().filter(x -> x.startsWith(q)).toList();
    }

    void show(Player p, boolean msg) {
        UUID id = p.getUniqueId();
        hidden.remove(id);
        Fox cur = fox.get(id);
        if (ok(cur)) {
            purge(id, cur);
            if (msg) p.sendMessage("§e你的伙伴已经在身边了。§r");
            return;
        }
        fox.remove(id);
        purge(id, null);
        if (!p.isOnline()) return;
        Fox f = p.getWorld().spawn(safeSpot(p), Fox.class);
        f.setCustomName("狐狸");
        f.setCustomNameVisible(true);
        f.setCanPickupItems(false);
        f.setFirstTrustedPlayer(p);
        f.setInvulnerable(true);
        f.setPersistent(true);
        f.setRemoveWhenFarAway(false);
        f.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, id.toString());
        f.getPersistentDataContainer().set(petKey, PersistentDataType.BYTE, (byte)1);
        var at = f.getAttribute(speedAttribute());
        if (at != null) at.setBaseValue(.28);
        fox.put(id, f);
        mode.put(id, PetMode.FOLLOW);
        refreshVisibility(f, p);
        followTask(f, p);
        if (msg) p.sendMessage("§a你的伙伴来了！§r");
    }

    void hide(Player p) {
        UUID id = p.getUniqueId();
        hidden.add(id);
        mode.remove(id);
        Fox f = fox.remove(id);
        if (f != null) f.remove();
        purge(id, null);
        p.sendMessage(f == null ? "§7伙伴本来就不在。§r" : "§e伙伴暂时隐藏了。§r");
    }

    void setMode(Player p, PetMode x) {
        Fox f = get(p);
        if (f == null) return;
        mode.put(p.getUniqueId(), x);
        f.setSleeping(x == PetMode.SLEEP);
        f.setSitting(x == PetMode.STAY || x == PetMode.SLEEP);
        f.setVelocity(new Vector());
        p.sendMessage(switch (x) {
            case FOLLOW -> "§a伙伴会继续跟着你。§r";
            case STAY -> "§e伙伴会留在这里。§r";
            case SLEEP -> "§d伙伴睡着了。§r";
            case SPIN -> "§d伙伴开始转圈。§r";
        });
    }

    void happy(Player p) {
        Fox f = get(p);
        if (f == null) return;
        f.getWorld().spawnParticle(Particle.HEART, f.getLocation().add(0, .8, 0), 7, .35, .3, .35, 0);
        f.getWorld().playSound(f.getLocation(), Sound.ENTITY_FOX_AMBIENT, .8f, 1.25f);
    }

    void spin(Player p) {
        if (get(p) == null) return;
        setMode(p, PetMode.SPIN);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline() && mode.get(p.getUniqueId()) == PetMode.SPIN) setMode(p, PetMode.FOLLOW);
        }, 60L);
    }

    void type(Player p, Fox.Type t) {
        Fox f = get(p);
        if (f == null) return;
        f.setFoxType(t);
        p.sendMessage(t == Fox.Type.SNOW ? "§b伙伴换上了雪地毛色。§r" : "§6伙伴换回了赤狐毛色。§r");
    }

    void beacon(Player p) {
        Fox f = get(p);
        if (f == null) return;
        PetMode old = mode.getOrDefault(p.getUniqueId(), PetMode.FOLLOW);
        mode.put(p.getUniqueId(), PetMode.STAY);
        p.sendMessage("§e伙伴信标将在 10 秒后消失。§r");
        new BukkitRunnable() {
            int t;
            @Override public void run() {
                if (!ok(f) || ++t > 50) {
                    cancel();
                    if (p.isOnline() && mode.get(p.getUniqueId()) == PetMode.STAY) mode.put(p.getUniqueId(), old);
                    return;
                }
                Location a = f.getLocation().add(0, .3, 0);
                for (int y = 0; y < 16; ++y) f.getWorld().spawnParticle(Particle.END_ROD, a.clone().add(0, y, 0), 1, 0, 0, 0, 0);
            }
        }.runTaskTimer(pl, 0L, 4L);
    }

    void music(Player p) {
        p.stopSound(SoundCategory.RECORDS);
        Sound s = MUSIC.get(rng.nextInt(MUSIC.size()));
        p.playSound(p.getLocation(), s, SoundCategory.RECORDS, 1f, 1f);
        p.sendMessage("§e伙伴为你播放了一张随机唱片。再次点击可换一首。§r");
    }

    boolean shares(Player p) { return flag(p, shareKey); }

    boolean seesOthers(Player p) { return flag(p, othersKey); }

    void toggleShare(Player p) {
        setFlag(p, shareKey, !shares(p));
        Fox f = fox.get(p.getUniqueId());
        if (ok(f)) refreshVisibility(f, p);
        p.sendMessage(shares(p) ? "§a其他玩家现在可以看到你的狐狸。§r" : "§e其他玩家现在看不到你的狐狸。§r");
    }

    void toggleOthers(Player p) {
        setFlag(p, othersKey, !seesOthers(p));
        for (var e : fox.entrySet()) {
            if (e.getKey().equals(p.getUniqueId()) || !ok(e.getValue())) continue;
            Player owner = Bukkit.getPlayer(e.getKey());
            if (owner != null) applyVisibility(p, e.getValue(), owner);
        }
        p.sendMessage(seesOthers(p) ? "§a你现在可以看到其他玩家的狐狸。§r" : "§e你现在看不到其他玩家的狐狸。§r");
    }

    private boolean flag(Player p, NamespacedKey key) {
        Byte x = p.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return x == null || x != 0;
    }

    private void setFlag(Player p, NamespacedKey key, boolean x) {
        p.getPersistentDataContainer().set(key, PersistentDataType.BYTE, x ? (byte)1 : (byte)0);
    }

    private void refreshVisibility(Fox f, Player owner) {
        for (Player viewer : Bukkit.getOnlinePlayers()) applyVisibility(viewer, f, owner);
    }

    private void applyVisibility(Player viewer, Fox f, Player owner) {
        boolean visible = viewer.getUniqueId().equals(owner.getUniqueId()) || shares(owner) && seesOthers(viewer);
        if (visible) viewer.showEntity(pl, f);
        else viewer.hideEntity(pl, f);
    }

    private Fox get(Player p) {
        Fox f = fox.get(p.getUniqueId());
        if (ok(f)) return f;
        p.sendMessage("§c伙伴不在，请先召唤。§r");
        return null;
    }

    private void followTask(Fox f, Player p) {
        new BukkitRunnable() {
            @Override public void run() {
                UUID id = p.getUniqueId();
                if (fox.get(id) != f || !ok(f) || !p.isOnline()) { cancel(); return; }
                f.setFireTicks(0);
                f.setCrouching(false);
                PetMode x = mode.getOrDefault(id, PetMode.FOLLOW);
                if (x == PetMode.SPIN) {
                    f.setSleeping(false);
                    f.setSitting(true);
                    f.setRotation(f.getLocation().getYaw() + 28, 0);
                } else if (x != PetMode.FOLLOW) face(f, p.getEyeLocation());
                else move(f, p);
            }
        }.runTaskTimer(pl, 0L, 2L);
    }

    private void move(Fox f, Player p) {
        Location a = f.getLocation(), b = p.getLocation();
        if (a.getWorld() != b.getWorld() || a.distanceSquared(b) > far * far) {
            f.teleport(safeSpot(p));
            return;
        }
        face(f, p.getEyeLocation());
        Vector d = b.toVector().subtract(a.toVector());
        double d2 = d.lengthSquared();
        if (d2 <= near * near) {
            f.setSleeping(false);
            f.setSitting(false);
            f.setVelocity(new Vector(0, f.getVelocity().getY(), 0));
            return;
        }
        f.setSleeping(false);
        f.setSitting(false);
        double len = Math.sqrt(d2), v = Math.min(speed, .16 + len * .025);
        Vector q = new Vector(d.getX() / len * v, f.getVelocity().getY(), d.getZ() / len * v);
        Vector front = q.clone().setY(0).normalize().multiply(.75);
        Location hit = a.clone().add(front);
        if (f.isOnGround() && (!hit.getBlock().isPassable() || !hit.clone().add(0, 1, 0).getBlock().isPassable())) q.setY(.42);
        f.setVelocity(q);
    }

    private static void face(Fox f, Location to) {
        Location a = f.getLocation();
        Vector d = to.toVector().subtract(a.toVector());
        double h = Math.hypot(d.getX(), d.getZ());
        float yaw = (float)Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        float pitch = (float)-Math.toDegrees(Math.atan2(d.getY(), h));
        f.setRotation(yaw, Math.max(-60, Math.min(60, pitch)));
    }

    private static Location safeSpot(Player p) {
        Location c = p.getLocation().clone();
        Vector back = c.getDirection().setY(0);
        if (back.lengthSquared() < 1e-6) back.setZ(1);
        Location q = c.add(back.normalize().multiply(-1.5));
        for (int r = 0; r <= 3; ++r) for (int dx = -r; dx <= r; ++dx) for (int dz = -r; dz <= r; ++dz) {
            Location x = q.clone().add(dx, 0, dz);
            if (x.getBlock().isPassable() && x.clone().add(0, 1, 0).getBlock().isPassable()
                    && !x.clone().add(0, -1, 0).getBlock().isPassable()) return x;
        }
        return p.getLocation();
    }

    private boolean marked(Fox f) {
        var x = f.getPersistentDataContainer();
        return x.has(petKey, PersistentDataType.BYTE) && x.has(ownerKey, PersistentDataType.STRING);
    }

    private UUID owner(Fox f) {
        String s = f.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        try { return s == null ? null : UUID.fromString(s); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static boolean ok(Fox f) { return f != null && f.isValid() && !f.isDead(); }

    private static Attribute speedAttribute() {
        for (String s : new String[]{"GENERIC_MOVEMENT_SPEED", "MOVEMENT_SPEED"}) {
            try { return (Attribute)Attribute.class.getField(s).get(null); }
            catch (ReflectiveOperationException ignored) {}
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

    @EventHandler public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Fox f && marked(f)) e.setCancelled(true);
    }

    @EventHandler public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Fox f) || !marked(f)) return;
        e.setCancelled(true);
        UUID id = owner(f);
        if (id == null) { f.remove(); return; }
        if (!id.equals(e.getPlayer().getUniqueId())) {
            e.getPlayer().sendMessage("§c这不是你的伙伴！§r");
            return;
        }
        menu.open(e.getPlayer());
    }

    @EventHandler public void onLoad(EntitiesLoadEvent e) {
        for (var x : e.getEntities()) {
            if (!(x instanceof Fox f) || !marked(f)) continue;
            UUID id = owner(f);
            Player p = id == null ? null : Bukkit.getPlayer(id);
            Fox cur = id == null ? null : fox.get(id);
            if (p == null || hidden.contains(id)) f.remove();
            else if (!ok(cur)) {
                fox.put(id, f);
                mode.putIfAbsent(id, PetMode.FOLLOW);
                refreshVisibility(f, p);
                followTask(f, p);
                purge(id, f);
            } else if (cur != f) f.remove();
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        purge(p.getUniqueId(), null);
        Bukkit.getScheduler().runTask(pl, () -> {
            for (var x : fox.entrySet()) {
                if (x.getKey().equals(p.getUniqueId()) || !ok(x.getValue())) continue;
                Player owner = Bukkit.getPlayer(x.getKey());
                if (owner != null) applyVisibility(p, x.getValue(), owner);
            }
        });
        Bukkit.getScheduler().runTaskLater(pl, () -> show(p, false), 100L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        hidden.remove(id);
        mode.remove(id);
        Fox f = fox.remove(id);
        if (f != null) f.remove();
        purge(id, null);
    }

    public void close() {
        new HashSet<>(fox.values()).forEach(Fox::remove);
        fox.clear();
        mode.clear();
    }
}
