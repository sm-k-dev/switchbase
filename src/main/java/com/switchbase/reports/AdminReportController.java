package com.switchbase.reports;

import com.switchbase.member.MemberUserDetails;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 관리자 신고 목록과 처리 (review-screens §2). /admin/** 는 ADMIN만. */
@Controller
public class AdminReportController {

    private static final int PAGE_SIZE = 20;

    private final ReportService reportService;

    AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/admin/reports")
    String list(@RequestParam(value = "status", defaultValue = "RECEIVED") String status,
                @RequestParam(value = "page", defaultValue = "0") int page, Model model) {
        ReportStatus filter = ReportStatus.valueOf(status);
        model.addAttribute("status", filter.name());
        model.addAttribute("page", reportService.list(filter, PageRequest.of(Math.max(page, 0), PAGE_SIZE)));
        return "admin/reports";
    }

    @PostMapping("/admin/reports/{id}/hide")
    String hide(@PathVariable Long id, @AuthenticationPrincipal MemberUserDetails admin, RedirectAttributes redirect) {
        try {
            reportService.resolve(id, admin.getId(), true);
            redirect.addFlashAttribute("message", "숨김 처리했어요.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/admin/reports";
    }

    @PostMapping("/admin/reports/{id}/dismiss")
    String dismiss(@PathVariable Long id, @AuthenticationPrincipal MemberUserDetails admin, RedirectAttributes redirect) {
        reportService.resolve(id, admin.getId(), false);
        redirect.addFlashAttribute("message", "기각했어요.");
        return "redirect:/admin/reports";
    }
}
