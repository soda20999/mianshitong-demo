package com.mianshitong.project.entity.vo;

import java.util.List;
import lombok.Data;

@Data
public class ResumeParseResultVo {
    private List<String> skills;
    private List<String> projects;
    private List<String> education;
    private List<String> risks;
    private List<String> deepDivePoints;
    private List<String> highlights;
}
