package com.mianshitong.project.service;

import com.mianshitong.project.entity.po.ResumePo;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ResumeService {
    ResumePo upload(Long userId, MultipartFile file);

    List<ResumePo> listByUser(Long userId);

    ResumePo parse(Long userId, Long resumeId);

    List<ResumePo> listAll();
}
