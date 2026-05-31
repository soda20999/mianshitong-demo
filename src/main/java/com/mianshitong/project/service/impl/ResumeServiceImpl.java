package com.mianshitong.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mianshitong.project.common.exception.BizException;
import com.mianshitong.project.entity.bo.AiCallResult;
import com.mianshitong.project.entity.po.ResumePo;
import com.mianshitong.project.entity.vo.ResumeParseResultVo;
import com.mianshitong.project.mapper.ResumeMapper;
import com.mianshitong.project.service.ResumeService;
import com.mianshitong.project.util.ResumeDocumentExtractor;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final int MAX_CONTENT_LENGTH = 20000;
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?i)^v(\\d+)$");

    private final ResumeMapper resumeMapper;
    private final SpringAiEngine springAiEngine;
    private final ResumeDocumentExtractor resumeDocumentExtractor;
    private final AiLogSupport aiLogSupport;
    private final AiRateLimitSupport aiRateLimitSupport;

    @Override
    public ResumePo upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请先选择简历文件");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BizException("简历文件不能超过 10MB");
        }
        String fileName = resolveFileName(file.getOriginalFilename());
        validateFileType(fileName);

        byte[] bytes = readBytes(file);
        validateFileSignature(bytes, fileName);
        String fileSha256 = sha256(bytes);
        ResumePo cachedResume = findParsedByFingerprint(fileSha256, null);

        ResumePo resume = new ResumePo();
        resume.setUserId(userId);
        resume.setFileName(fileName);
        resume.setVersion(nextVersion(userId));
        resume.setFileSha256(fileSha256);
        resume.setUploadedAt(LocalDateTime.now());

        if (cachedResume != null) {
            resume.setContent(cachedResume.getContent());
            resume.setParseResult(cachedResume.getParseResult());
            resume.setCacheHit(true);
            resumeMapper.insert(resume);
            return resume;
        }

        String content = resumeDocumentExtractor.extract(bytes, fileName);
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }
        resume.setContent(content);
        resume.setCacheHit(false);

        aiRateLimitSupport.checkPerMinuteLimit(userId);
        AiCallResult<ResumeParseResultVo> aiResult = springAiEngine.parseResume(content);
        resume.setParseResult(aiResult.data());
        resumeMapper.insert(resume);

        aiLogSupport.log(userId, "resume", aiResult.usage(), "SUCCESS");
        return resume;
    }

    @Override
    public List<ResumePo> listByUser(Long userId) {
        return resumeMapper.selectList(
            new LambdaQueryWrapper<ResumePo>()
                .eq(ResumePo::getUserId, userId)
                .orderByDesc(ResumePo::getUploadedAt)
        );
    }

    @Override
    public ResumePo parse(Long userId, Long resumeId) {
        ResumePo resume = requireOwned(userId, resumeId);
        ResumePo cachedResume = findParsedByFingerprint(resume.getFileSha256(), resume.getId());
        if (cachedResume != null) {
            resume.setContent(cachedResume.getContent());
            resume.setParseResult(cachedResume.getParseResult());
            resume.setCacheHit(true);
            resumeMapper.updateById(resume);
            return resume;
        }

        aiRateLimitSupport.checkPerMinuteLimit(userId);
        AiCallResult<ResumeParseResultVo> aiResult = springAiEngine.parseResume(resume.getContent());
        resume.setParseResult(aiResult.data());
        resume.setCacheHit(false);
        resumeMapper.updateById(resume);
        aiLogSupport.log(userId, "resume", aiResult.usage(), "SUCCESS");
        return resume;
    }

    @Override
    public List<ResumePo> listAll() {
        return resumeMapper.selectList(
            new LambdaQueryWrapper<ResumePo>()
                .orderByDesc(ResumePo::getUploadedAt)
        );
    }

    private String nextVersion(Long userId) {
        int max = listByUser(userId).stream()
            .map(ResumePo::getVersion)
            .mapToInt(this::versionNumber)
            .max()
            .orElse(0);
        return "v" + (max + 1);
    }

    private int versionNumber(String version) {
        if (version == null) {
            return 0;
        }
        Matcher matcher = VERSION_PATTERN.matcher(version.trim());
        if (!matcher.matches()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    private void validateFileType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        boolean matched = lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx");
        if (!matched) {
            throw new BizException("仅支持 PDF / DOC / DOCX 简历");
        }
    }

    private void validateFileSignature(byte[] bytes, String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        byte[] header = readHeader(bytes, 8);
        if (lower.endsWith(".pdf")) {
            if (!startsWith(header, new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D})) {
                throw new BizException("PDF 文件头校验失败");
            }
            return;
        }
        if (lower.endsWith(".docx")) {
            if (!startsWith(header, new byte[] {0x50, 0x4B, 0x03, 0x04})) {
                throw new BizException("DOCX 文件头校验失败");
            }
            return;
        }
        if (lower.endsWith(".doc") && !startsWith(
            header,
            new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}
        )) {
            throw new BizException("DOC 文件头校验失败");
        }
    }

    private byte[] readHeader(byte[] bytes, int size) {
        if (bytes == null || bytes.length == 0) {
            return new byte[0];
        }
        int read = Math.min(bytes.length, size);
        byte[] header = new byte[read];
        System.arraycopy(bytes, 0, header, 0, read);
        return header;
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source == null || prefix == null || source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private String resolveFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BizException("文件名不能为空");
        }
        String safeName = originalFilename.replace("\\", "/");
        safeName = safeName.substring(safeName.lastIndexOf("/") + 1).trim();
        if (safeName.isBlank()) {
            throw new BizException("文件名不能为空");
        }
        return safeName;
    }

    private ResumePo requireOwned(Long userId, Long resumeId) {
        ResumePo resume = resumeMapper.selectOne(
            new LambdaQueryWrapper<ResumePo>()
                .eq(ResumePo::getId, resumeId)
                .eq(ResumePo::getUserId, userId)
                .last("LIMIT 1")
        );
        if (resume == null) {
            throw new BizException("简历不存在");
        }
        return resume;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BizException("读取简历文件失败");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException("文件指纹计算失败");
        }
    }

    private ResumePo findParsedByFingerprint(String fileSha256, Long excludeId) {
        if (fileSha256 == null || fileSha256.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<ResumePo> wrapper = new LambdaQueryWrapper<ResumePo>()
            .eq(ResumePo::getFileSha256, fileSha256)
            .isNotNull(ResumePo::getParseResult)
            .orderByDesc(ResumePo::getUploadedAt);
        if (excludeId != null) {
            wrapper.ne(ResumePo::getId, excludeId);
        }
        wrapper.last("LIMIT 1");
        return resumeMapper.selectOne(wrapper);
    }
}
