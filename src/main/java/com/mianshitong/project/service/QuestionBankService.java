package com.mianshitong.project.service;

import com.mianshitong.project.entity.po.QuestionBankFilePo;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface QuestionBankService {
    QuestionBankFilePo upload(Long userId, MultipartFile file);

    List<QuestionBankFilePo> listByUser(Long userId);

    QuestionBankFilePo getById(Long userId, Long bankFileId);

    void deleteById(Long userId, Long bankFileId);

    int batchDelete(Long userId, List<Long> bankFileIds);
}
