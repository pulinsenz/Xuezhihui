package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量文档操作请求（批量移除入库 / 批量删除）
 *
 * @author pulinsenz
 */
@Data
public class BatchDocRequest implements Serializable {

    /**
     * 文档 id 列表（JSON 中为字符串，Jackson 自动转 Long）
     */
    private List<Long> docIds;

    private static final long serialVersionUID = 1L;
}
