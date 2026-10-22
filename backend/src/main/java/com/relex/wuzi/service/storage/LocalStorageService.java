package com.relex.wuzi.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘存储（默认实现）。
 * 目录 {user.dir}/data/attachments，可用 wuzi.attachment.dir 覆盖。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "wuzi.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    @Value("${wuzi.attachment.dir:${user.dir}/data/attachments}")
    private String baseDir;

    @Override
    public String type() {
        return "local";
    }

    @Override
    public String save(String key, InputStream in, String contentType) throws Exception {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        return target.toAbsolutePath().toString();
    }

    @Override
    public InputStream open(String storedPath) throws Exception {
        return Files.newInputStream(Paths.get(storedPath));
    }

    @Override
    public void delete(String storedPath) throws Exception {
        Files.deleteIfExists(Paths.get(storedPath));
    }

    @Override
    public boolean exists(String storedPath) {
        return storedPath != null && Files.exists(Paths.get(storedPath));
    }

    @Override
    public long size(String storedPath) {
        try {
            return Files.size(Paths.get(storedPath));
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public void copyTo(String storedPath, OutputStream out) throws Exception {
        Files.copy(Paths.get(storedPath), out);
        out.flush();
    }

    private Path resolve(String key) {
        Path base = Paths.get(baseDir).toAbsolutePath().normalize();
        Path target = base.resolve(key).normalize();
        // 防目录穿越
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("非法存储键: " + key);
        }
        return target;
    }
}
