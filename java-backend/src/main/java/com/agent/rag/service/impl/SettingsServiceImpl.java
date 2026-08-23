package com.agent.rag.service.impl;

import com.agent.rag.common.ErrorCode;
import com.agent.rag.dto.resp.SettingsVO;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.UserMapper;
import com.agent.rag.service.SettingsService;
import com.agent.rag.util.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户设置服务实现
 *
 * @author pulinsenz
 */
@Slf4j
@Service
public class SettingsServiceImpl implements SettingsService {

    @Resource
    private UserMapper userMapper;

    @Override
    public SettingsVO getSettings() {
        User user = getLoginUser();
        SettingsVO vo = new SettingsVO();
        vo.setDefaultVectorize(user.getDefaultVectorize() == null ? 1 : user.getDefaultVectorize());
        vo.setCollapseRefs(user.getCollapseRefs() == null ? 1 : user.getCollapseRefs());
        return vo;
    }

    @Override
    public void updateVectorizeDefault(Integer defaultVectorize) {
        if (defaultVectorize == null || (defaultVectorize != 0 && defaultVectorize != 1)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "defaultVectorize 只能为 0 或 1");
        }
        User user = getLoginUser();
        User update = new User();
        update.setId(user.getId());
        update.setDefaultVectorize(defaultVectorize);
        userMapper.updateById(update);
        log.info("更新默认入库设置: userId={}, defaultVectorize={}", user.getId(), defaultVectorize);
    }

    @Override
    public void updateCollapseRefs(Integer collapseRefs) {
        if (collapseRefs == null || (collapseRefs != 0 && collapseRefs != 1)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "collapseRefs 只能为 0 或 1");
        }
        User user = getLoginUser();
        User update = new User();
        update.setId(user.getId());
        update.setCollapseRefs(collapseRefs);
        userMapper.updateById(update);
        log.info("更新参考文献折叠设置: userId={}, collapseRefs={}", user.getId(), collapseRefs);
    }

    private User getLoginUser() {
        User user = UserContext.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "未登录");
        }
        return user;
    }
}
