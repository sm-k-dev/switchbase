package com.switchbase.member;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** 회원 탈퇴 확인과 처리 (review-screens §2). confirmText가 "탈퇴"일 때만 처리한다. */
@Controller
public class WithdrawController {

    static final String CONFIRM_WORD = "탈퇴";

    private final WithdrawalService withdrawalService;

    WithdrawController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @GetMapping("/me/withdraw")
    String form(Model model) {
        model.addAttribute("form", new WithdrawForm());
        return "member/withdraw";
    }

    @PostMapping("/me/withdraw")
    String withdraw(@ModelAttribute("form") WithdrawForm form, BindingResult binding,
                    @AuthenticationPrincipal MemberUserDetails member,
                    HttpServletRequest request, HttpServletResponse response) {
        String typed = form.getConfirmText() == null ? "" : form.getConfirmText().strip();
        if (!CONFIRM_WORD.equals(typed)) {
            binding.rejectValue("confirmText", "mismatch", "확인 문구를 정확히 적어 주세요.");
            return "member/withdraw";
        }
        withdrawalService.withdraw(member.getId());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, auth);
        return "redirect:/";
    }
}
