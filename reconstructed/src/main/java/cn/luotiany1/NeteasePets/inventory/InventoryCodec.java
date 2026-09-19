package cn.luotiany1.NeteasePets.inventory;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class InventoryCodec {
    private static final int MAGIC = 0x4e504554;
    private static final int VERSION = 1;

    private InventoryCodec() {}

    public static byte[] encode(ItemStack[] a) throws IOException {
        var buf = new ByteArrayOutputStream();
        try (var out = new BukkitObjectOutputStream(buf)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(a.length);
            for (var x : a) out.writeObject(x);
        }
        return buf.toByteArray();
    }

    public static ItemStack[] decode(byte[] a) throws IOException {
        try (var in = new BukkitObjectInputStream(new ByteArrayInputStream(a))) {
            if (in.readInt() != MAGIC) throw new IOException("背包数据标识无效");
            int ver = in.readInt(), n = in.readInt();
            if (ver != VERSION) throw new IOException("不支持的背包数据版本: " + ver);
            if (n < 0 || n > 256) throw new IOException("背包槽位数量无效: " + n);
            var ans = new ItemStack[n];
            for (int i = 0; i < n; ++i) {
                Object x;
                try {
                    x = in.readObject();
                } catch (ClassNotFoundException e) {
                    throw new IOException("物品类型无法读取", e);
                }
                if (x != null && !(x instanceof ItemStack)) throw new IOException("槽位 " + i + " 不是物品");
                ans[i] = (ItemStack)x;
            }
            return ans;
        }
    }
}
