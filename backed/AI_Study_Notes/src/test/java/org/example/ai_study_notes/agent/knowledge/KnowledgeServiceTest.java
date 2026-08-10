package org.example.ai_study_notes.agent.knowledge;

import org.example.ai_study_notes.agent.config.AgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeServiceTest {

    @TempDir
    Path tempDir;

    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        AgentProperties properties = new AgentProperties();
        properties.setDataDir(tempDir.toString());
        service = new KnowledgeService(properties);
    }

    @Test
    void saveCreatesFrontmatterDoc() {
        KnowledgeDoc doc = service.save(1L, "登录超时经验", "登录接口超时重试两次后报错",
                "经验教训", List.of("登录", "超时"), false);
        assertEquals("登录超时经验", doc.title());
        assertEquals("经验教训", doc.category());
        assertTrue(doc.confirmed());
        assertTrue(Files.exists(service.userRoot(1L).resolve("经验教训").resolve("登录超时经验.md")));
    }

    @Test
    void overwriteRequiresFlag() {
        service.save(1L, "标题A", "内容1", "默认", null, false);
        assertThrows(IllegalArgumentException.class,
                () -> service.save(1L, "标题A", "内容2", "默认", null, false));
        KnowledgeDoc updated = service.save(1L, "标题A", "内容2", "默认", null, true);
        assertTrue(updated.content().contains("内容2"));
    }

    @Test
    void candidateConfirmMovesToCategory() {
        KnowledgeDoc candidate = service.saveCandidate(1L, "支付测试要点", "支付接口需要幂等键",
                "经验教训", null);
        assertFalse(candidate.confirmed());
        assertTrue(candidate.slug().startsWith("支付测试要点-"));

        KnowledgeDoc confirmed = service.confirm(1L, candidate.slug());
        assertTrue(confirmed.confirmed());
        assertEquals("支付测试要点", confirmed.slug());
        assertFalse(Files.exists(service.userRoot(1L)
                .resolve(KnowledgeService.CANDIDATE_DIR)
                .resolve(candidate.slug() + ".md")));
        assertTrue(Files.exists(service.userRoot(1L)
                .resolve("经验教训").resolve("支付测试要点.md")));
    }

    @Test
    void searchFindsByKeyword() {
        service.save(1L, "登录接口", "用户名密码登录，成功返回 token", "经验教训", List.of("登录"), false);
        service.save(1L, "删除用户", "删除用户需校验存在性", "经验教训", List.of("删除"), false);

        List<KnowledgeDoc> hits = service.search(1L, "登录", 5);
        assertEquals(1, hits.size());
        assertEquals("登录接口", hits.get(0).title());
    }

    @Test
    void userIsolation() {
        service.save(1L, "标题A", "内容1", "默认", null, false);
        assertTrue(service.list(2L, null, false).isEmpty());
        assertEquals(1, service.list(1L, null, false).size());
    }

    @Test
    void pathTraversalSanitized() {
        String safe = service.sanitizeSegment("../secret");
        assertFalse(safe.contains(".."));
        assertFalse(safe.contains("/"));
        String cat = service.sanitizeSegment("a/b\\c");
        assertFalse(cat.contains("/"));
        assertFalse(cat.contains("\\"));
    }

    @Test
    void deleteRemovesDoc() {
        service.save(1L, "标题A", "内容1", "默认", null, false);
        service.delete(1L, "默认", "标题A");
        assertTrue(service.list(1L, null, false).isEmpty());
    }

    @Test
    void smartSaveSkipsDuplicate() {
        service.save(1L, "登录接口超时经验", "登录接口超时后重试两次，超过则返回明确错误，不要无限重试。",
                "经验教训", null, false);
        KnowledgeService.SmartSaveResult result = service.saveSmart(
                1L, "登录接口超时重试策略",
                "登录接口超时后重试两次，超过则返回明确错误，不要无限重试。超时用例可能不稳定，建议用 mock 服务控制响应延迟。",
                "经验教训", List.of("auto"));
        assertEquals("skipped_duplicate", result.action());
        assertEquals(1, service.list(1L, null, false).size());
    }

    @Test
    void smartSaveAppendsRelatedSameCategory() {
        service.save(1L, "登录接口超时经验", "登录接口超时后重试两次，超过则返回明确错误，不要无限重试。",
                "经验教训", null, false);
        KnowledgeService.SmartSaveResult result = service.saveSmart(
                1L, "超时重试场景测试要点",
                "超时重试类用例需验证响应时间、重试次数精确性及错误码是否明确可区分。注意幂等性：登录接口重试时不能产生重复 token。设计用例需覆盖正常、超时重试成功、重试仍失败及临界边界场景。",
                "经验教训", List.of("auto"));
        assertEquals("appended", result.action());
        assertEquals("登录接口超时经验", result.doc().slug());
        assertTrue(result.doc().content().contains("超时重试类用例"));
        assertEquals(1, service.list(1L, null, false).size());
    }

    @Test
    void smartSaveCreatesNewForUnrelated() {
        service.save(1L, "登录接口超时经验", "登录接口超时后重试两次，超过则返回明确错误，不要无限重试。",
                "经验教训", null, false);
        KnowledgeService.SmartSaveResult result = service.saveSmart(
                1L, "支付接口幂等", "支付接口需要幂等键防止重复扣款", "经验教训", List.of("auto"));
        assertEquals("created", result.action());
        assertEquals(2, service.list(1L, null, false).size());
    }

    @Test
    void smartSaveDoesNotMergeAcrossCategories() {
        service.save(1L, "登录接口超时经验", "登录接口超时后重试两次，超过则返回明确错误，不要无限重试。",
                "经验教训", null, false);
        KnowledgeService.SmartSaveResult result = service.saveSmart(
                1L, "超时重试场景测试要点",
                "超时重试类用例需验证响应时间、重试次数精确性及错误码是否明确可区分。注意幂等性：登录接口重试时不能产生重复 token。",
                "测试理论", List.of("auto"));
        assertEquals("created", result.action());
        assertEquals(2, service.list(1L, null, false).size());
    }
}
