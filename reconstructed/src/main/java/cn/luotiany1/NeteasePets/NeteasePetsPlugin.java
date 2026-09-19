package cn.luotiany1.NeteasePets;

import cn.luotiany1.NeteasePets.inventory.PetInventoryService;
import cn.luotiany1.NeteasePets.pet.PetManager;
import cn.luotiany1.NeteasePets.storage.Storage;
import cn.luotiany1.NeteasePets.storage.StorageFactory;
import org.bukkit.plugin.java.JavaPlugin;

public final class NeteasePetsPlugin extends JavaPlugin {
    private Storage db;
    private PetInventoryService bag;
    private PetManager pet;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            db = StorageFactory.create(this);
            db.init();
        } catch (Exception e) {
            getLogger().severe("存储初始化失败，插件已停用: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bag = new PetInventoryService(this, db);
        pet = new PetManager(this, bag);
        getServer().getPluginManager().registerEvents(bag, this);
        getServer().getPluginManager().registerEvents(pet, this);
        getCommand("npets").setExecutor(pet);
        getCommand("npets").setTabCompleter(pet);
        pet.start();
        getLogger().info("存储后端: " + db.name());
    }

    @Override
    public void onDisable() {
        if (bag != null) bag.close();
        if (pet != null) pet.close();
        if (db != null) {
            try {
                db.close();
            } catch (Exception e) {
                getLogger().warning("关闭存储失败: " + e.getMessage());
            }
        }
    }
}
