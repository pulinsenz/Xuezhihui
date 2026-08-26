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

    /**
     * 存储文件，返回可被前端访问的 URL（静态资源经 /api/files 暴露）
     *
     * @param file   上传的文件
     * @param userId 所属用户
     * @return 如 /api/files/202608/{userId}/{uuid}.png
     */
    String storeForWeb(MultipartFile file, Long userId);

    /**
     * 存储头像图片，返回可被前端 {@code <img>} 直接加载的 URL
     * <p>
     * 与 storeForWeb 的区别仅是目录前缀（avatar/ vs cover/），头像与知识库封面互不混用。
     *
     * @param file   上传的图片
     * @param userId 所属用户
     * @return COS 直链或 /api/files/avatar/... 静态地址
     */
    String storeAvatar(MultipartFile file, Long userId);
}
