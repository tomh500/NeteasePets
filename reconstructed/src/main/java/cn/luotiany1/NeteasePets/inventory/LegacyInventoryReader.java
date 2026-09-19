package cn.luotiany1.NeteasePets.inventory;

import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;

final class LegacyInventoryReader {
    private LegacyInventoryReader() {}

    static ItemStack[] read(Path p) throws Exception {
        if (!Files.exists(p)) return null;
        var root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
        var src = root.getAsJsonArray("inventory");
        var ans = new ItemStack[Math.min(9, src.size())];
        for (int i = 0; i < ans.length; ++i) {
            var y = new YamlConfiguration();
            y.loadFromString(src.get(i).getAsJsonObject().get("yaml").getAsString());
            ans[i] = y.getItemStack("item");
        }
        return ans;
    }
}
