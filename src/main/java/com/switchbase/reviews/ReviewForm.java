package com.switchbase.reviews;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/** 후기 폼 (review-screens §3). recommendedFor는 RecommendFor 코드 목록. */
public class ReviewForm {

    private Long version;

    private List<String> recommendedFor = new ArrayList<>();

    @NotBlank(message = "후기 내용을 적어 주세요.")
    @Size(max = 2000, message = "후기는 2000자 이하로 적어 주세요.")
    private String body;

    public static ReviewForm from(Review r) {
        ReviewForm f = new ReviewForm();
        f.version = r.getVersion();
        f.recommendedFor = r.recommendations().stream().map(Enum::name).toList();
        f.body = r.getBody();
        return f;
    }

    /** 선택지에 없는 코드는 IllegalArgumentException. */
    public List<RecommendFor> recommendations() {
        List<RecommendFor> result = new ArrayList<>();
        if (recommendedFor != null) {
            for (String code : recommendedFor) {
                if (code != null && !code.isBlank()) {
                    result.add(RecommendFor.valueOf(code.strip()));
                }
            }
        }
        return result;
    }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public List<String> getRecommendedFor() { return recommendedFor; }
    public void setRecommendedFor(List<String> recommendedFor) { this.recommendedFor = recommendedFor; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
