package cn.luotiany1.NeteasePets.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class StorageFactory {
    private StorageFactory() {}

    public static Storage create(JavaPlugin pl) {
        String x = pl.getConfig().getString("storage", "file").toLowerCase(Locale.ROOT);
        return switch (x) {
            case "file", "yml", "local" -> new FileStorage(pl.getDataFolder().toPath().resolve("data"));
            case "mysql", "db" -> {
                var c = pl.getConfig().getConfigurationSection("mysql");
                if (c == null) throw new IllegalArgumentException("storage=mysql 时必须提供 mysql 配置段");
                yield new MySqlStorage(c);
            }
            default -> throw new IllegalArgumentException("未知 storage: " + x + "（可用: file, mysql）");
        };
    }
}
