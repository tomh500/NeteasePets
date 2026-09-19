package cn.luotiany1.NeteasePets.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class FileStorage implements Storage {
    private final Path dir;

    public FileStorage(Path dir) {
        this.dir = dir;
    }

    @Override
    public void init() throws IOException {
        Files.createDirectories(dir);
    }

    @Override
    public byte[] load(UUID id) throws IOException {
        var p = file(id);
        return Files.exists(p) ? Files.readAllBytes(p) : null;
    }

    @Override
    public void save(UUID id, byte[] data) throws IOException {
        var dst = file(id);
        var tmp = dir.resolve(id + ".tmp");
        Files.write(tmp, data);
        try {
            Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path file(UUID id) {
        return dir.resolve(id + ".bin");
    }

    @Override
    public String name() {
        return "file";
    }
}
