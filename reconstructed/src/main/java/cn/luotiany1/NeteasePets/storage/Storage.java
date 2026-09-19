package cn.luotiany1.NeteasePets.storage;

import java.util.UUID;

public interface Storage extends AutoCloseable {
    void init() throws Exception;
    byte[] load(UUID id) throws Exception;
    void save(UUID id, byte[] data) throws Exception;
    String name();

    @Override
    default void close() throws Exception {}
}
