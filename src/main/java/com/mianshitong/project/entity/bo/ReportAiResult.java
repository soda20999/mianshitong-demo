package com.mianshitong.project.entity.bo;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class ReportAiResult {
    private Integer overallScore;
    private Map<String, Integer> dimensions;
    private List<String> weakPoints;
    private List<String> reviewRoadmap;
    private List<String> questionList;
    private List<String> userAnswerHighlights;
    private List<String> aiStandardAnswers;
    private List<String> brightSpots;
}
