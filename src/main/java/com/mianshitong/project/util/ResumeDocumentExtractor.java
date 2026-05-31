package com.mianshitong.project.util;

import com.mianshitong.project.common.exception.BizException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

@Component
public class ResumeDocumentExtractor {

    private static final int TIKA_WRITE_LIMIT = 200000;

    private final AutoDetectParser parser = new AutoDetectParser();
    private final Tika tika = new Tika();

    public String extract(MultipartFile file, String fileName) {
        Objects.requireNonNull(file, "file");
        String text = extract(readBytes(file), fileName);
        if (text.isBlank()) {
            throw new BizException("文档内容为空或暂时无法解析，请确认简历内容是否可读取");
        }
        return text;
    }

    public String extract(byte[] bytes, String fileName) {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, fileName);
        BodyContentHandler handler = new BodyContentHandler(TIKA_WRITE_LIMIT);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            parser.parse(inputStream, handler, metadata);
        } catch (IOException | TikaException | SAXException ex) {
            throw new BizException("文档解析失败，请上传可读取的 PDF / DOC / DOCX 文件");
        }
        return normalize(handler.toString());
    }

    public String detectMediaType(byte[] bytes, String fileName) {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, fileName);
        return tika.detect(bytes, metadata);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BizException("读取简历文件失败");
        }
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
}
