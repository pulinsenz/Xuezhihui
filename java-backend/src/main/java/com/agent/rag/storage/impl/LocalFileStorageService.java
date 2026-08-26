package com.agent.rag.storage.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.storage.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Date;

/**
 * 本地文件存储实现（开发/测试环境）
 * <p>
 * 目录结构：{local-path}/{yyyyMM}/{userId}/{uuid}.{ext}
 *
 * @author pulinsenz
 */
@Slf4j
@Component
public class LocalFileStorageService implements FileStorageService {

    @Value("${app.file.storage.local-path:./data/files}")
    private String localPath;

    @Override
    public String store(MultipartFile file, Long userId) {
        String relativePath = storeInternal(file, userId, "");
        // 必须用绝对路径：transferTo 对相对路径会按容器临时目录解析，导致 FileNotFoundException
        File target = new File(Paths.get(localPath).toAbsolutePath().normalize().toFile(), relativePath);
        return target.getAbsolutePath();
    }

    @Override
    public String storeForWeb(MultipartFile file, Long userId) {
        return storeWeb(file, userId, "cover");
    }

    @Override
    public String storeAvatar(MultipartFile file, Long userId) {
        return storeWeb(file, userId, "avatar");
    }

    @Override
    public InputStream open(String fileUrl) throws IOException {
        // 本地实现不处理远程 URL（COS 地址由 CosFileStorageService 代理），防止误用
        if (StrUtil.startWithIgnoreCase(fileUrl, "http://")
                || StrUtil.startWithIgnoreCase(fileUrl, "https://")) {
            throw new IllegalArgumentException("本地存储不处理远程 URL: " + fileUrl);
        }
        File file = new File(fileUrl);
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("文件不存在或已被清理: " + fileUrl);
        }
        return new FileInputStream(file);
    }

    @Override
    public String presignedUrl(String fileUrl, int expireSeconds) {
        // 本地路径无需签名：python worker 经共享卷按绝对路径直接读取
        return fileUrl;
    }

    /**
     * 公网图片落盘并返回 /api/files 地址：封面/头像存入各自独立子目录，
     * 静态资源只暴露这些子目录，避免用户上传的文档文件被公开下载
     */
    private String storeWeb(MultipartFile file, Long userId, String subDir) {
        String relativePath = storeInternal(file, userId, subDir);
        String webPath = relativePath.substring((subDir + "/").length());
        return "/api/files/" + webPath;
    }

    /**
     * 落盘并返回相对路径 {subDir}/{yyyyMM}/{userId}/{uuid}.{ext}
     */
    private String storeInternal(MultipartFile file, Long userId, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        String originalName = StrUtil.nullToEmpty(file.getOriginalFilename());
        String ext = FileUtil.extName(originalName);
        String datePath = DateUtil.format(new Date(), "yyyyMM");
        String fileName = IdUtil.fastSimpleUUID() + (StrUtil.isBlank(ext) ? "" : "." + ext);
        String relativePath = StrUtil.isBlank(subDir)
                ? StrUtil.format("{}/{}/{}", datePath, userId, fileName)
                : StrUtil.format("{}/{}/{}/{}", subDir, datePath, userId, fileName);
        // 必须用绝对路径：transferTo 对相对路径会按容器临时目录解析，导致 FileNotFoundException
        File base = Paths.get(localPath).toAbsolutePath().normalize().toFile();
        File target = new File(base, relativePath);
        FileUtil.mkParentDirs(target);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            log.error("文件存储失败: {}", originalName, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件存储失败");
        }
        log.info("文件存储成功: path={}", target.getAbsolutePath());
        return relativePath;
    }
}
