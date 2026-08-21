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
import java.io.IOException;
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
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        String originalName = StrUtil.nullToEmpty(file.getOriginalFilename());
        String ext = FileUtil.extName(originalName);
        String datePath = DateUtil.format(new Date(), "yyyyMM");
        String fileName = IdUtil.fastSimpleUUID() + (StrUtil.isBlank(ext) ? "" : "." + ext);
        String relativePath = StrUtil.format("{}/{}/{}", datePath, userId, fileName);
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
        return target.getAbsolutePath();
    }
}
