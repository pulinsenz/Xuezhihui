package com.agent.rag.common;

/**
 * 文档向量化状态
 *
 * @author pulinsenz
 */
public enum VectorStatus {

    /**
     * 待向量化
     */
    PENDING,

    /**
     * 向量化成功
     */
    SUCCESS,

    /**
     * 向量化失败
     */
    FAILED,

    /**
     * 重复文件，默认跳过向量化（未入库，可强制入库）
     */
    SKIPPED,

    /**
     * 未入库：从索引移除 或 按设置默认不入库（文档记录保留，可重新入库）
     */
    REMOVED
}
