package com.agent.rag.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 上传文件类型白名单校验
 * <p>
 * 只按文件扩展名判定（与 python-agent 解析能力对齐）：
 * <ul>
 *   <li>文档：txt/md/markdown/csv/json/html/xml/pdf/docx —— 与 python-agent knowledge_api 的解析集合一致，
 *       白名单外类型 Python 侧无法可靠解析，直接拒绝避免脏数据入库；</li>
 *   <li>封面/头像：jpg/jpeg/png/gif/webp/bmp —— 仅允许位图。明确<b>排除 svg</b>：SVG 是 XML 文本，
 *       可内嵌脚本，直接以 image/svg+xml 提供会形成存储型 XSS；</li>
 * </ul>
 * 校验放 Service 层上传方法入口，先于落盘/入库执行。
 *
 * @author pulinsenz
 */
public final class UploadFileTypeValidator {

    private UploadFileTypeValidator() {
    }

    /** 文档白名单：与 python-agent/api/knowledge_api.py 的 TEXT_EXTS + pdf + docx 保持一致 */
    public static final Set<String> DOCUMENT_EXTS = Set.of(
            "txt", "md", "markdown", "csv", "json", "html", "xml", "pdf", "docx");

    /** 图片白名单：仅位图，排除 svg（可嵌脚本的 XML，存储型 XSS 风险） */
    public static final Set<String> IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp");

    /**
     * 校验文档类型（知识库文档上传）。非法扩展名抛 PARAMS_ERROR。
     */
    public static void checkDocument(MultipartFile file) {
        String ext = extOf(file);
        if (!DOCUMENT_EXTS.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的文件类型" + (StrUtil.isBlank(ext) ? "" : " ." + ext)
                            + "，仅支持：pdf / docx / txt / md / csv / json / html / xml");
        }
    }

    /**
     * 校验图片类型（封面 / 头像上传）。非法扩展名抛 PARAMS_ERROR。
     */
    public static void checkImage(MultipartFile file) {
        String ext = extOf(file);
        if (!IMAGE_EXTS.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的文件类型" + (StrUtil.isBlank(ext) ? "" : " ." + ext)
                            + "，仅支持：jpg / jpeg / png / gif / webp / bmp");
        }
    }

    /** 取原始文件名扩展名（小写、去点）；无扩展名返回空串 */
    private static String extOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        String originalName = StrUtil.nullToEmpty(file.getOriginalFilename());
        return FileUtil.extName(originalName).toLowerCase();
    }
}
