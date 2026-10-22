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
 * MinIO 存储（预留实现）：配置 wuzi.storage.type=minio 时启用。
 * 未引入 minio SDK 依赖时降级为本地磁盘并记日志提示，不阻断附件上传下载。
 * 引入依赖后可在 store/open 中接入 io.minio.MinioClient。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "wuzi.storage.type", havingValue = "minio")
public class MinioStorageService implements StorageService {

    private static final String MINIO_CLIENT = "io.minio.MinioClient";

    @Value("${wuzi.storage.minio.endpoint:}")
    private String endpoint;
    @Value("${wuzi.storage.minio.access-key:}")
    private String accessKey;
    @Value("${wuzi.storage.minio.secret-key:}")
    private String secretKey;
    @Value("${wuzi.storage.minio.bucket:wuzi}")
    private String bucket;
    /** MinIO 不可用时的本地降级目录 */
    @Value("${wuzi.attachment.dir:${user.dir}/data/attachments}")
    private String localBaseDir;

    private volatile Boolean minioAvailable;
    private volatile boolean degradedLogged;

    @Override
    public String type() {
        return "minio";
    }

    @Override
    public String save(String key, InputStream in, String contentType) throws Exception {
        if (!minioReady()) {
            return localSave(key, in);
        }
        // 预留：接入 MinioClient.putObject(bucket, key, in, ...)
        log.info("[storage:minio] putObject bucket={} key={} (占位，当前降级本地)", bucket, key);
        return localSave(key, in);
    }

    @Override
    public InputStream open(String storedPath) throws Exception {
        if (!minioReady()) {
            return Files.newInputStream(Paths.get(storedPath));
        }
        // 预留：MinioClient.getObject
        return Files.newInputStream(Paths.get(storedPath));
    }

    @Override
    public void delete(String storedPath) throws Exception {
        if (!minioReady()) {
            Files.deleteIfExists(Paths.get(storedPath));
            return;
        }
        // 预留：MinioClient.removeObject
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

    /** MinIO SDK 是否可用：未装依赖则降级本地并提示一次 */
    private boolean minioReady() {
        if (minioAvailable == null) {
            synchronized (this) {
                if (minioAvailable == null) {
                    boolean ok;
                    try {
                        Class.forName(MINIO_CLIENT);
                        ok = endpoint != null && !endpoint.isBlank()
                                && accessKey != null && !accessKey.isBlank();
                    } catch (ClassNotFoundException e) {
                        ok = false;
                    }
                    minioAvailable = ok;
                    if (!ok && !degradedLogged) {
                        degradedLogged = true;
                        log.warn("[storage] MinIO 不可用（未引入依赖或未配置 endpoint/access-key），已降级为本地存储 dir={}",
                                localBaseDir);
                    } else if (ok) {
                        log.info("[storage] MinIO 已启用 endpoint={} bucket={}", endpoint, bucket);
                    }
                }
            }
        }
        return minioAvailable;
    }

    private String localSave(String key, InputStream in) throws Exception {
        Path base = Paths.get(localBaseDir).toAbsolutePath().normalize();
        Path target = base.resolve(key).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("非法存储键: " + key);
        }
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        return target.toAbsolutePath().toString();
    }
}
