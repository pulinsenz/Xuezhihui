package com.agent.rag.storage.impl;

import cn.hutool.core.date.DateUtil;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.config.CosProperties;
import com.agent.rag.exception.BusinessException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.CannedAccessControlList;
import com.qcloud.cos.model.PutObjectRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CosFileStorageService 单元测试（Mockito，不连真实 COS）
 * <p>
 * 覆盖：封面走 COS（URL 直链格式 + public-read ACL）、配置缺失报错、COS 网络/服务端异常
 * 包装为业务错误（而非漏成"系统内部异常"）、文档 store 上传 COS 私有对象 / 未配置回退本地。
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
    void storeAvatar_uploadsUnderAvatarPrefix() throws Exception {
        COSClient cosClient = mock(COSClient.class);
        CosFileStorageService svc = service(props(), cosClient);
        MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", "avatar".getBytes());

        String url = svc.storeAvatar(file, 1001L);

        String month = DateUtil.format(new Date(), "yyyyMM");
        assertTrue(url.startsWith("https://picture-1391878614.cos.ap-guangzhou.myqcloud.com/avatar/"
                + month + "/1001/"), "头像应走 avatar/ 前缀: " + url);
        assertTrue(url.endsWith(".png"), "应保留扩展名");
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cosClient).putObject(captor.capture());
        assertEquals(CannedAccessControlList.PublicRead, captor.getValue().getCannedAcl(),
                "头像对象同样需要 public-read，<img> 才能免鉴权加载");
    }

    @Test
    void store_cosConfigured_uploadsPrivateDocAndReturnsCosUrl() throws Exception {
        // 配置了 COS：文档同样上传对象存储（doc/ 前缀），且必须私有（不设 public-read，与封面相反）
        COSClient cosClient = mock(COSClient.class);
        CosFileStorageService svc = service(props(), cosClient);
        MockMultipartFile file = new MockMultipartFile("file", "doc.md", "text/markdown", "hi".getBytes());

        String url = svc.store(file, 1001L);

        String month = DateUtil.format(new Date(), "yyyyMM");
        assertTrue(url.startsWith("https://picture-1391878614.cos.ap-guangzhou.myqcloud.com/doc/"
                + month + "/1001/"), "文档应上传到 doc/ 前缀: " + url);
        assertTrue(url.endsWith(".md"), "应保留扩展名");
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cosClient).putObject(captor.capture());
        assertEquals(null, captor.getValue().getCannedAcl(),
                "文档是私有对象，不得设 public-read");
        // 不再委托本地
        LocalFileStorageService local = (LocalFileStorageService) ReflectionTestUtils.getField(svc, "localFileStorageService");
        verify(local, org.mockito.Mockito.never()).store(any(), any());
    }

    @Test
    void store_cosUnconfigured_fallsBackToLocal() {
        // COS 未配置（占位符原样保留）时回退本地共享卷：纯本地开发/离线可用
        CosProperties p = props();
        p.setHost("${cos.host}");
        p.setSecretId("${cos.secretId}");
        LocalFileStorageService local = mock(LocalFileStorageService.class);
        CosFileStorageService svc = service(p, mock(COSClient.class));
        ReflectionTestUtils.setField(svc, "localFileStorageService", local);
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "hi".getBytes());
        when(local.store(any(), any())).thenReturn("/local/doc.txt");

        assertEquals("/local/doc.txt", svc.store(file, 1L), "COS 未配置时文档应回退本地存储");
        verify(local).store(file, 1L);
    }

    @Test
    void store_cosConfigured_emptyFile_throws() {
        CosFileStorageService svc = service(props(), mock(COSClient.class));
        MockMultipartFile empty = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);
        BusinessException e = assertThrows(BusinessException.class, () -> svc.store(empty, 1L));
        assertTrue(e.getMessage().contains("文件不能为空"), e.getMessage());
    }

    @Test
    void storeForWeb_cosClientException_wrapsAsBusiness() throws Exception {
        // 网络层失败（如 TLS 握手被掐断，即本次 Docker 死代理场景）不得漏成"系统内部异常"
        COSClient cosClient = mock(COSClient.class);
        when(cosClient.putObject(any(PutObjectRequest.class)))
                .thenThrow(new CosClientException("Remote host terminated the handshake"));
        CosFileStorageService svc = service(props(), cosClient);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

        BusinessException e = assertThrows(BusinessException.class, () -> svc.storeForWeb(file, 1L));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("图片上传失败"), "COS 网络错误应明确提示而非系统内部异常: " + e.getMessage());
    }

    @Test
    void storeForWeb_cosServiceException_wrapsAsBusiness() throws Exception {
        // 服务端拒绝（如无权限/桶 ACL 限制），同样包装为业务错误
        COSClient cosClient = mock(COSClient.class);
        CosServiceException svcErr = new CosServiceException("simulated");
        svcErr.setErrorCode("AccessDenied");
        when(cosClient.putObject(any(PutObjectRequest.class))).thenThrow(svcErr);
        CosFileStorageService svc = service(props(), cosClient);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

        BusinessException e = assertThrows(BusinessException.class, () -> svc.storeForWeb(file, 1L));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("图片上传失败"), e.getMessage());
    }

    // ---------- 私有对象读取（open）+ 签名 URL（presignedUrl）----------

    private static final String COS_URL = "https://picture-1391878614.cos.ap-guangzhou.myqcloud.com/doc/202608/1001/a.md";
    private static final String COS_KEY = "doc/202608/1001/a.md";

    @Test
    void open_cosUrl_returnsObjectContent() throws Exception {
        // 下载接口对存量 COS 文档做服务端代理：getObject 按解析出的 key 拉取内容流
        COSClient cosClient = mock(COSClient.class);
        CosFileStorageService svc = service(props(), cosClient);
        COSObject obj = new COSObject();
        obj.setObjectContent(new ByteArrayInputStream("cos-content".getBytes(StandardCharsets.UTF_8)));
        when(cosClient.getObject(props().getBucket(), COS_KEY)).thenReturn(obj);

        InputStream in = svc.open(COS_URL);

        assertEquals("cos-content", new String(StreamUtils.copyToByteArray(in), StandardCharsets.UTF_8));
    }

    @Test
    void open_cosUrl_queryParams_stripped() throws Exception {
        // fileUrl 若残留签名 query 参数（如重试场景），解析 key 时必须剥离，getObject 才能命中对象
        COSClient cosClient = mock(COSClient.class);
        CosFileStorageService svc = service(props(), cosClient);
        COSObject obj = new COSObject();
        obj.setObjectContent(new ByteArrayInputStream(new byte[]{1}));
        when(cosClient.getObject(props().getBucket(), COS_KEY)).thenReturn(obj);

        svc.open(COS_URL + "?q-sign-algorithm=sha1&q-sign-time=1;2");

        verify(cosClient).getObject(props().getBucket(), COS_KEY);
    }

    @Test
    void open_localPath_delegatesToLocal() throws Exception {
        // 本地路径文档（新上传仍走共享卷）由本地实现读取
        LocalFileStorageService local = mock(LocalFileStorageService.class);
        CosFileStorageService svc = service(props(), mock(COSClient.class));
        ReflectionTestUtils.setField(svc, "localFileStorageService", local);
        when(local.open("/data/files/202608/1/x.md")).thenReturn(new ByteArrayInputStream("local".getBytes()));

        InputStream in = svc.open("/data/files/202608/1/x.md");

        assertEquals("local", new String(StreamUtils.copyToByteArray(in), StandardCharsets.UTF_8));
        verify(local).open("/data/files/202608/1/x.md");
    }

    @Test
    void open_cosClientException_wrapsAsBusiness() throws Exception {
        // 私有对象读取失败（无权限/对象不存在）不得漏成"系统内部异常"
        COSClient cosClient = mock(COSClient.class);
        CosFileStorageService svc = service(props(), cosClient);
        when(cosClient.getObject(anyString(), anyString())).thenThrow(new CosClientException("AccessDenied"));

        BusinessException e = assertThrows(BusinessException.class, () -> svc.open(COS_URL));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("文件读取失败"), e.getMessage());
    }

    @Test
    void presignedUrl_cosUrl_generatesSignedUrl() throws Exception {
        // 向量化任务消息里的 file_url 应为临时签名 URL，Python worker 免凭证即可拉取私有对象
        COSClient cosClient = mock(COSClient.class);
        CosFileStorageService svc = service(props(), cosClient);
        URL signed = new URL(COS_URL + "?q-sign-algorithm=sha1");
        when(cosClient.generatePresignedUrl(eq(props().getBucket()), eq(COS_KEY), any(Date.class)))
                .thenReturn(signed);

        String url = svc.presignedUrl(COS_URL, 7200);

        assertEquals(signed.toString(), url, "签名 URL 应由 COS SDK 生成并原样返回");
    }

    @Test
    void presignedUrl_localPath_returnsAsIs() {
        // 本地路径无需签名：Python worker 经共享卷按绝对路径直接读取
        LocalFileStorageService local = mock(LocalFileStorageService.class);
        CosFileStorageService svc = service(props(), mock(COSClient.class));
        ReflectionTestUtils.setField(svc, "localFileStorageService", local);
        when(local.presignedUrl("/data/files/202608/1/x.md", 7200)).thenReturn("/data/files/202608/1/x.md");

        assertEquals("/data/files/202608/1/x.md", svc.presignedUrl("/data/files/202608/1/x.md", 7200));
    }
}
