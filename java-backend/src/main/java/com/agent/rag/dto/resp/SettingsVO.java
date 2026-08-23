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

    /**
     * 参考文献默认折叠：1=折叠 0=展开
     */
    private Integer collapseRefs;

    private static final long serialVersionUID = 1L;
}
