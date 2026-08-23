package com.agent.rag.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户设置
 *
 * @author pulinsenz
 */
@Data
public class SettingsVO implements Serializable {

    /**
     * 上传文档是否默认入库：1=是 0=否
     */
    private Integer defaultVectorize;

    private static final long serialVersionUID = 1L;
}
