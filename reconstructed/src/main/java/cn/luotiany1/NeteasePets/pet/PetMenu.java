package cn.luotiany1.NeteasePets.pet;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

final class PetMenu implements Listener {
    private static final String TITLE = "我的伙伴";
    private final PetManager pet;

    PetMenu(PetManager pet) {
        this.pet = pet;
        Bukkit.getPluginManager().registerEvents(this, pet.plugin());
    }

    void open(Player p) {
        var h = new Holder();
        var inv = Bukkit.createInventory(h, 27, TITLE);
        h.inv = inv;
        inv.setItem(0, item(Material.FOX_SPAWN_EGG, "§a召唤伙伴", "让伙伴出现在你身边"));
        inv.setItem(1, item(Material.BARRIER, "§c隐藏伙伴", "暂时收起伙伴"));
        inv.setItem(3, item(Material.LEAD, "§a跟随", "平滑跟随并面朝你"));
        inv.setItem(4, item(Material.OAK_FENCE, "§e原地等待", "停在当前位置并看着你"));
        inv.setItem(5, item(Material.RED_BED, "§d睡觉", "让伙伴趴下睡觉"));
        inv.setItem(7, item(Material.CHEST, "§6伙伴背包", "打开九格随身背包"));
        inv.setItem(11, item(Material.POPPY, "§d高兴", "爱心和狐狸叫声"));
        inv.setItem(12, item(Material.CLOCK, "§b转圈", "开心地转三秒"));
        inv.setItem(14, item(Material.SWEET_BERRIES, "§6赤狐外观", "切换为赤狐"));
        inv.setItem(15, item(Material.SNOWBALL, "§f雪狐外观", "切换为雪狐"));
        inv.setItem(20, item(Material.BEACON, "§e临时信标", "在伙伴位置显示十秒光柱"));
        inv.setItem(24, item(Material.MUSIC_DISC_CAT, "§b随机音乐", "随机播放一张唱片"));
        inv.setItem(21, item(pet.shares(p) ? Material.ENDER_EYE : Material.ENDER_PEARL,
                pet.shares(p) ? "§a别人可见我的狐狸" : "§c别人不可见我的狐狸", "点击切换其他玩家能否看到它"));
        inv.setItem(23, item(pet.seesOthers(p) ? Material.SPYGLASS : Material.CARVED_PUMPKIN,
                pet.seesOthers(p) ? "§a显示他人的狐狸" : "§c隐藏他人的狐狸", "点击切换你能否看到其他狐狸"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        switch (e.getRawSlot()) {
            case 0 -> pet.show(p, true);
            case 1 -> pet.hide(p);
            case 3 -> pet.setMode(p, PetMode.FOLLOW);
            case 4 -> pet.setMode(p, PetMode.STAY);
            case 5 -> pet.setMode(p, PetMode.SLEEP);
            case 7 -> pet.openBag(p);
            case 11 -> pet.happy(p);
            case 12 -> pet.spin(p);
            case 14 -> pet.type(p, Fox.Type.RED);
            case 15 -> pet.type(p, Fox.Type.SNOW);
            case 20 -> pet.beacon(p);
            case 21 -> pet.toggleShare(p);
            case 23 -> pet.toggleOthers(p);
            case 24 -> pet.music(p);
            default -> { return; }
        }
        if (e.getRawSlot() == 21 || e.getRawSlot() == 23) open(p);
        else if (e.getRawSlot() != 7) p.closeInventory();
    }

    private static ItemStack item(Material type, String name, String lore) {
        var x = new ItemStack(type);
        ItemMeta m = x.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            m.setLore(List.of("§7" + lore));
            x.setItemMeta(m);
        }
        return x;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }
}
