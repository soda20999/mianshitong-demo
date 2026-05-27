package com.mianshitong.project.service.impl;

import com.mianshitong.project.common.exception.BizException;
import com.mianshitong.project.entity.po.ReportPo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ReportPdfExporter {

    private static final float PAGE_MARGIN = 48f;
    private static final float LINE_GAP = 4f;
    private static final float SECTION_GAP = 12f;
    private static final float BLOCK_GAP = 8f;
    private static final float TITLE_SIZE = 18f;
    private static final float SECTION_SIZE = 13f;
    private static final float TEXT_SIZE = 11f;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<Path> FONT_CANDIDATES = List.of(
        Path.of("C:/Windows/Fonts/msyh.ttc"),
        Path.of("C:/Windows/Fonts/msyh.ttf"),
        Path.of("C:/Windows/Fonts/simsun.ttc"),
        Path.of("C:/Windows/Fonts/simhei.ttf"),
        Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
        Path.of("/usr/share/fonts/truetype/arphic/uming.ttc"),
        Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    );

    public byte[] export(ReportPo report) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFont font = loadFont(document);
            PdfWriter writer = new PdfWriter(document, font);
            writeReport(writer, report);
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BizException("导出 PDF 失败：" + ex.getMessage());
        }
    }

    private void writeReport(PdfWriter writer, ReportPo report) throws IOException {
        writer.writeTitle("面试复盘报告");

        writer.writeSection("基础信息");
        writer.writeLine("报告ID: " + valueOf(report.getId()));
        writer.writeLine("面试ID: " + valueOf(report.getInterviewId()));
        writer.writeLine("状态: " + valueOf(report.getStatus()));
        writer.writeLine("创建时间: " + formatTime(report.getCreatedAt()));
        writer.writeLine("更新时间: " + formatTime(report.getUpdatedAt()));
        writer.blankBlock();

        writer.writeSection("综合得分");
        writer.writeLine(String.valueOf(report.getOverallScore() == null ? 0 : report.getOverallScore()));
        writer.blankBlock();

        writer.writeSection("能力维度评分");
        Map<String, Integer> dimensions = report.getDimensions() == null
            ? Map.of()
            : new LinkedHashMap<>(report.getDimensions());
        if (dimensions.isEmpty()) {
            writer.writeLine("暂无");
        } else {
            for (Map.Entry<String, Integer> entry : dimensions.entrySet()) {
                writer.writeLine(entry.getKey() + ": " + (entry.getValue() == null ? 0 : entry.getValue()));
            }
        }
        writer.blankBlock();

        writer.writeSection("高频失分点");
        writer.writeBullets(report.getWeakPoints());
        writer.blankSection();

        writer.writeSection("复习路线");
        writer.writeBullets(report.getReviewRoadmap());
        writer.blankSection();

        writer.writeSection("本场问题列表");
        writer.writeBullets(report.getQuestionList());
        writer.blankSection();

        writer.writeSection("用户回答摘录");
        writer.writeBullets(report.getUserAnswerHighlights());
        writer.blankSection();

        writer.writeSection("AI 推荐标准表达");
        writer.writeBullets(report.getAiStandardAnswers());
        writer.blankSection();

        writer.writeSection("亮点总结");
        writer.writeBullets(report.getBrightSpots());
    }

    private String valueOf(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "-" : value.format(TIME_FORMATTER);
    }

    private PDFont loadFont(PDDocument document) {
        for (Path path : FONT_CANDIDATES) {
            if (!Files.exists(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                return PDType0Font.load(document, input, true);
            } catch (Exception ignore) {
                // try next font
            }
        }
        return PDType1Font.HELVETICA;
    }

    private static final class PdfWriter {
        private final PDDocument document;
        private final PDFont font;
        private final float contentWidth;
        private PDPage currentPage;
        private float y;

        private PdfWriter(PDDocument document, PDFont font) {
            this.document = document;
            this.font = font;
            this.currentPage = new PDPage(PDRectangle.A4);
            this.document.addPage(this.currentPage);
            this.contentWidth = PDRectangle.A4.getWidth() - PAGE_MARGIN * 2;
            this.y = PDRectangle.A4.getHeight() - PAGE_MARGIN;
        }

        private void writeTitle(String text) throws IOException {
            writeWrapped(text, TITLE_SIZE, 0f);
            y -= BLOCK_GAP;
        }

        private void writeSection(String text) throws IOException {
            writeWrapped(text, SECTION_SIZE, 0f);
            y -= LINE_GAP;
        }

        private void writeLine(String text) throws IOException {
            String safe = StringUtils.hasText(text) ? text : "-";
            writeWrapped(safe, TEXT_SIZE, 0f);
        }

        private void writeBullets(List<String> items) throws IOException {
            List<String> safeItems = sanitize(items);
            if (safeItems.isEmpty()) {
                writeLine("暂无");
                return;
            }
            for (String item : safeItems) {
                writeWrapped("- " + item, TEXT_SIZE, 0f);
            }
        }

        private void blankBlock() {
            y -= BLOCK_GAP;
        }

        private void blankSection() {
            y -= SECTION_GAP;
        }

        private void writeWrapped(String text, float fontSize, float indent) throws IOException {
            List<String> wrapped = wrapText(text, fontSize, contentWidth - indent);
            if (wrapped.isEmpty()) {
                wrapped = List.of("-");
            }
            for (String line : wrapped) {
                ensureSpace(fontSize + LINE_GAP);
                writeTextLine(line, fontSize, PAGE_MARGIN + indent);
                y -= fontSize + LINE_GAP;
            }
        }

        private void ensureSpace(float requiredHeight) {
            if (y - requiredHeight >= PAGE_MARGIN) {
                return;
            }
            currentPage = new PDPage(PDRectangle.A4);
            document.addPage(currentPage);
            y = PDRectangle.A4.getHeight() - PAGE_MARGIN;
        }

        private void writeTextLine(String line, float fontSize, float x) throws IOException {
            String safeLine = StringUtils.hasText(line) ? line : " ";
            try (PDPageContentStream stream = new PDPageContentStream(document, currentPage, AppendMode.APPEND, true, true)) {
                stream.beginText();
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(x, y);
                stream.showText(safeLine);
                stream.endText();
            }
        }

        private List<String> wrapText(String text, float fontSize, float width) throws IOException {
            List<String> lines = new ArrayList<>();
            String safe = text == null ? "" : text.replace("\r", "");
            String[] paragraphs = safe.split("\n", -1);
            for (String paragraph : paragraphs) {
                String line = paragraph == null ? "" : paragraph.trim();
                if (line.isEmpty()) {
                    lines.add("");
                    continue;
                }
                StringBuilder current = new StringBuilder();
                for (int i = 0; i < line.length(); i++) {
                    char ch = line.charAt(i);
                    String candidate = current.toString() + ch;
                    float candidateWidth = font.getStringWidth(candidate) / 1000f * fontSize;
                    if (candidateWidth > width && current.length() > 0) {
                        lines.add(current.toString());
                        current = new StringBuilder().append(ch);
                    } else {
                        current.append(ch);
                    }
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
            }
            return lines;
        }

        private List<String> sanitize(List<String> items) {
            if (items == null || items.isEmpty()) {
                return List.of();
            }
            return items.stream()
                .map(item -> item == null ? "" : item.replaceAll("\\s+", " ").trim())
                .filter(StringUtils::hasText)
                .toList();
        }
    }
}

