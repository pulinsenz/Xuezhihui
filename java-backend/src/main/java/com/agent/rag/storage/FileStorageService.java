package com.agent.rag.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

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

    /**
     * 打开文件内容流（供下载接口流式返回）
     * <p>
     * 调用方负责关闭返回的流；COS URL 由实现从对象存储拉取（服务端代理），本地路径直接读文件。
     *
     * @param fileUrl 本地绝对路径或对象存储 URL
     * @return 文件内容流
     * @throws IOException 打开失败（本地文件不存在等）
     */
    InputStream open(String fileUrl) throws IOException;

    /**
     * 生成可访问地址：私有对象返回临时签名 URL（供 Python worker 拉取文件），本地路径原样返回
     *
     * @param fileUrl       本地绝对路径或对象存储 URL
     * @param expireSeconds 签名有效期（秒），仅对象存储实现生效
     * @return 可访问地址
     */
    String presignedUrl(String fileUrl, int expireSeconds);
}
