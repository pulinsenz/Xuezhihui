package com.agent.rag.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象：当前默认本地存储，可替换为 COS 等对象存储实现
 *
 * @author pulinsenz
 */
public interface FileStorageService {

    /**
     * 存储文件，返回文件访问地址
     *
     * @param file   上传的文件
     * @param userId 所属用户
     * @return 存储地址
     */
    String store(MultipartFile file, Long userId);
}
