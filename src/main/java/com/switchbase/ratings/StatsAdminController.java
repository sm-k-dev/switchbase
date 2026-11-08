package com.switchbase.ratings;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 관리용 전체 재계산 (stats-design Q3). 관리자 전용(/admin/**). 주기 실행은 없다.
 * 노출: POST /admin/stats/recompute (CSRF 포함 폼 버튼 또는 curl). 멱등이다.
 */
@Controller
public class StatsAdminController {

    private final RatingStatsService stats;

    StatsAdminController(RatingStatsService stats) {
        this.stats = stats;
    }

    @PostMapping("/admin/stats/recompute")
    String recomputeAll(RedirectAttributes redirect) {
        int count = stats.recomputeAll();
        redirect.addFlashAttribute("message", "집계를 다시 계산했어요 (" + count + "개 스위치).");
        return "redirect:/admin/switches/import";
    }
}
