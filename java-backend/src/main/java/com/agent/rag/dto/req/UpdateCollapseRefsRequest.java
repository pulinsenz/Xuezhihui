package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新"参考文献默认折叠"设置请求
 *
 * @author pulinsenz
 */
@Data
public class UpdateCollapseRefsRequest implements Serializable {

    /**
     * 1=默认折叠 0=默认展开
     */
    private Integer collapseRefs;

    private static final long serialVersionUID = 1L;
}
