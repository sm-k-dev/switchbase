package com.switchbase.reports;

import com.switchbase.builds.Option;
import com.switchbase.member.MemberUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;

/** 신고 접수 (review-screens §2). 본인 대상과 중복은 거부한다. */
@Controller
public class ReportController {

    private final ReportService reportService;

    ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/reports/new")
    String newForm(@RequestParam("targetType") ReportTargetType targetType,
                   @RequestParam("targetId") Long targetId, Model model) {
        ReportForm form = new ReportForm();
        form.setTargetType(targetType);
        form.setTargetId(targetId);
        model.addAttribute("form", form);
        model.addAttribute("targetLabel", targetType.getLabel());
        addOptions(model);
        return "reports/form";
    }

    @PostMapping("/reports")
    String create(@Valid @ModelAttribute("form") ReportForm form, BindingResult binding,
                  @AuthenticationPrincipal MemberUserDetails member,
                  RedirectAttributes redirect, HttpServletResponse response, Model model) {
        if (binding.hasErrors()) {
            return renderForm(form, model);
        }
        try {
            String href = reportService.create(member.getId(), form.getTargetType(), form.getTargetId(),
                    form.getReasonCode(), form.getDetail());
            redirect.addFlashAttribute("message", "신고를 접수했어요. 관리자가 확인할게요.");
            return "redirect:" + (href == null ? "/switches" : href);
        } catch (ReportDuplicateException e) {
            binding.reject("duplicate", e.getMessage());
            response.setStatus(HttpStatus.CONFLICT.value());
        } catch (IllegalArgumentException e) {
            binding.reject("self", e.getMessage());
        }
        return renderForm(form, model);
    }

    private String renderForm(ReportForm form, Model model) {
        model.addAttribute("targetLabel", form.getTargetType() == null ? "" : form.getTargetType().getLabel());
        addOptions(model);
        return "reports/form";
    }

    private void addOptions(Model model) {
        model.addAttribute("reasonOptions", Arrays.stream(ReportReason.values())
                .map(r -> new Option(r.name(), r.getLabel())).toList());
    }
}
