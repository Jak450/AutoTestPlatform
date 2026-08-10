package org.example.ai_study_notes.agent.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 私有测试知识库：以 MD 文件按用户目录存储，支持 frontmatter、原子写入、
 * 关键词检索与候选确认（自动提炼的知识先落 _candidates，确认后移入正式目录）。
 *
 * 目录结构：
 * {data-dir}/knowledge/{userId}/
 *   ├── _candidates/               # 自动提炼、待用户确认
 *   └── {category}/{slug}.md       # 已确认知识
 */
@Slf4j
@Service
public class KnowledgeService {

    public static final String CANDIDATE_DIR = "_candidates";
    public static final String DEFAULT_CATEGORY = "默认";

    private static final int MAX_INJECT_DOCS = 5;
    private static final int MAX_INJECT_CHARS = 2048;
    private static final int MAX_SNIPPET_CHARS = 300;
    private static final int MAX_CONTENT_CHARS_FOR_SEARCH = 20_000;
    /** 相似度 ≥ 该值视为重复（跳过）；≥ APPEND 且 < DUP 且同分类视为相关（追加合并）。 */
    private static final double DUP_THRESHOLD = 0.65;
    private static final double APPEND_THRESHOLD = 0.22;

    private final AgentProperties properties;

    public KnowledgeService(AgentProperties properties) {
        this.properties = properties;
    }

    public Path userRoot(Long userId) {
        return Path.of(properties.getDataDir()).resolve("knowledge").resolve(String.valueOf(userId));
    }

    private Path candidateRoot(Long userId) {
        return userRoot(userId).resolve(CANDIDATE_DIR);
    }

    /**
     * 保存/更新已确认知识文档（写工具调用，需用户确认）。
     */
    public KnowledgeDoc save(Long userId, String title, String content, String category,
                             List<String> tags, boolean overwrite) {
        String safeTitle = requireText(title, "知识标题不能为空");
        String safeContent = requireText(content, "知识内容不能为空");
        String safeCategory = sanitizeSegment(category == null || category.isBlank() ? DEFAULT_CATEGORY : category);
        String slug = sanitizeSegment(safeTitle);
        Path target = userRoot(userId).resolve(safeCategory).resolve(slug + ".md");
        if (Files.exists(target) && !overwrite) {
            throw new IllegalArgumentException("知识文档已存在: " + safeCategory + "/" + slug + "，如需覆盖请设置 overwrite=true");
        }
        try {
            writeDoc(target, safeTitle, safeCategory, tags == null ? List.of() : tags, true, safeContent);
        } catch (IOException e) {
            throw new IllegalStateException("知识文档写入失败: " + e.getMessage(), e);
        }
        log.info("知识保存成功 userId={} category={} slug={}", userId, safeCategory, slug);
        return readDoc(target);
    }

    /**
     * 智能保存（供自动提炼）：去重 / 追加合并 / 新建。
     * 返回动作：created（新建）、appended（追加到相似文档）、skipped_duplicate（与已有文档重复，跳过）。
     */
    public SmartSaveResult saveSmart(Long userId, String title, String content, String category, List<String> tags) {
        SimilarMatch match = findSimilar(userId, title, content);
        if (match == null || match.score() < APPEND_THRESHOLD) {
            KnowledgeDoc doc = save(userId, title, content, category, tags, false);
            return new SmartSaveResult("created", doc, match);
        }
        if (match.score() >= DUP_THRESHOLD) {
            return new SmartSaveResult("skipped_duplicate", match.doc(), match);
        }
        // 相关但不同分类：保持分类独立，不强行合并（一次性规整由人工/管理端完成）
        if (category != null && !category.isBlank()
                && !category.trim().equalsIgnoreCase(match.doc().category())) {
            KnowledgeDoc doc = save(userId, title, content, category, tags, false);
            return new SmartSaveResult("created", doc, match);
        }
        KnowledgeDoc updated = append(userId, match.doc().category(), match.doc().slug(), title, content);
        return new SmartSaveResult("appended", updated, match);
    }

    /**
     * 在全库（含各分类）中找与给定标题/内容最相似的知识文档。
     */
    public SimilarMatch findSimilar(Long userId, String title, String content) {
        String query = (title + " " + content).toLowerCase(Locale.ROOT);
        Set<String> queryBigrams = bigrams(query);
        if (queryBigrams.isEmpty()) {
            return null;
        }
        SimilarMatch best = null;
        for (KnowledgeDoc doc : list(userId, null, false)) {
            String target = (doc.title() + " " + doc.content()).toLowerCase(Locale.ROOT);
            double score = bigramScore(queryBigrams, bigrams(target));
            if (best == null || score > best.score()) {
                best = new SimilarMatch(doc, score);
            }
        }
        return best;
    }

    /**
     * 把新内容作为一个小节追加到已有知识文档末尾（保留原内容，刷新 updated）。
     */
    public KnowledgeDoc append(Long userId, String category, String slug, String sectionTitle, String content) {
        KnowledgeDoc doc = get(userId, category, slug);
        String merged = doc.content() + "\n\n## " + singleLine(sectionTitle) + "\n" + content.trim();
        Path target = userRoot(userId).resolve(sanitizeSegment(doc.category())).resolve(doc.slug() + ".md");
        try {
            writeDoc(target, doc.title(), doc.category(), doc.tags(), true, merged);
        } catch (IOException e) {
            throw new IllegalStateException("知识追加失败: " + e.getMessage(), e);
        }
        log.info("知识追加到已有文档 category={} slug={}，新增小节: {}", doc.category(), doc.slug(), sectionTitle);
        return readDoc(target);
    }

    /**
     * 删除整个分类目录下的全部知识文档（一次性规整用）。
     */
    public void deleteCategory(Long userId, String category) {
        Path dir = userRoot(userId).resolve(sanitizeSegment(category));
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path p : stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".md")).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            throw new IllegalStateException("分类知识清理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 自动提炼的知识候选（confirmed=false，落 _candidates 目录，不参与注入）。
     */
    public KnowledgeDoc saveCandidate(Long userId, String title, String content, String category, List<String> tags) {
        String safeTitle = requireText(title, "知识候选标题不能为空");
        String safeContent = requireText(content, "知识候选内容不能为空");
        String safeCategory = category == null || category.isBlank() ? DEFAULT_CATEGORY : sanitizeSegment(category);
        String slug = sanitizeSegment(safeTitle) + "-" + UUID.randomUUID().toString().substring(0, 6);
        Path target = candidateRoot(userId).resolve(slug + ".md");
        try {
            writeDoc(target, safeTitle, safeCategory, tags == null ? List.of("auto") : tags, false, safeContent);
        } catch (IOException e) {
            throw new IllegalStateException("知识候选写入失败: " + e.getMessage(), e);
        }
        log.info("知识候选已生成 userId={} slug={}", userId, slug);
        return readDoc(target);
    }

    /**
     * 确认知识候选：移入正式分类目录（同标题已存在则覆盖），并删除候选文件。
     */
    public KnowledgeDoc confirm(Long userId, String candidateSlug) {
        String slug = sanitizeSegment(candidateSlug);
        Path candidate = candidateRoot(userId).resolve(slug + ".md");
        if (!Files.isRegularFile(candidate)) {
            throw new IllegalArgumentException("知识候选不存在: " + slug);
        }
        KnowledgeDoc doc = readDoc(candidate);
        String safeCategory = sanitizeSegment(doc.category());
        String finalSlug = sanitizeSegment(doc.title());
        Path target = userRoot(userId).resolve(safeCategory).resolve(finalSlug + ".md");
        try {
            writeDoc(target, doc.title(), safeCategory, doc.tags(), true, doc.content());
            Files.deleteIfExists(candidate);
        } catch (IOException e) {
            throw new IllegalStateException("知识候选确认失败: " + e.getMessage(), e);
        }
        log.info("知识候选已确认 userId={} category={} slug={}", userId, safeCategory, finalSlug);
        return readDoc(target);
    }

    /**
     * 列出知识文档；category 为空时列出全部（不含 _candidates，除非 includeCandidates=true）。
     */
    public List<KnowledgeDoc> list(Long userId, String category, boolean includeCandidates) {
        List<KnowledgeDoc> docs = new ArrayList<>();
        Path root = userRoot(userId);
        if (includeCandidates) {
            walk(candidateRoot(userId), docs, false);
        }
        Path target = root;
        if (category != null && !category.isBlank()) {
            target = root.resolve(sanitizeSegment(category));
        }
        walk(target, docs, true);
        docs.sort(Comparator.comparing(KnowledgeDoc::updatedAt, Comparator.nullsFirst(Comparator.reverseOrder())));
        return docs;
    }

    /**
     * 关键词检索已确认知识（标题/标签/内容命中计分），返回 Top-N。
     */
    public List<KnowledgeDoc> search(Long userId, String query, int limit) {
        List<KnowledgeDoc> all = list(userId, null, false);
        if (query == null || query.isBlank()) {
            return all.stream().limit(Math.max(1, limit)).toList();
        }
        List<String> terms = tokenize(query);
        List<KnowledgeDoc> scored = new ArrayList<>(all);
        scored.sort((a, b) -> {
            int byScore = Integer.compare(score(b, terms), score(a, terms));
            if (byScore != 0) {
                return byScore;
            }
            return Comparator.comparing(KnowledgeDoc::updatedAt,
                    Comparator.nullsFirst(Comparator.reverseOrder())).compare(a, b);
        });
        int n = Math.max(1, limit);
        return scored.stream().filter(d -> score(d, terms) > 0)
                .limit(n).toList();
    }

    /**
     * 按 slug（可选 category）读取单篇知识文档全文；候选与已确认都支持。
     */
    public KnowledgeDoc get(Long userId, String category, String slug) {
        String safeSlug = sanitizeSegment(requireText(slug, "知识 slug 不能为空"));
        Path candidate = candidateRoot(userId).resolve(safeSlug + ".md");
        if (Files.isRegularFile(candidate)) {
            return readDoc(candidate);
        }
        if (category != null && !category.isBlank() && !CANDIDATE_DIR.equals(category)) {
            Path target = userRoot(userId).resolve(sanitizeSegment(category)).resolve(safeSlug + ".md");
            if (Files.isRegularFile(target)) {
                return readDoc(target);
            }
        }
        for (KnowledgeDoc doc : list(userId, null, true)) {
            if (safeSlug.equals(doc.slug())) {
                return doc;
            }
        }
        throw new IllegalArgumentException("知识文档不存在: " + safeSlug);
    }

    /**
     * 按标题精确/包含匹配查找知识文档（供"追加到已有文档"使用）。
     */
    public KnowledgeDoc findByTitle(Long userId, String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String target = title.trim();
        for (KnowledgeDoc doc : list(userId, null, false)) {
            if (doc.title().equalsIgnoreCase(target)) {
                return doc;
            }
        }
        for (KnowledgeDoc doc : list(userId, null, false)) {
            String existing = doc.title();
            if (existing.length() >= 4 && (existing.contains(target) || target.contains(existing))) {
                return doc;
            }
        }
        return null;
    }

    /**
     * 供上下文注入：按查询返回 Top-N 知识片段，总预算 maxChars。
     */
    public List<String> injectable(Long userId, String query, int maxChars) {
        List<KnowledgeDoc> docs = search(userId, query, MAX_INJECT_DOCS);
        List<String> lines = new ArrayList<>();
        int budget = maxChars <= 0 ? MAX_INJECT_CHARS : maxChars;
        for (KnowledgeDoc doc : docs) {
            String line = "[" + doc.category() + "] " + doc.title() + ": "
                    + snippet(doc.content(), 160);
            if (budget - line.length() < 0 && !lines.isEmpty()) {
                break;
            }
            budget -= line.length();
            lines.add(line);
        }
        return lines;
    }

    /**
     * 删除已确认知识文档；category 为空时默认 默认 分类。
     */
    public void delete(Long userId, String category, String title) {
        String safeCategory = category == null || category.isBlank() ? DEFAULT_CATEGORY : sanitizeSegment(category);
        String slug = sanitizeSegment(requireText(title, "知识标题不能为空"));
        Path target = userRoot(userId).resolve(safeCategory).resolve(slug + ".md");
        try {
            if (!Files.deleteIfExists(target)) {
                throw new IllegalArgumentException("知识文档不存在: " + safeCategory + "/" + slug);
            }
        } catch (IOException e) {
            throw new IllegalStateException("知识文档删除失败: " + e.getMessage(), e);
        }
        log.info("知识删除成功 userId={} category={} slug={}", userId, safeCategory, slug);
    }

    /**
     * 删除知识候选。
     */
    public void deleteCandidate(Long userId, String candidateSlug) {
        String slug = sanitizeSegment(candidateSlug);
        Path target = candidateRoot(userId).resolve(slug + ".md");
        try {
            if (!Files.deleteIfExists(target)) {
                throw new IllegalArgumentException("知识候选不存在: " + slug);
            }
        } catch (IOException e) {
            throw new IllegalStateException("知识候选删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 目录/文件名分段消毒：禁止路径分隔符与特殊字符，防止目录穿越。
     */
    public String sanitizeSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_CATEGORY;
        }
        String cleaned = raw.replaceAll("[\\\\/:*?\"<>|\\s]+", "-")
                .replaceAll("^[\\.-]+|[\\.-]+$", "");
        if (cleaned.isBlank()) {
            return DEFAULT_CATEGORY;
        }
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    private void walk(Path dir, List<KnowledgeDoc> out, boolean skipCandidates) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .filter(p -> !skipCandidates || !p.toString().contains(CANDIDATE_DIR))
                    .forEach(p -> {
                        try {
                            out.add(readDoc(p));
                        } catch (Exception e) {
                            log.warn("知识文档读取失败，跳过 {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("知识目录扫描失败 {}: {}", dir, e.getMessage());
        }
    }

    private void writeDoc(Path target, String title, String category, List<String> tags, boolean confirmed,
                          String content)
            throws IOException {
        Files.createDirectories(target.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("title: ").append(singleLine(title)).append('\n');
        sb.append("category: ").append(singleLine(category)).append('\n');
        sb.append("tags: [").append(tags == null ? "" : String.join(", ", tags)).append("]\n");
        sb.append("confirmed: ").append(confirmed).append('\n');
        sb.append("updated: ").append(LocalDateTime.now()).append('\n');
        sb.append("---\n\n");
        sb.append(requireText(content, "知识内容不能为空").trim());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private KnowledgeDoc readDoc(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            String title = "";
            String category = DEFAULT_CATEGORY;
            List<String> tags = List.of();
            boolean confirmed = true;
            LocalDateTime updatedAt = LocalDateTime.now();
            int bodyStart = 0;
            if (lines.size() >= 2 && "---".equals(lines.get(0).trim())) {
                int i = 1;
                while (i < lines.size() && !"---".equals(lines.get(i).trim())) {
                    String line = lines.get(i);
                    int idx = line.indexOf(':');
                    if (idx > 0) {
                        String key = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        switch (key) {
                            case "title" -> title = value;
                            case "category" -> category = value.isBlank() ? DEFAULT_CATEGORY : value;
                            case "tags" -> tags = parseTags(value);
                            case "confirmed" -> confirmed = Boolean.parseBoolean(value);
                            case "updated" -> updatedAt = parseTime(value, updatedAt);
                            default -> { }
                        }
                    }
                    i++;
                }
                bodyStart = i + 1;
            }
            StringBuilder body = new StringBuilder();
            for (int j = bodyStart; j < lines.size(); j++) {
                body.append(lines.get(j)).append('\n');
            }
            String content = body.toString().trim();
            String slug = path.getFileName().toString().replaceFirst("\\.md$", "");
            return new KnowledgeDoc(slug, category, title, tags, confirmed, content,
                    snippet(content, MAX_SNIPPET_CHARS), updatedAt);
        } catch (IOException e) {
            throw new IllegalStateException("知识文档读取失败: " + path, e);
        }
    }

    private int score(KnowledgeDoc doc, List<String> terms) {
        if (terms.isEmpty()) {
            return 1;
        }
        String haystack = (doc.title() + " " + String.join(" ", doc.tags())
                + " " + doc.content()).toLowerCase(Locale.ROOT);
        String limited = haystack.length() > MAX_CONTENT_CHARS_FOR_SEARCH
                ? haystack.substring(0, MAX_CONTENT_CHARS_FOR_SEARCH) : haystack;
        int score = 0;
        for (String term : terms) {
            if (limited.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private List<String> tokenize(String query) {
        List<String> terms = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return terms;
        }
        String[] parts = query.toLowerCase(Locale.ROOT).split("[\\s,，。；;、:：]+");
        for (String part : parts) {
            if (part.length() >= 2) {
                terms.add(part);
            }
        }
        return terms;
    }

    private Set<String> bigrams(String text) {
        Set<String> set = new HashSet<>();
        String clean = text == null ? "" : text.replaceAll("\\s+", "");
        for (int i = 0; i + 1 < clean.length(); i++) {
            set.add(clean.substring(i, i + 2));
        }
        return set;
    }

    private double bigramScore(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String s : a) {
            if (b.contains(s)) {
                inter++;
            }
        }
        double containment = (double) inter / Math.min(a.size(), b.size());
        double jaccard = (double) inter / (a.size() + b.size() - inter);
        return Math.max(containment, jaccard);
    }

    private List<String> parseTags(String value) {
        if (value == null || value.isBlank() || "[]".equals(value.trim())) {
            return List.of();
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("[")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.endsWith("]")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return Stream.of(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private LocalDateTime parseTime(String value, LocalDateTime fallback) {
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private String singleLine(String text) {
        return text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String snippet(String text, int max) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > max ? oneLine.substring(0, max) + "…" : oneLine;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public record SimilarMatch(KnowledgeDoc doc, double score) {
    }

    public record SmartSaveResult(String action, KnowledgeDoc doc, SimilarMatch match) {
    }
}
