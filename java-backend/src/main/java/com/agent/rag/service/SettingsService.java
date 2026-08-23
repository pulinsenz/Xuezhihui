package com.agent.rag.service;

import com.agent.rag.dto.resp.SettingsVO;

/**
 * 用户设置服务
 *
 * @author pulinsenz
 */
public interface SettingsService {

    /**
     * 当前用户设置
     */
    SettingsVO getSettings();

    /**
     * 更新"上传文档是否默认入库"：1=是 0=否
     */
    void updateVectorizeDefault(Integer defaultVectorize);
}
