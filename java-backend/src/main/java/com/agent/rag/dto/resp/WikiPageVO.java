package com.agent.rag.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;

/**
 * AI Wiki 页面视图对象。
 * 列表接口不含 content（正文较大），详情接口携带。
 *
 * @author pulinsenz
 */
@Data
public class WikiPageVO implements Serializable {

    /**
     * 页面 id
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 页面标题
     */
    private String title;

    /**
     * 页面摘要
     */
    private String summary;

    /**
     * Markdown 正文（仅详情接口返回）
     */
    private String content;

    /**
     * 来源文档 id JSON 数组字符串（如 ["1","3"]）
     */
    private String sourceDocIds;

    /**
     * 页面顺序（从 1 开始）
     */
    private Integer sortOrder;

    /**
     * 页面级状态：SUCCESS / FAILED
     */
    private String status;

    /**
     * 单页生成失败原因
     */
    private String errorMsg;

    /**
     * 更新时间（ISO 字符串）
     */
    private String updateTime;

    private static final long serialVersionUID = 1L;
}
