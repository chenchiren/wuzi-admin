package com.relex.wuzi.service.storage;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 附件存储抽象：本地磁盘 / MinIO 等实现。
 * 实现方约定：路径键 key 形如 yyyyMMdd/uuid_filename，不暴露物理路径细节。
 */
public interface StorageService {

    /** 存储类型标识：local / minio */
    String type();

    /**
     * 保存输入流到存储。
     *
     * @param key         存储键（相对路径）
     * @param in          文件流
     * @param contentType MIME（可空）
     * @return 实际存储路径/URI（用于落库 file_path）
     */
    String save(String key, InputStream in, String contentType) throws Exception;

    /** 打开读取流 */
    InputStream open(String storedPath) throws Exception;

    /** 删除文件 */
    void delete(String storedPath) throws Exception;

    /** 文件是否存在 */
    boolean exists(String storedPath);

    /** 文件大小（字节），不存在返回 -1 */
    long size(String storedPath);

    /** 写出到响应流 */
    void copyTo(String storedPath, OutputStream out) throws Exception;
}
