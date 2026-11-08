package com.switchbase.ratings;

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
import java.util.List;

/** 체감 평가 등록·수정·삭제 (rating-screens §1). 목록과 요약은 상세 화면(SwitchController)이 보여 준다. */
@Controller
public class RatingController {

    private final RatingService ratingService;

    RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @GetMapping("/switches/{switchId}/ratings/new")
    String newForm(@PathVariable Long switchId,
                   @AuthenticationPrincipal MemberUserDetails member, Model model) {
        model.addAttribute("form", new RatingForm());
        renderForm(member.getId(), switchId, null, model);
        return "ratings/form";
    }

    @PostMapping("/switches/{switchId}/ratings")
    String create(@PathVariable Long switchId,
                  @Valid @ModelAttribute("form") RatingForm form, BindingResult binding,
                  @AuthenticationPrincipal MemberUserDetails member,
                  RedirectAttributes redirect, HttpServletResponse response, Model model) {
        if (binding.hasErrors()) {
            renderForm(member.getId(), switchId, null, model);
            return "ratings/form";
        }
        try {
            ratingService.create(switchId, member.getId(), form.toInput());
        } catch (OptimisticLockingFailureException e) {
            // 개조 태그를 다른 요청과 동시에 만든 경우
            binding.reject("conflict", "그 사이 다른 사람이 먼저 고쳤어요. 새로고침 후 다시 해 주세요.");
            response.setStatus(HttpStatus.CONFLICT.value());
            renderForm(member.getId(), switchId, null, model);
            return "ratings/form";
        } catch (BuildNotOwnedException e) {
            binding.rejectValue("buildId", "notOwned", e.getMessage());
            renderForm(member.getId(), switchId, null, model);
            return "ratings/form";
        } catch (RatingDuplicateException e) {
            binding.reject("duplicate", e.getMessage());
            response.setStatus(HttpStatus.CONFLICT.value());
            renderForm(member.getId(), switchId, null, model);
            return "ratings/form";
        } catch (IllegalArgumentException e) {
            binding.reject("invalid", e.getMessage());
            renderForm(member.getId(), switchId, null, model);
            return "ratings/form";
        }
        redirect.addFlashAttribute("message", "평가를 남겼어요.");
        return "redirect:/switches/" + switchId;
    }

    /** 작성자가 아니면 403(rating-screens §1). */
    @GetMapping("/ratings/{id}/edit")
    String editForm(@PathVariable Long id,
                    @AuthenticationPrincipal MemberUserDetails member, Model model) {
        Long switchId = ratingService.loadForEdit(id, member.getId()).getKeyboardSwitch().getId();
        model.addAttribute("form", ratingService.formForEdit(id, member.getId()));
        renderForm(member.getId(), switchId, id, model);
        return "ratings/form";
    }

    @PostMapping("/ratings/{id}")
    String update(@PathVariable Long id,
                  @Valid @ModelAttribute("form") RatingForm form, BindingResult binding,
                  @AuthenticationPrincipal MemberUserDetails member,
                  RedirectAttributes redirect, HttpServletResponse response, Model model) {
        Long switchId = ratingService.loadForEdit(id, member.getId()).getKeyboardSwitch().getId();
        if (binding.hasErrors()) {
            renderForm(member.getId(), switchId, id, model);
            return "ratings/form";
        }
        try {
            ratingService.update(id, member.getId(), form.getVersion(), form.toInput());
        } catch (OptimisticLockingFailureException e) {
            binding.reject("conflict", "그 사이 다른 사람이 먼저 고쳤어요. 새로고침 후 다시 해 주세요.");
            response.setStatus(HttpStatus.CONFLICT.value());
            renderForm(member.getId(), switchId, id, model);
            return "ratings/form";
        } catch (BuildNotOwnedException e) {
            binding.rejectValue("buildId", "notOwned", e.getMessage());
            renderForm(member.getId(), switchId, id, model);
            return "ratings/form";
        } catch (RatingDuplicateException e) {
            binding.reject("duplicate", e.getMessage());
            response.setStatus(HttpStatus.CONFLICT.value());
            renderForm(member.getId(), switchId, id, model);
            return "ratings/form";
        } catch (IllegalArgumentException e) {
            binding.reject("invalid", e.getMessage());
            renderForm(member.getId(), switchId, id, model);
            return "ratings/form";
        }
        redirect.addFlashAttribute("message", "평가를 고쳤어요.");
        return "redirect:/switches/" + switchId;
    }

    /** 작성자 또는 관리자만. 제3자는 403(RatingAccessDeniedException, 서비스에서 검사). */
    @PostMapping("/ratings/{id}/delete")
    String delete(@PathVariable Long id,
                  @AuthenticationPrincipal MemberUserDetails member, RedirectAttributes redirect) {
        Long switchId = ratingService.delete(id, member.getId(), member.isAdmin());
        redirect.addFlashAttribute("message", "평가를 삭제했어요.");
        return "redirect:/switches/" + switchId;
    }

    /** 평가 숨김 해제. 작성자만(그 외 404). */
    @PostMapping("/ratings/{id}/unhide")
    String unhide(@PathVariable Long id, @AuthenticationPrincipal MemberUserDetails member,
                  RedirectAttributes redirect) {
        Long switchId = ratingService.unhide(id, member.getId());
        redirect.addFlashAttribute("message", "평가 숨김을 풀었어요.");
        return "redirect:/switches/" + switchId;
    }

    // ---- 내부 ----

    private void renderForm(Long memberId, Long switchId, Long ratingId, Model model) {
        model.addAttribute("switchId", switchId);
        model.addAttribute("ratingId", ratingId);
        model.addAttribute("buildOptions", ratingService.buildOptionsFor(memberId));
        model.addAttribute("soundToneOptions", options(SoundTone.values(), SoundTone::getLabel));
        model.addAttribute("feelOptions", options(Feel.values(), Feel::getLabel));
    }

    private static <E extends Enum<E>> List<Option> options(E[] values, java.util.function.Function<E, String> label) {
        return Arrays.stream(values).map(v -> new Option(v.name(), label.apply(v))).toList();
    }
}
