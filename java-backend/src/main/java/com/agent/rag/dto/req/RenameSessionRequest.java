package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 重命名会话请求（仅本人可操作）
 *
 * @author pulinsenz
 */
@Data
public class RenameSessionRequest implements Serializable {

    /**
     * 新的会话名称（去空格后非空，最长 64 字符，与 title 列宽一致）
     */
    private String title;

    private static final long serialVersionUID = 1L;
}
