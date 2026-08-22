package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询请求（用户/知识库列表通用）
 *
 * @author pulinsenz
 */
@Data
public class UserQueryRequest implements Serializable {

    /**
     * 关键词（账号/昵称/知识库名 模糊搜索，可空）
     */
    private String keyword;

    /**
     * 页码，从 1 开始
     */
    private Long pageNum = 1L;

    /**
     * 每页条数，1~50
     */
    private Long pageSize = 10L;

    /**
     * 逻辑删除过滤：null=全部、0=正常、1=已删除（管理员查看被删用户用）
     */
    private Integer deleted;

    private static final long serialVersionUID = 1L;
}
