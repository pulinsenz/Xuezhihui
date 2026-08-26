package com.agent.rag.storage.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.config.CosProperties;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.storage.FileStorageService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.CannedAccessControlList;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * 腾讯云 COS 文件存储实现（封面、头像等公网图片）
 * <p>
 * store() 委托本地存储（文档仍走共享卷，python worker 按绝对路径读取）；
 * storeForWeb()（封面）/ storeAvatar()（头像）上传 COS：桶为私有时给对象单独加 public-read，
 * 前端 {@code <img src>} 免鉴权即可直接加载。
 *
 * @author pulinsenz
 */
@Slf4j
@Primary
@Component
public class CosFileStorageService implements FileStorageService {

    @Resource
    private CosProperties cosProperties;

    @Resource
    private LocalFileStorageService localFileStorageService;

    /**
     * 测试可用 @MockBean 注入替身；生产为空则按配置懒构建（启动时不因未配置 COS 而失败）
     */
    @Autowired(required = false)
    private COSClient cosClient;

    @Override
    public String store(MultipartFile file, Long userId) {
        // 文档同样上传 COS（私有对象，不设 ACL）：与机器无关，下载/向量化走签名+服务端代理；
        // COS 未配置时回退本地共享卷，保证纯本地开发/离线可用
        if (!cosConfigured()) {
            return localFileStorageService.store(file, userId);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        String host = normalizeHost(cosProperties.getHost());
        String originalName = StrUtil.nullToEmpty(file.getOriginalFilename());
        String ext = FileUtil.extName(originalName);
        String datePath = DateUtil.format(new Date(), "yyyyMM");
        String key = StrUtil.format("doc/{}/{}/{}", datePath, userId,
                IdUtil.fastSimpleUUID() + (StrUtil.isBlank(ext) ? "" : "." + ext));
        try (InputStream in = file.getInputStream()) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(file.getSize());
            meta.setContentType(StrUtil.blankToDefault(file.getContentType(), "application/octet-stream"));
            // 文档为私有对象：不设 ACL（默认私有），仅签名 URL / 服务端代理可读
            client().putObject(new PutObjectRequest(cosProperties.getBucket(), key, in, meta));
        } catch (IOException e) {
            log.error("文档上传 COS 失败: file={}", originalName, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件上传失败");
        } catch (CosClientException e) {
            // 网络/签名/服务端错误不吞成"系统内部异常"
            String cosError = (e instanceof CosServiceException svc) ? svc.getErrorCode() : e.getMessage();
            log.error("文档上传 COS 异常: key={}, cosError={}", key, cosError, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件上传失败");
        }
        String url = host + "/" + key;
        log.info("文档已上传 COS: key={}, url={}", key, url);
        return url;
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
        if (!isCosUrl(fileUrl)) {
            // 本地路径：委托本地实现读共享卷
            return localFileStorageService.open(fileUrl);
        }
        String key = parseKey(fileUrl);
        try {
            COSObject object = client().getObject(cosProperties.getBucket(), key);
            return object.getObjectContent();
        } catch (CosClientException e) {
            // 私有对象读取失败（如无权限/对象不存在）不吞成"系统内部异常"
            String cosError = (e instanceof CosServiceException svc) ? svc.getErrorCode() : e.getMessage();
            log.error("读取 COS 文件失败: key={}, cosError={}", key, cosError, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件读取失败");
        }
    }

    @Override
    public String presignedUrl(String fileUrl, int expireSeconds) {
        if (!isCosUrl(fileUrl)) {
            // 本地路径无需签名，Python worker 直接读共享卷
            return localFileStorageService.presignedUrl(fileUrl, expireSeconds);
        }
        String key = parseKey(fileUrl);
        Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000L);
        try {
            return client().generatePresignedUrl(cosProperties.getBucket(), key, expiration).toString();
        } catch (CosClientException e) {
            String cosError = (e instanceof CosServiceException svc) ? svc.getErrorCode() : e.getMessage();
            log.error("生成 COS 签名 URL 失败: key={}, cosError={}", key, cosError, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件访问地址生成失败");
        }
    }

    /**
     * 是否为本实现管理的 COS URL：host 前缀匹配；非 COS 地址（本地路径/其它域）交由本地实现处理
     */
    private boolean isCosUrl(String fileUrl) {
        if (StrUtil.isBlank(fileUrl) || !StrUtil.startWithIgnoreCase(fileUrl, "http")) {
            return false;
        }
        String host = normalizeHost(cosProperties.getHost());
        return StrUtil.isNotBlank(host) && fileUrl.toLowerCase().startsWith(host.toLowerCase());
    }

    /**
     * 从 COS URL 解析对象 key：去掉 host 前缀与前导斜杠，剥离 query 参数（如签名残留）
     */
    private String parseKey(String fileUrl) {
        String host = normalizeHost(cosProperties.getHost());
        String withoutHost = fileUrl.substring(host.length());
        int queryIdx = withoutHost.indexOf('?');
        if (queryIdx >= 0) {
            withoutHost = withoutHost.substring(0, queryIdx);
        }
        return withoutHost.replaceAll("^/+", "");
    }

    /**
     * 公网图片上传 COS（封面/头像共用）：桶为私有时给对象单独加 public-read，
     * 前端 {@code <img>} 免鉴权即可直接加载。目录前缀区分业务（cover/avatar）。
     */
    private String storeWeb(MultipartFile file, Long userId, String dir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        String host = normalizeHost(cosProperties.getHost());
        // base 配置缺失时占位符会原样保留（如 ${cos.host}），不能当作可用值
        if (!cosConfigured()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "COS 未配置，无法上传图片");
        }
        String originalName = StrUtil.nullToEmpty(file.getOriginalFilename());
        String ext = FileUtil.extName(originalName);
        String datePath = DateUtil.format(new Date(), "yyyyMM");
        String key = StrUtil.format("{}/{}/{}/{}", dir, datePath, userId,
                IdUtil.fastSimpleUUID() + (StrUtil.isBlank(ext) ? "" : "." + ext));
        try (InputStream in = file.getInputStream()) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(file.getSize());
            meta.setContentType(StrUtil.blankToDefault(file.getContentType(), "application/octet-stream"));
            // 私有桶：对象单独 public-read，浏览器 <img> 可免鉴权加载
            PutObjectRequest request = new PutObjectRequest(cosProperties.getBucket(), key, in, meta)
                    .withCannedAcl(CannedAccessControlList.PublicRead);
            client().putObject(request);
        } catch (IOException e) {
            log.error("图片上传 COS 失败: dir={}, file={}", dir, originalName, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片上传失败");
        } catch (CosClientException e) {
            // 网络/签名/服务端错误不吞成"系统内部异常"，明确提示上传失败并带上 COS 错误码便于定位
            String cosError = (e instanceof CosServiceException svc) ? svc.getErrorCode() : e.getMessage();
            log.error("图片上传 COS 异常: dir={}, key={}, cosError={}", dir, key, cosError, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片上传失败");
        }
        String url = host + "/" + key;
        log.info("图片已上传 COS: dir={}, key={}, url={}", dir, key, url);
        return url;
    }

    private COSClient client() {
        if (cosClient == null) {
            synchronized (this) {
                if (cosClient == null) {
                    BasicCOSCredentials creds = new BasicCOSCredentials(
                            cosProperties.getSecretId(), cosProperties.getSecretKey());
                    ClientConfig config = new ClientConfig(
                            new Region(StrUtil.blankToDefault(cosProperties.getRegion(), "ap-guangzhou")));
                    cosClient = new COSClient(creds, config);
                }
            }
        }
        return cosClient;
    }

    private String normalizeHost(String host) {
        return StrUtil.isBlank(host) ? "" : host.replaceAll("/+$", "");
    }

    private boolean isConfigured(String value) {
        return StrUtil.isNotBlank(value) && !value.startsWith("${");
    }

    /**
     * COS 四项配置（host/bucket/secretId/secretKey）是否真实可用（占位符不算配置）
     */
    private boolean cosConfigured() {
        return StrUtil.isNotBlank(normalizeHost(cosProperties.getHost()))
                && StrUtil.isNotBlank(cosProperties.getBucket())
                && isConfigured(cosProperties.getSecretId())
                && isConfigured(cosProperties.getSecretKey());
    }
}
