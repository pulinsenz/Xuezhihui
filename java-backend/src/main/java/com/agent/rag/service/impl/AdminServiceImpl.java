package com.agent.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.RoleConstant;
import com.agent.rag.config.JwtProperties;
import com.agent.rag.dto.req.UpdateUserRoleRequest;
import com.agent.rag.dto.req.UserQueryRequest;
import com.agent.rag.dto.resp.AdminUserVO;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.agent.rag.entity.Knowledge;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.mapper.KnowledgeMapper;
import com.agent.rag.mapper.UserMapper;
import com.agent.rag.service.AdminService;
import com.agent.rag.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理员服务实现
 *
 * @author pulinsenz
 */
@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private KnowledgeMapper knowledgeMapper;

    @Resource
    private KnowledgeDocMapper knowledgeDocMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtProperties jwtProperties;

    @Override
    public Page<AdminUserVO> listUsers(UserQueryRequest request) {
        long pageNum = normalizePageNum(request);
        long pageSize = normalizePageSize(request);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .and(StrUtil.isNotBlank(request.getKeyword()), w -> w
                        .like(User::getUserAccount, request.getKeyword())
                        .or()
                        .like(User::getUserName, request.getKeyword()))
                .orderByDesc(User::getCreateTime);
        Page<User> userPage = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<AdminUserVO> records = userPage.getRecords().stream()
                .map(AdminUserVO::from)
                .toList();
        Page<AdminUserVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public void updateUserRole(Long userId, UpdateUserRoleRequest request) {
        if (userId == null || request == null || StrUtil.isBlank(request.getUserRole())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        String role = request.getUserRole();
        if (!RoleConstant.USER.equals(role) && !RoleConstant.ADMIN.equals(role)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "角色只能是 user 或 admin");
        }
        // 不能操作自己，防止误降级管理员
        if (userId.equals(UserContext.getUser().getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能修改自己的角色");
        }
        User target = userMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }
        User update = new User();
        update.setId(userId);
        update.setUserRole(role);
        userMapper.updateById(update);
        log.info("修改角色成功: userId={}, role={}", userId, role);
    }

    @Override
    public void deleteUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        if (userId.equals(UserContext.getUser().getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能删除自己");
        }
        User target = userMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }
        userMapper.deleteById(userId);
        // 清理该用户全部登录 token，强制下线
        evictLoginTokens(userId);
        log.info("删除用户成功: userId={}", userId);
    }

    @Override
    public Page<KnowledgeVO> listAllKnowledge(UserQueryRequest request) {
        long pageNum = normalizePageNum(request);
        long pageSize = normalizePageSize(request);
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<Knowledge>()
                .and(StrUtil.isNotBlank(request.getKeyword()), w ->
                        w.like(Knowledge::getName, request.getKeyword()))
                .orderByDesc(Knowledge::getCreateTime);
        Page<Knowledge> knowledgePage = knowledgeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<KnowledgeVO> records = knowledgePage.getRecords().stream().map(knowledge -> {
            KnowledgeVO vo = KnowledgeVO.from(knowledge);
            Long docCount = knowledgeDocMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
                    .eq(KnowledgeDoc::getKnowledgeId, knowledge.getId()));
            vo.setDocCount(docCount);
            return vo;
        }).toList();
        Page<KnowledgeVO> voPage = new Page<>(knowledgePage.getCurrent(), knowledgePage.getSize(), knowledgePage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    private long normalizePageNum(UserQueryRequest request) {
        Long pageNum = request.getPageNum();
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private long normalizePageSize(UserQueryRequest request) {
        Long pageSize = request.getPageSize();
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 50);
    }

    /**
     * 用 SCAN 遍历白名单，删除指定用户的全部 token（避免 KEYS 阻塞 Redis）
     */
    private void evictLoginTokens(Long userId) {
        String prefix = jwtProperties.getRedisPrefix();
        String userIdStr = String.valueOf(userId);
        long removed = 0;
        Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(prefix + "*").count(100).build());
        try (cursor) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (userIdStr.equals(stringRedisTemplate.opsForValue().get(key))) {
                    stringRedisTemplate.delete(key);
                    removed++;
                }
            }
        }
        log.info("清理用户登录 token: userId={}, removed={}", userId, removed);
    }
}
