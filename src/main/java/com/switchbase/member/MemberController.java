package com.switchbase.member;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/login")
    String loginForm(Principal principal) {
        return principal != null ? "redirect:/switches" : "member/login";
    }

    @GetMapping("/signup")
    String signupForm(Principal principal, Model model) {
        if (principal != null) {
            return "redirect:/switches";
        }
        model.addAttribute("signupForm", new SignupForm());
        return "member/signup";
    }

    @PostMapping("/signup")
    String signup(@Valid @ModelAttribute("signupForm") SignupForm form, BindingResult binding, Model model) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getPasswordConfirm())) {
            binding.rejectValue("passwordConfirm", "mismatch", "비밀번호가 서로 다릅니다.");
        }
        if (binding.hasErrors()) {
            return "member/signup";
        }
        try {
            memberService.signup(form);
        } catch (DuplicateMemberException e) {
            binding.rejectValue(e.getField(), "duplicate", e.getMessage());
            return "member/signup";
        } catch (SignupBlockedException e) {
            binding.rejectValue("email", "blocked", e.getMessage());
            return "member/signup";
        }
        model.addAttribute("email", Member.normalizeEmail(form.getEmail()));
        return "member/signup-done";
    }

    @GetMapping("/verify")
    String verify(@RequestParam(value = "token", required = false) String token, Model model) {
        model.addAttribute("success", memberService.verify(token));
        return "member/verify-result";
    }

    /** 존재 여부와 상관없이 같은 화면을 보여준다. */
    @PostMapping("/signup/resend")
    String resend(@RequestParam("email") String email, Model model) {
        memberService.resendVerification(email);
        return "member/signup-done";
    }
}
