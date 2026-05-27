package com.mianshitong.project.entity.po;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InterviewMessagePo {
    private String role;
    private String content;
    private LocalDateTime time;
}
