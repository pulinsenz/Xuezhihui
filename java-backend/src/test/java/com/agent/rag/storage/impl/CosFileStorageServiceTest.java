package com.agent.rag.storage.impl;

import cn.hutool.core.date.DateUtil;
import com.agent.rag.config.CosProperties;
import com.agent.rag.exception.BusinessException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.CannedAccessControlList;
import com.qcloud.cos.model.PutObjectRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CosFileStorageService 单元测试（Mockito，不连真实 COS）
 * <p>
 * 覆盖：封面走 COS（URL 直链格式 + public-read ACL）、配置缺失报错、文档 store 委托本地。
 *
 * @author pulinsenz
 */
class CosFileStorageServiceTest {

    private CosProperties props() {
        CosProperties p = new CosProperties();
        p.setHost("https://picture-1391878614.cos.ap-guangzhou.myqcloud.com");
        p.setSecretId("AKIDtest");
        p.setSecretKey("secretKeyTest");
        p.setRegion("ap-guangzhou");
        p.setBucket("picture-1391878614");
        return p;
    }

    private CosFileStorageService service(CosProperties props, COSClient cosClient) {
        CosFileStorageService svc = new CosFileStorageService();
        ReflectionTestUtils.setField(svc, "cosProperties", props);
        ReflectionTestUtils.setField(svc, "localFileStorageService", mock(LocalFileStorageService.class));
        ReflectionTestUtils.setField(svc, "cosClient", cosClient);
        return svc;
    }

    @Test
    void storeForWeb_uploadsPublicRead_andReturnsCosUrl() throws Exception {
        COSClient cosClient = mock(COSClient.class);
        CosFileStorageService svc = service(props(), cosClient);
        MockMultipartFile file = new MockMultipartFile("file", "封面.png", "image/png", "png-content".getBytes());

        String url = svc.storeForWeb(file, 1001L);

        String month = DateUtil.format(new Date(), "yyyyMM");
        assertTrue(url.startsWith("https://picture-1391878614.cos.ap-guangzhou.myqcloud.com/cover/"
                + month + "/1001/"), "应为 COS 直链: " + url);
        assertTrue(url.endsWith(".png"), "应保留扩展名");
        // 私有桶必须给对象加 public-read，前端 <img> 才能免鉴权加载
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cosClient).putObject(captor.capture());
        assertEquals(CannedAccessControlList.PublicRead, captor.getValue().getCannedAcl(),
                "上传请求必须带 public-read ACL");
    }

    @Test
    void storeForWeb_unconfiguredCos_throws() throws Exception {
        // base 配置未注入时占位符原样保留（如 ${cos.host}），不能当可用 URL
        CosProperties p = props();
        p.setHost("${cos.host}");
        p.setSecretId("${cos.secretId}");
        CosFileStorageService svc = service(p, mock(COSClient.class));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

        BusinessException e = assertThrows(BusinessException.class, () -> svc.storeForWeb(file, 1L));
        assertTrue(e.getMessage().contains("COS 未配置"), e.getMessage());
    }

    @Test
    void storeForWeb_emptyFile_throws() {
        CosFileStorageService svc = service(props(), mock(COSClient.class));
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertThrows(BusinessException.class, () -> svc.storeForWeb(empty, 1L));
    }

    @Test
    void store_delegatesToLocal() {
        LocalFileStorageService local = mock(LocalFileStorageService.class);
        CosFileStorageService svc = service(props(), mock(COSClient.class));
        ReflectionTestUtils.setField(svc, "localFileStorageService", local);
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "hi".getBytes());
        when(local.store(any(), any())).thenReturn("/local/doc.txt");

        assertEquals("/local/doc.txt", svc.store(file, 1L), "文档文件应继续走本地存储");
        verify(local).store(file, 1L);
    }
}
