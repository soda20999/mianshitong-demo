package com.mianshitong.project.util;

import com.mianshitong.project.common.exception.BizException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ResumeDocumentExtractor {

    private static final Pattern PDF_TEXT_BLOCK = Pattern.compile("BT(.*?)ET", Pattern.DOTALL);
    private static final Pattern PDF_TEXT_LINE = Pattern.compile("\\((.*?)(?<!\\\\)\\)\\s*Tj", Pattern.DOTALL);
    private static final Pattern PDF_TEXT_ARRAY = Pattern.compile("\\[(.*?)]\\s*TJ", Pattern.DOTALL);
    private static final Pattern PDF_TEXT_ARRAY_ITEM = Pattern.compile("\\((.*?)(?<!\\\\)\\)", Pattern.DOTALL);
    private static final int MIN_READABLE_SCORE = 30;

    public String extract(MultipartFile file, String fileName) {
        Objects.requireNonNull(file, "file");
        byte[] bytes = readBytes(file);
        String lower = fileName.toLowerCase(Locale.ROOT);
        String text;
        if (lower.endsWith(".docx")) {
            text = extractFromDocx(bytes);
        } else if (lower.endsWith(".pdf")) {
            text = extractFromPdf(bytes);
        } else if (lower.endsWith(".doc")) {
            text = extractFromDoc(bytes);
        } else {
            throw new BizException("仅支持 PDF / DOC / DOCX 简历");
        }
        text = normalize(text);
        if (text.isBlank()) {
            text = normalize(extractPrintable(new String(bytes, StandardCharsets.ISO_8859_1)));
        }
        if (text.isBlank()) {
            throw new BizException("文档内容为空或暂无法解析，请确认简历内容是否可读");
        }
        return text;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BizException("读取简历文件失败");
        }
    }

    private String extractFromDocx(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || !name.startsWith("word/") || !name.endsWith(".xml")) {
                    continue;
                }
                String xml = new String(readEntry(zipInputStream), StandardCharsets.UTF_8);
                builder.append(parseDocxXml(xml)).append('\n');
            }
        } catch (IOException ex) {
            throw new BizException("Word 文档解析失败");
        }
        return builder.toString();
    }

    private byte[] readEntry(ZipInputStream zipInputStream) throws IOException {
        byte[] buffer = new byte[4096];
        int len;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while ((len = zipInputStream.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        return out.toByteArray();
    }

    private String parseDocxXml(String xml) {
        String text = xml
            .replace("</w:p>", "\n")
            .replace("</w:tr>", "\n")
            .replace("<w:tab/>", "\t")
            .replace("<w:tab />", "\t")
            .replaceAll("<[^>]+>", "");
        return unescapeXml(text);
    }

    private String unescapeXml(String text) {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'");
    }

    private String extractFromPdf(byte[] bytes) {
        String viaPdfBox = extractFromPdfByPdfBox(bytes);
        if (isReadable(viaPdfBox)) {
            return viaPdfBox;
        }

        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        StringBuilder builder = new StringBuilder();
        Matcher blockMatcher = PDF_TEXT_BLOCK.matcher(raw);
        while (blockMatcher.find()) {
            String block = blockMatcher.group(1);
            appendPdfTextLine(builder, block);
            appendPdfTextArray(builder, block);
        }
        String fallback = builder.length() == 0 ? extractPrintable(raw) : builder.toString();
        fallback = normalize(fallback);
        if (!isReadable(fallback) || looksLikePdfNoise(fallback)) {
            return "";
        }
        return fallback;
    }

    private String extractFromPdfByPdfBox(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            if (document.isEncrypted()) {
                try {
                    document.setAllSecurityToBeRemoved(true);
                } catch (Exception ignore) {
                    // ignore and continue
                }
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return normalize(stripper.getText(document));
        } catch (Exception ignore) {
            return "";
        }
    }

    private void appendPdfTextLine(StringBuilder builder, String block) {
        Matcher lineMatcher = PDF_TEXT_LINE.matcher(block);
        while (lineMatcher.find()) {
            builder.append(decodePdfEscapes(lineMatcher.group(1))).append('\n');
        }
    }

    private void appendPdfTextArray(StringBuilder builder, String block) {
        Matcher arrayMatcher = PDF_TEXT_ARRAY.matcher(block);
        while (arrayMatcher.find()) {
            String array = arrayMatcher.group(1);
            Matcher itemMatcher = PDF_TEXT_ARRAY_ITEM.matcher(array);
            while (itemMatcher.find()) {
                builder.append(decodePdfEscapes(itemMatcher.group(1)));
            }
            builder.append('\n');
        }
    }

    private String decodePdfEscapes(String input) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current != '\\') {
                result.append(current);
                continue;
            }
            if (i == input.length() - 1) {
                break;
            }
            char next = input.charAt(++i);
            switch (next) {
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case '\\' -> result.append('\\');
                case '(' -> result.append('(');
                case ')' -> result.append(')');
                default -> {
                    if (next >= '0' && next <= '7') {
                        int end = i + 1;
                        while (end < input.length() && end <= i + 2) {
                            char c = input.charAt(end);
                            if (c < '0' || c > '7') {
                                break;
                            }
                            end++;
                        }
                        String oct = input.substring(i, end);
                        try {
                            result.append((char) Integer.parseInt(oct, 8));
                        } catch (NumberFormatException ignore) {
                            result.append(next);
                        }
                        i = end - 1;
                    } else {
                        result.append(next);
                    }
                }
            }
        }
        return result.toString();
    }

    private String extractFromDoc(byte[] bytes) {
        String utf8 = extractPrintable(new String(bytes, StandardCharsets.UTF_8));
        String gbk = extractPrintable(new String(bytes, Charset.forName("GBK")));
        return score(gbk) > score(utf8) ? gbk : utf8;
    }

    private int score(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int score = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c) || isHan(c)) {
                score++;
            }
        }
        return score;
    }

    private boolean isHan(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private String extractPrintable(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c == ' ') {
                builder.append(c);
                continue;
            }
            if (Character.isLetterOrDigit(c) || isHan(c) || isCommonPunctuation(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private boolean isCommonPunctuation(char c) {
        return ",.;:!?()[]{}<>+-*/=_~'\"#%&".indexOf(c) >= 0
            || "，。；：？！、（）【】《》“”‘’".indexOf(c) >= 0;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace('\u0000', ' ')
            .replaceAll("[\\r\\t]+", " ")
            .replaceAll("[ ]{2,}", " ")
            .replaceAll("\\s*\\n\\s*", "\n")
            .trim();
    }

    private boolean isReadable(String text) {
        return score(normalize(text)) >= MIN_READABLE_SCORE;
    }

    private boolean looksLikePdfNoise(String text) {
        String safe = text == null ? "" : text.toLowerCase(Locale.ROOT);
        int hit = 0;
        hit += count(safe, " obj");
        hit += count(safe, " endobj");
        hit += count(safe, " stream");
        hit += count(safe, " endstream");
        hit += count(safe, " xref");
        hit += count(safe, "/length");
        return hit >= 6;
    }

    private int count(String text, String token) {
        int count = 0;
        int idx = 0;
        while (idx >= 0) {
            idx = text.indexOf(token, idx);
            if (idx >= 0) {
                count++;
                idx += token.length();
            }
        }
        return count;
    }
}
