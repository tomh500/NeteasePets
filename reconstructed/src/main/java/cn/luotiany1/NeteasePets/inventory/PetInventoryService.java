package cn.luotiany1.NeteasePets.inventory;

import cn.luotiany1.NeteasePets.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class PetInventoryService implements Listener {
    public static final String TITLE = "狐狸背包";
    private static final int SIZE = 27, OPEN = 9;

    private final JavaPlugin pl;
    private final Storage db;
    private final ExecutorService io;
    private final Map<UUID, Inventory> bag = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Set<UUID> openWhenReady = ConcurrentHashMap.newKeySet();

    public PetInventoryService(JavaPlugin pl, Storage db) {
        this.pl = pl;
        this.db = db;
        this.io = Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r, "NPets-storage");
            t.setDaemon(true);
            return t;
        });
    }

    public void open(Player p) {
        var id = p.getUniqueId();
        var inv = bag.get(id);
        if (inv != null) {
            p.openInventory(inv);
            return;
        }
        p.sendMessage("§7正在读取狐狸背包……");
        load(id, p, true);
    }

    private void load(UUID id, Player p, boolean open) {
        if (open) openWhenReady.add(id);
        if (!loading.add(id)) return;
        io.execute(() -> {
            byte[] raw = null;
            Exception failure = null;
            try {
                raw = db.load(id);
            } catch (Exception e) {
                failure = e;
                pl.getLogger().severe("读取 " + id + " 的背包失败: " + e.getMessage());
            }
            byte[] data = raw;
            Exception error = failure;
            Bukkit.getScheduler().runTask(pl, () -> {
                loading.remove(id);
                if (!p.isOnline()) {
                    openWhenReady.remove(id);
                    return;
                }
                if (error != null) {
                    openWhenReady.remove(id);
                    p.sendMessage("§c狐狸背包读取失败，请稍后重试。数据未被覆盖。");
                    return;
                }
                ItemStack[] a = new ItemStack[0];
                try {
                    if (data != null) {
                        a = InventoryCodec.decode(data);
                    } else {
                        var old = pl.getDataFolder().toPath().resolve("data").resolve(id + ".json");
                        var migrated = LegacyInventoryReader.read(old);
                        if (migrated != null) {
                            a = migrated;
                            pl.getLogger().info("已迁移旧背包数据: " + id);
                        }
                    }
                } catch (Exception e) {
                    pl.getLogger().severe("解析 " + id + " 的背包失败: " + e.getMessage());
                }
                var inv = make(a);
                var old = bag.putIfAbsent(id, inv);
                if (data == null && a.length != 0) save(id, old == null ? inv : old);
                if (openWhenReady.remove(id)) p.openInventory(old == null ? inv : old);
            });
        });
    }

    private Inventory make(ItemStack[] a) {
        var h = new BagHolder();
        var inv = Bukkit.createInventory(h, SIZE, TITLE);
        h.inv = inv;
        for (int i = 0; i < Math.min(OPEN, a.length); ++i) inv.setItem(i, a[i]);
        var wall = new ItemStack(Material.BARRIER);
        for (int i = OPEN; i < SIZE; ++i) inv.setItem(i, wall);
        return inv;
    }

    private void save(UUID id, Inventory inv) {
        var a = new ItemStack[OPEN];
        for (int i = 0; i < OPEN; ++i) a[i] = inv.getItem(i) == null ? null : inv.getItem(i).clone();
        final byte[] raw;
        try {
            raw = InventoryCodec.encode(a);
        } catch (Exception e) {
            pl.getLogger().severe("序列化 " + id + " 的背包失败: " + e.getMessage());
            return;
        }
        io.execute(() -> {
            try {
                db.save(id, raw);
            } catch (Exception e) {
                pl.getLogger().severe("保存 " + id + " 的背包失败: " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        load(e.getPlayer().getUniqueId(), e.getPlayer(), false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (isBag(e.getView().getTopInventory()) && e.getRawSlot() >= OPEN && e.getRawSlot() < SIZE) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!isBag(e.getView().getTopInventory())) return;
        if (e.getRawSlots().stream().anyMatch(i -> i >= OPEN && i < SIZE)) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!isBag(e.getView().getTopInventory())) return;
        save(e.getPlayer().getUniqueId(), e.getInventory());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        var inv = bag.remove(e.getPlayer().getUniqueId());
        if (inv != null) save(e.getPlayer().getUniqueId(), inv);
    }

    public void close() {
        bag.forEach(this::save);
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) pl.getLogger().warning("存储任务未能在 10 秒内全部完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isBag(Inventory inv) {
        return inv.getHolder() instanceof BagHolder;
    }

    private static final class BagHolder implements InventoryHolder {
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }
}
