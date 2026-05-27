package com.mianshitong.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mianshitong.project.common.exception.BizException;
import com.mianshitong.project.entity.po.QuestionBankFilePo;
import com.mianshitong.project.entity.po.UserPo;
import com.mianshitong.project.mapper.QuestionBankFileMapper;
import com.mianshitong.project.mapper.UserMapper;
import com.mianshitong.project.service.HotQuestionService;
import com.mianshitong.project.service.QuestionBankService;
import com.mianshitong.project.util.ResumeDocumentExtractor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class QuestionBankServiceImpl implements QuestionBankService {

    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    private final QuestionBankFileMapper questionBankFileMapper;
    private final ResumeDocumentExtractor resumeDocumentExtractor;
    private final HotQuestionService hotQuestionService;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionBankFilePo upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请先选择题库文件");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BizException("题库文件不能超过 10MB");
        }

        String fileName = resolveFileName(file.getOriginalFilename());
        String fileType = resolveFileType(fileName);
        validateFileSignature(file, fileType);
        String content = extractContent(file, fileName, fileType);
        if (content.isBlank()) {
            throw new BizException("题库内容为空或暂时无法解析，请确认文件内容是否可读");
        }

        QuestionBankFilePo bankFile = new QuestionBankFilePo();
        bankFile.setUserId(userId);
        bankFile.setFileName(fileName);
        bankFile.setFileType(fileType);
        bankFile.setContent(content);
        bankFile.setUploadedAt(LocalDateTime.now());
        questionBankFileMapper.insert(bankFile);
        hotQuestionService.rebuildFromQuestionBank(
            userId,
            bankFile.getId(),
            fileName,
            content,
            resolveDefaultPosition(userId)
        );
        return bankFile;
    }

    @Override
    public List<QuestionBankFilePo> listByUser(Long userId) {
        return questionBankFileMapper.selectList(
            new LambdaQueryWrapper<QuestionBankFilePo>()
                .eq(QuestionBankFilePo::getUserId, userId)
                .orderByDesc(QuestionBankFilePo::getUploadedAt)
        );
    }

    @Override
    public QuestionBankFilePo getById(Long userId, Long bankFileId) {
        QuestionBankFilePo bankFile = questionBankFileMapper.selectOne(
            new LambdaQueryWrapper<QuestionBankFilePo>()
                .eq(QuestionBankFilePo::getId, bankFileId)
                .eq(QuestionBankFilePo::getUserId, userId)
                .last("LIMIT 1")
        );
        if (bankFile == null) {
            throw new BizException("题库文件不存在");
        }
        return bankFile;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long userId, Long bankFileId) {
        QuestionBankFilePo existing = getById(userId, bankFileId);
        hotQuestionService.removeByQuestionBank(userId, existing.getId());
        int affected = questionBankFileMapper.delete(
            new LambdaQueryWrapper<QuestionBankFilePo>()
                .eq(QuestionBankFilePo::getId, bankFileId)
                .eq(QuestionBankFilePo::getUserId, userId)
        );
        if (affected == 0) {
            throw new BizException("题库文件不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(Long userId, List<Long> bankFileIds) {
        if (bankFileIds == null || bankFileIds.isEmpty()) {
            throw new BizException("请先选择要删除的题库");
        }
        List<Long> ids = bankFileIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (ids.isEmpty()) {
            throw new BizException("请先选择要删除的题库");
        }
        List<Long> ownedIds = questionBankFileMapper.selectList(
            new LambdaQueryWrapper<QuestionBankFilePo>()
                .select(QuestionBankFilePo::getId)
                .eq(QuestionBankFilePo::getUserId, userId)
                .in(QuestionBankFilePo::getId, ids)
        ).stream().map(QuestionBankFilePo::getId).toList();
        if (ownedIds.isEmpty()) {
            throw new BizException("未删除任何题库文件");
        }
        hotQuestionService.removeByQuestionBanks(userId, ownedIds);
        int affected = questionBankFileMapper.delete(
            new LambdaQueryWrapper<QuestionBankFilePo>()
                .eq(QuestionBankFilePo::getUserId, userId)
                .in(QuestionBankFilePo::getId, ownedIds)
        );
        if (affected == 0) {
            throw new BizException("未删除任何题库文件");
        }
        return affected;
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

    private String resolveFileType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        if (lower.endsWith(".docx")) {
            return "docx";
        }
        if (lower.endsWith(".doc")) {
            return "doc";
        }
        if (lower.endsWith(".markdown")) {
            return "markdown";
        }
        if (lower.endsWith(".md")) {
            return "md";
        }
        throw new BizException("仅支持 PDF / DOC / DOCX / MD / MARKDOWN 题库");
    }

    private void validateFileSignature(MultipartFile file, String fileType) {
        if ("md".equals(fileType) || "markdown".equals(fileType)) {
            return;
        }
        byte[] header = readHeader(file, 8);
        if ("pdf".equals(fileType)) {
            if (!startsWith(header, new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D})) {
                throw new BizException("PDF 文件头校验失败");
            }
            return;
        }
        if ("docx".equals(fileType)) {
            if (!startsWith(header, new byte[] {0x50, 0x4B, 0x03, 0x04})) {
                throw new BizException("DOCX 文件头校验失败");
            }
            return;
        }
        if ("doc".equals(fileType) && !startsWith(
            header,
            new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}
        )) {
            throw new BizException("DOC 文件头校验失败");
        }
    }

    private byte[] readHeader(MultipartFile file, int size) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[size];
            int read = inputStream.read(header);
            if (read <= 0) {
                return new byte[0];
            }
            if (read == size) {
                return header;
            }
            byte[] actual = new byte[read];
            System.arraycopy(header, 0, actual, 0, read);
            return actual;
        } catch (IOException ex) {
            throw new BizException("读取文件头失败");
        }
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

    private String extractContent(MultipartFile file, String fileName, String fileType) {
        if ("md".equals(fileType) || "markdown".equals(fileType)) {
            return extractMarkdown(file);
        }
        return resumeDocumentExtractor.extract(file, fileName);
    }

    private String extractMarkdown(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String utf8 = normalize(new String(bytes, StandardCharsets.UTF_8));
            String gbk = normalize(new String(bytes, Charset.forName("GBK")));
            return readableScore(gbk) > readableScore(utf8) ? gbk : utf8;
        } catch (Exception ex) {
            throw new BizException("读取题库文件失败");
        }
    }

    private int readableScore(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int score = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c) || isHan(c) || isCommonPunctuation(c)) {
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

    private boolean isCommonPunctuation(char c) {
        return ",.;:!?()[]{}<>+-*/=_~'\"#%&|@$`".indexOf(c) >= 0
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

    private String resolveDefaultPosition(Long userId) {
        if (userId == null) {
            return "";
        }
        UserPo user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getTargetPosition())) {
            return "";
        }
        return user.getTargetPosition().trim();
    }
}
