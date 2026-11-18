package com.agent.rag.service;

import cn.hutool.core.util.StrUtil;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.entity.*;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.mapper.UserMapper;
import com.agent.rag.mapper.WikiPageMapper;
import com.agent.rag.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;

/** 仅供问答的只读取证，权限与现有页面服务保持一致，不产生浏览量等副作用。 */
@Service
public class AgentEvidenceService {
    @Resource private KnowledgeService knowledgeService;
    @Resource private ChatHistoryService chatHistoryService;
    @Resource private KnowledgeDocMapper knowledgeDocMapper;
    @Resource private WikiPageMapper wikiPageMapper;
    @Resource private UserMapper userMapper;
    @Resource private ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> knowledgeOutline(Long userId, Long knowledgeId) {
        return asUser(userId, () -> {
            if (knowledgeId == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库 id 不能为空");
            }
            knowledgeService.getViewableKnowledge(knowledgeId);
            List<WikiPage> pages = wikiPageMapper.selectList(new LambdaQueryWrapper<WikiPage>()
                    .eq(WikiPage::getKnowledgeId, knowledgeId)
                    .orderByAsc(WikiPage::getSortOrder, WikiPage::getId));
            List<KnowledgeDoc> docs = knowledgeDocMapper.selectList(new LambdaQueryWrapper<KnowledgeDoc>()
                    .eq(KnowledgeDoc::getKnowledgeId, knowledgeId).orderByAsc(KnowledgeDoc::getId));
            List<Map<String, Object>> entries = new ArrayList<>();
            Set<String> covered = new HashSet<>();
            for (WikiPage page : pages) {
                if (!validPage(page)) continue;
                entries.add(entry(page.getTitle(), page.getSummary(), "wiki-" + page.getId()));
                if (StrUtil.isNotBlank(page.getSourceDocIds())) {
                    covered.addAll(parseDocIds(page.getSourceDocIds()));
                }
            }
            List<Map<String, Object>> documents = new ArrayList<>();
            long uncovered = 0;
            for (KnowledgeDoc doc : docs) {
                if (Integer.valueOf(1).equals(doc.getIsDelete())) continue;
                String id = String.valueOf(doc.getId());
                documents.add(entry(doc.getName(), null, id));
                if (!covered.contains(id)) uncovered++;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("wiki_pages", entries);
            result.put("documents", documents);
            result.put("total", entries.size());
            result.put("uncovered_document_count", uncovered);
            result.put("version", version(result, pages, docs));
            return result;
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> conversationEvidence(Long userId, String sessionId) {
        return asUser(userId, () -> {
            List<ChatMessage> messages = chatHistoryService.getEvidenceMessages(sessionId, userId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (ChatMessage message : messages) {
                List<Map<String, Object>> raw = parseSources(message.getSources());
                List<Map<String, Object>> sources = new ArrayList<>();
                boolean revoked = !canViewKnowledge(message.getKnowledgeId());
                for (Map<String, Object> source : raw) {
                    Object sourceKnowledge = source.get("knowledge_id");
                    if (sourceKnowledge == null) sourceKnowledge = source.get("knowledgeId");
                    String knowledgeId = sourceKnowledge == null ? message.getKnowledgeId() : sourceKnowledge.toString();
                    Map<String, Object> checked = authorizedSource(source, knowledgeId);
                    if (checked == null) revoked = true;
                    else sources.add(checked);
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", String.valueOf(message.getId()));
                item.put("role", message.getRole());
                // 回答正文可能已包含来源原文；权限撤销后同样不能经加工再次暴露。
                item.put("content", revoked && "assistant".equals(message.getRole()) ? "" : message.getContent());
                item.put("sources", revoked ? List.of() : sources);
                item.put("knowledge_id", message.getKnowledgeId());
                item.put("route", message.getRoute());
                item.put("access_revoked", revoked);
                result.add(item);
            }
            return Map.of("messages", result);
        });
    }

    private Map<String, Object> authorizedSource(Map<String, Object> source, String knowledgeId) {
        if (StrUtil.isBlank(knowledgeId) || source.get("doc_id") == null) return null;
        try {
            Long kid = Long.valueOf(knowledgeId);
            knowledgeService.getViewableKnowledge(kid);
            String docId = source.get("doc_id").toString();
            String canonicalId = docId;
            if (docId.startsWith("wiki-")) {
                WikiPage page = null;
                try {
                    page = wikiPageMapper.selectById(Long.valueOf(docId.substring(5)));
                } catch (NumberFormatException ignored) {
                    // 旧历史存的是向量 UUID，必须解析为当前实际页面 id。
                }
                if (page == null) {
                    List<WikiPage> matches = wikiPageMapper.selectList(new LambdaQueryWrapper<WikiPage>()
                            .eq(WikiPage::getKnowledgeId, kid).eq(WikiPage::getVectorDocId, docId));
                    if (!matches.isEmpty()) page = matches.get(0);
                }
                if (page == null || !kid.equals(page.getKnowledgeId()) || !validPage(page)) return null;
                canonicalId = "wiki-" + page.getId();
            } else {
                KnowledgeDoc doc = knowledgeService.getViewableDoc(kid, Long.valueOf(docId));
                if (Integer.valueOf(1).equals(doc.getIsDelete())) return null;
            }
            Map<String, Object> checked = new LinkedHashMap<>(source);
            checked.put("doc_id", canonicalId);
            checked.put("knowledge_id", knowledgeId);
            return checked;
        } catch (NumberFormatException e) {
            return null;
        } catch (BusinessException e) {
            if (unavailable(e)) return null;
            throw e;
        }
    }

    private boolean canViewKnowledge(String knowledgeId) {
        if (StrUtil.isBlank(knowledgeId)) return true;
        try {
            knowledgeService.getViewableKnowledge(Long.valueOf(knowledgeId));
            return true;
        } catch (NumberFormatException e) {
            return false;
        } catch (BusinessException e) {
            if (unavailable(e)) return false;
            throw e;
        }
    }

    private boolean unavailable(BusinessException e) {
        return e.getCode() == ErrorCode.NO_AUTH.getCode() || e.getCode() == ErrorCode.NOT_FOUND.getCode();
    }

    private boolean validPage(WikiPage page) {
        return !Integer.valueOf(1).equals(page.getIsDelete()) && StrUtil.isBlank(page.getErrorMsg());
    }

    private Map<String, Object> entry(String title, String summary, String docId) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("title", title);
        entry.put("summary", summary);
        entry.put("doc_id", docId);
        return entry;
    }

    private List<String> parseDocIds(String json) {
        try {
            List<String> ids = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            if (ids == null) throw new IllegalArgumentException("null sourceDocIds");
            return ids;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Wiki 来源记录格式错误");
        }
    }

    private List<Map<String, Object>> parseSources(String json) {
        if (StrUtil.isBlank(json)) return List.of();
        try {
            List<Map<String, Object>> sources = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            if (sources == null || sources.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("null sources");
            }
            return sources;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "历史来源记录格式错误");
        }
    }

    private String version(Map<String, Object> outline, List<WikiPage> pages, List<KnowledgeDoc> docs) {
        try {
            String snapshot = objectMapper.writeValueAsString(outline) + pages.stream()
                    .map(p -> p.getId() + ":" + p.getUpdateTime()).toList() + docs.stream()
                    .map(d -> d.getId() + ":" + d.getUpdateTime()).toList();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(snapshot.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "目录版本生成失败");
        }
    }

    private <T> T asUser(Long userId, Supplier<T> action) {
        if (userId == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户 id 不能为空");
        User user = userMapper.selectById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getIsDelete())) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "用户不存在或已停用");
        }
        User previous = UserContext.getUser();
        try {
            UserContext.setUser(user);
            return action.get();
        } finally {
            if (previous == null) UserContext.clear();
            else UserContext.setUser(previous);
        }
    }
}
