package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新"上传文档是否默认入库"设置请求
 *
 * @author pulinsenz
 */
@Data
public class UpdateVectorizeDefaultRequest implements Serializable {

    /**
     * 1=默认入库 0=默认不入库
     */
    private Integer defaultVectorize;

    private static final long serialVersionUID = 1L;
}
