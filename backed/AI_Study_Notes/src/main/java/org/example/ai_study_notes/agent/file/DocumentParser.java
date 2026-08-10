package org.example.ai_study_notes.agent.file;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 需求文档文本提取：支持 .md/.txt/.pdf/.doc/.docx。
 */
@Slf4j
@Component
public class DocumentParser {

    public String extractText(String fileName, Path filePath) throws Exception {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".txt")) {
            String text = Files.readString(filePath, StandardCharsets.UTF_8);
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        }
        if (lower.endsWith(".pdf")) {
            return extractPdf(filePath);
        }
        if (lower.endsWith(".docx")) {
            return extractDocx(filePath);
        }
        if (lower.endsWith(".doc")) {
            return extractDoc(filePath);
        }
        throw new IllegalArgumentException("不支持的文档格式: " + fileName);
    }

    private String extractPdf(Path filePath) throws Exception {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractDocx(Path filePath) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (InputStream in = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(in)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                builder.append(paragraph.getText()).append('\n');
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder line = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        line.append(cell.getText()).append(" | ");
                    }
                    builder.append(line).append('\n');
                }
            }
        }
        return builder.toString();
    }

    private String extractDoc(Path filePath) throws Exception {
        try (InputStream in = Files.newInputStream(filePath);
             HWPFDocument document = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }
}
