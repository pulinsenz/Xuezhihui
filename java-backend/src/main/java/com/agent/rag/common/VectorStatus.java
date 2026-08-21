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
    FAILED
}
