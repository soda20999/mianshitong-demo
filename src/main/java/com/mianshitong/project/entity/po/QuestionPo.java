package com.mianshitong.project.entity.po;

import lombok.Data;

@Data
public class QuestionPo {
    private Long id;
    private String category;
    private String direction;
    private String level;
    private String content;
}
