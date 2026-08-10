package org.example.ai_study_notes.agent.file;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.example.ai_study_notes.agent.contract.AgentContract;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 附件磁盘存储：{data-dir}/agent/files/{userId}/{conversationId}/，单文件上限 10MB。
 */
@Slf4j
@Service
public class FileStorageService {

    private final AgentProperties properties;
    private final DocumentParser documentParser;

    public FileStorageService(AgentProperties properties, DocumentParser documentParser) {
        this.properties = properties;
        this.documentParser = documentParser;
    }

    public Path store(MultipartFile file, Long userId, Long conversationId) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String lower = fileName.toLowerCase();
        boolean allowed = AgentContract.FILE_ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
        if (!allowed) {
            throw new IllegalArgumentException("仅支持 " + AgentContract.FILE_ALLOWED_EXTENSIONS + " 格式");
        }
        if (file.getSize() > AgentContract.FILE_MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("文件大小不能超过 10MB");
        }
        Path dir = baseDir().resolve(String.valueOf(userId)).resolve(String.valueOf(conversationId));
        Files.createDirectories(dir);
        String storedName = UUID.randomUUID() + "_" + fileName;
        Path target = dir.resolve(storedName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public Path extractTextFile(AgentAttachment attachment) {
        Path source = Path.of(attachment.getStoragePath());
        String text;
        try {
            text = documentParser.extractText(attachment.getFileName(), source);
        } catch (Exception e) {
            log.warn("文档解析失败 fileId={}: {}", attachment.getId(), e.getMessage());
            throw new IllegalStateException("文档解析失败: " + e.getMessage(), e);
        }
        Path textPath = source.resolveSibling(source.getFileName() + ".txt");
        try {
            Files.writeString(textPath, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("解析结果写入失败", e);
        }
        return textPath;
    }

    public String readText(AgentAttachment attachment) {
        if (attachment.getParseResultRef() != null && Files.exists(Path.of(attachment.getParseResultRef()))) {
            try {
                return Files.readString(Path.of(attachment.getParseResultRef()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("读取解析结果失败 fileId={}", attachment.getId(), e);
            }
        }
        try {
            return documentParser.extractText(attachment.getFileName(), Path.of(attachment.getStoragePath()));
        } catch (Exception e) {
            throw new IllegalStateException("读取文件内容失败: " + e.getMessage(), e);
        }
    }

    public void delete(AgentAttachment attachment) {
        try {
            Files.deleteIfExists(Path.of(attachment.getStoragePath()));
            if (attachment.getParseResultRef() != null) {
                Files.deleteIfExists(Path.of(attachment.getParseResultRef()));
            }
        } catch (IOException e) {
            log.warn("删除附件文件失败 fileId={}", attachment.getId(), e);
        }
    }

    public Path baseDir() {
        return Path.of(properties.getDataDir()).resolve("agent").resolve("files");
    }
}
