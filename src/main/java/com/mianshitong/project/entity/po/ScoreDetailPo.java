package com.mianshitong.project.entity.po;

import java.util.List;
import lombok.Data;

@Data
public class ScoreDetailPo {
    private Integer correctness;
    private Integer completeness;
    private Integer logic;
    private Integer expression;
    private Integer depth;
    private Integer total;
    private List<String> advantages;
    private List<String> gaps;
    private String suggestion;
    private String recommendedAnswer;
}
