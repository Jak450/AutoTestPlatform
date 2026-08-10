package org.example.ai_study_notes.agent.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.ai_study_notes.agent.contract.AgentContract;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用例模板服务：模板 CRUD + 会话激活模板。
 */
@Service
public class CaseTemplateService {

    private static final String ACTIVE_TEMPLATE_PREFIX = "agent:conv:";
    private static final String ACTIVE_TEMPLATE_SUFFIX = ":activeTemplate";

    private final CaseTemplateMapper templateMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public CaseTemplateService(CaseTemplateMapper templateMapper, RedisTemplate<String, Object> redisTemplate) {
        this.templateMapper = templateMapper;
        this.redisTemplate = redisTemplate;
    }

    public CaseTemplate create(Long userId, String name, String description,
                               String caseShape, String coverageRules, String assertRules, String examples) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        Long exists = templateMapper.selectCount(new LambdaQueryWrapper<CaseTemplate>()
                .eq(CaseTemplate::getUserId, userId)
                .eq(CaseTemplate::getName, name));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("同名模板已存在");
        }
        CaseTemplate template = CaseTemplate.builder()
                .userId(userId)
                .name(name)
                .description(description == null ? "" : description)
                .caseShape(caseShape == null ? "{}" : caseShape)
                .coverageRules(coverageRules)
                .assertRules(assertRules)
                .examples(examples)
                .build();
        templateMapper.insert(template);
        return template;
    }

    public List<CaseTemplate> list(Long userId) {
        return templateMapper.selectList(new LambdaQueryWrapper<CaseTemplate>()
                .eq(CaseTemplate::getUserId, userId)
                .orderByDesc(CaseTemplate::getUpdatedAt));
    }

    public CaseTemplate getOwned(Long userId, Long templateId) {
        CaseTemplate template = templateMapper.selectById(templateId);
        if (template == null || !template.getUserId().equals(userId)) {
            return null;
        }
        return template;
    }

    public void delete(Long userId, Long templateId) {
        if (getOwned(userId, templateId) == null) {
            throw new IllegalArgumentException("模板不存在或无权访问");
        }
        // 激活引用在 Redis，模板删除后 getActive 会解析为 null，无需主动清理
        templateMapper.deleteById(templateId);
    }

    public void setActive(Long conversationId, CaseTemplate template) {
        String key = activeKey(conversationId);
        if (template == null) {
            redisTemplate.delete(key);
        } else {
            redisTemplate.opsForValue().set(key, template.getId(),
                    AgentContract.SESSION_RETENTION_DAYS, TimeUnit.DAYS);
        }
    }

    public CaseTemplate getActive(Long conversationId) {
        Object value = redisTemplate.opsForValue().get(activeKey(conversationId));
        if (value == null) {
            return null;
        }
        Long templateId = value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
        return templateMapper.selectById(templateId);
    }

    private String activeKey(Long conversationId) {
        return ACTIVE_TEMPLATE_PREFIX + conversationId + ACTIVE_TEMPLATE_SUFFIX;
    }

    public Map<String, Object> toMap(CaseTemplate template) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", template.getId());
        map.put("name", template.getName());
        map.put("description", template.getDescription());
        map.put("caseShape", template.getCaseShape());
        map.put("coverageRules", template.getCoverageRules());
        map.put("assertRules", template.getAssertRules());
        map.put("examples", template.getExamples());
        return map;
    }
}
