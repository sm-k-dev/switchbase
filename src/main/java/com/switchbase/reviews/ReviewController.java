package com.switchbase.reviews;

import com.switchbase.builds.Option;
import com.switchbase.member.MemberUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
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

import java.util.Arrays;

/** 후기 쓰기·수정·삭제·도움됨·숨김 해제 (review-screens §2). */
@Controller
public class ReviewController {

    private final ReviewService reviewService;

    ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/switches/{switchId}/reviews/new")
    String newForm(@PathVariable Long switchId, Model model) {
        model.addAttribute("form", new ReviewForm());
        model.addAttribute("switchId", switchId);
        addOptions(model);
        return "reviews/form";
    }

    @PostMapping("/switches/{switchId}/reviews")
    String create(@PathVariable Long switchId,
                  @Valid @ModelAttribute("form") ReviewForm form, BindingResult binding,
                  @AuthenticationPrincipal MemberUserDetails member,
                  RedirectAttributes redirect, HttpServletResponse response, Model model) {
        if (binding.hasErrors()) {
            return renderNew(switchId, model);
        }
        try {
            reviewService.create(switchId, member.getId(), form.getBody(), form.recommendations());
        } catch (ReviewDuplicateException e) {
            binding.reject("duplicate", e.getMessage());
            response.setStatus(HttpStatus.CONFLICT.value());
            return renderNew(switchId, model);
        } catch (IllegalArgumentException e) {
            binding.reject("invalid", "추천 대상을 다시 골라 주세요.");
            return renderNew(switchId, model);
        }
        redirect.addFlashAttribute("message", "후기를 남겼어요.");
        return "redirect:/switches/" + switchId;
    }

    @GetMapping("/reviews/{id}/edit")
    String editForm(@PathVariable Long id, @AuthenticationPrincipal MemberUserDetails member, Model model) {
        var review = reviewService.loadForEdit(id, member.getId());
        model.addAttribute("form", ReviewForm.from(review));
        model.addAttribute("reviewId", id);
        model.addAttribute("switchId", review.getKeyboardSwitch().getId());
        addOptions(model);
        return "reviews/form";
    }

    @PostMapping("/reviews/{id}")
    String update(@PathVariable Long id,
                  @Valid @ModelAttribute("form") ReviewForm form, BindingResult binding,
                  @AuthenticationPrincipal MemberUserDetails member,
                  RedirectAttributes redirect, HttpServletResponse response, Model model) {
        Long switchId = reviewService.loadForEdit(id, member.getId()).getKeyboardSwitch().getId();
        if (!binding.hasErrors()) {
            try {
                reviewService.update(id, member.getId(), form.getVersion(), form.getBody(), form.recommendations());
                redirect.addFlashAttribute("message", "후기를 고쳤어요.");
                return "redirect:/switches/" + switchId;
            } catch (OptimisticLockingFailureException e) {
                binding.reject("conflict", "그 사이 다른 사람이 먼저 고쳤어요. 새로고침 후 다시 해 주세요.");
                response.setStatus(HttpStatus.CONFLICT.value());
            } catch (IllegalArgumentException e) {
                binding.reject("invalid", "추천 대상을 다시 골라 주세요.");
            }
        }
        model.addAttribute("reviewId", id);
        model.addAttribute("switchId", switchId);
        addOptions(model);
        return "reviews/form";
    }

    @PostMapping("/reviews/{id}/delete")
    String delete(@PathVariable Long id, @AuthenticationPrincipal MemberUserDetails member,
                  RedirectAttributes redirect) {
        Long switchId = reviewService.delete(id, member.getId(), member.isAdmin());
        redirect.addFlashAttribute("message", "후기를 삭제했어요.");
        return "redirect:/switches/" + switchId;
    }

    @PostMapping("/reviews/{id}/helpful")
    String helpful(@PathVariable Long id, @AuthenticationPrincipal MemberUserDetails member,
                   RedirectAttributes redirect) {
        try {
            Long switchId = reviewService.toggleHelpful(id, member.getId());
            return "redirect:/switches/" + switchId;
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("message", e.getMessage());
            Long switchId = reviewService.load(id).getKeyboardSwitch().getId();
            return "redirect:/switches/" + switchId;
        }
    }

    @PostMapping("/reviews/{id}/unhide")
    String unhide(@PathVariable Long id, @AuthenticationPrincipal MemberUserDetails member,
                  RedirectAttributes redirect) {
        Long switchId = reviewService.unhide(id, member.getId());
        redirect.addFlashAttribute("message", "숨김을 풀었어요.");
        return "redirect:/switches/" + switchId;
    }

    private String renderNew(Long switchId, Model model) {
        model.addAttribute("switchId", switchId);
        addOptions(model);
        return "reviews/form";
    }

    private void addOptions(Model model) {
        model.addAttribute("recommendOptions", Arrays.stream(RecommendFor.values())
                .map(r -> new Option(r.name(), r.getLabel())).toList());
    }
}
