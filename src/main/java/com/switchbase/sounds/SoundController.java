package com.switchbase.sounds;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 타건음 샘플 등록 화면 (sound-compare-design §5.1). 목록·비교·필터는 다음 단계. */
@Controller
public class SoundController {

    private final SoundService soundService;

    SoundController(SoundService soundService) {
        this.soundService = soundService;
    }

    @GetMapping("/switches/{switchId}/sounds/new")
    String newForm(@PathVariable Long switchId, Model model) {
        model.addAttribute("form", new SoundForm());
        model.addAttribute("switchId", switchId);
        model.addAttribute("conditionOptions", SoundCondition.values());
        return "sounds/form";
    }

    @PostMapping("/switches/{switchId}/sounds")
    String create(@PathVariable Long switchId,
                  @Valid @ModelAttribute("form") SoundForm form, BindingResult binding,
                  @AuthenticationPrincipal MemberUserDetails member,
                  RedirectAttributes redirect, HttpServletResponse response, Model model) {
        if (binding.hasErrors()) {
            return renderNew(switchId, model);
        }
        try {
            soundService.register(switchId, member.getId(), form.getYoutube(), form.getStartTime(),
                    form.getChannelName(), form.conditionSet());
        } catch (SoundDuplicateException e) {
            binding.reject("duplicate", e.getMessage());
            response.setStatus(HttpStatus.CONFLICT.value());
            return renderNew(switchId, model);
        } catch (IllegalArgumentException e) {
            binding.reject("invalid", e.getMessage());
            return renderNew(switchId, model);
        }
        redirect.addFlashAttribute("message", "샘플을 올렸어요.");
        return "redirect:/switches/" + switchId;
    }

    private String renderNew(Long switchId, Model model) {
        model.addAttribute("switchId", switchId);
        model.addAttribute("conditionOptions", SoundCondition.values());
        return "sounds/form";
    }
}
