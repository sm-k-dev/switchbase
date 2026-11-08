package com.switchbase.builds;

import com.switchbase.member.MemberUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** 빌드 목록·상세는 누구나, 등록·수정·숨김은 로그인 회원 (build-design §4, build-screens). */
@Controller
public class BuildController {

    private static final int PAGE_SIZE = 20;

    private final BuildService buildService;

    BuildController(BuildService buildService) {
        this.buildService = buildService;
    }

    // ---- 목록·상세 (누구나) ----

    @GetMapping("/builds")
    String list(@RequestParam(value = "page", defaultValue = "0") int page, Model model) {
        Page<BuildView> result = buildService.visibleBuilds(PageRequest.of(Math.max(page, 0), PAGE_SIZE))
                .map(BuildView::from);
        model.addAttribute("page", result);
        return "builds/list";
    }

    @GetMapping("/builds/{id}")
    String detail(@PathVariable Long id,
                  @AuthenticationPrincipal MemberUserDetails viewer, Model model) {
        KeyboardBuild build = buildService.view(id, viewerId(viewer));
        model.addAttribute("build", BuildView.from(build, viewerId(viewer)));
        return "builds/detail";
    }

    // ---- 등록·수정 (로그인 필요) ----

    @GetMapping("/builds/new")
    String newForm(Model model) {
        model.addAttribute("form", new BuildForm());
        addOptions(model);
        return "builds/form";
    }

    @PostMapping("/builds")
    String create(@Valid @ModelAttribute("form") BuildForm form, BindingResult binding,
                  @AuthenticationPrincipal MemberUserDetails member, Model model) {
        if (binding.hasErrors()) {
            addOptions(model);
            return "builds/form";
        }
        try {
            KeyboardBuild saved = buildService.create(member.getId(), form.toInput());
            return "redirect:/builds/" + saved.getId();
        } catch (BuildChoiceException e) {
            binding.rejectValue(fieldOf(e.getPart()), "invalid", e.getMessage());
        } catch (BuildLimitExceededException e) {
            binding.reject("limit", e.getMessage());
        }
        addOptions(model);
        return "builds/form";
    }

    @GetMapping("/builds/{id}/edit")
    String editForm(@PathVariable Long id,
                    @AuthenticationPrincipal MemberUserDetails viewer, Model model) {
        KeyboardBuild build = buildService.view(id, viewerId(viewer));
        model.addAttribute("form", BuildForm.from(build));
        model.addAttribute("buildId", id);
        addOptions(model);
        return "builds/form";
    }

    @PostMapping("/builds/{id}")
    String update(@PathVariable Long id, @Valid @ModelAttribute("form") BuildForm form, BindingResult binding,
                  @AuthenticationPrincipal MemberUserDetails viewer,
                  RedirectAttributes redirect, HttpServletResponse response, Model model) {
        if (binding.hasErrors()) {
            return rerenderForm(id, model);
        }
        try {
            buildService.update(id, viewerId(viewer), form.getVersion(), form.toInput());
        } catch (BuildChoiceException e) {
            binding.rejectValue(fieldOf(e.getPart()), "invalid", e.getMessage());
            return rerenderForm(id, model);
        } catch (OptimisticLockingFailureException e) {
            binding.reject("conflict", "그 사이 다른 사람이 먼저 고쳤어요. 새로고침 후 다시 해 주세요.");
            response.setStatus(HttpStatus.CONFLICT.value());
            return rerenderForm(id, model);
        }
        redirect.addFlashAttribute("message", "저장했어요.");
        return "redirect:/builds/" + id;
    }

    // ---- 숨김·숨김 해제 (로그인 필요, 데이터는 지우지 않음) ----

    /** 숨긴 뒤에는 목록으로 간다(숨긴 빌드의 상세는 작성자 외에게 보이지 않으므로). */
    @PostMapping("/builds/{id}/hide")
    String hide(@PathVariable Long id, RedirectAttributes redirect) {
        buildService.setHidden(id, true);
        redirect.addFlashAttribute("message", "숨겼어요.");
        return "redirect:/builds";
    }

    @PostMapping("/builds/{id}/unhide")
    String unhide(@PathVariable Long id,
                  @AuthenticationPrincipal MemberUserDetails viewer, RedirectAttributes redirect) {
        buildService.unhide(id, viewerId(viewer));
        redirect.addFlashAttribute("message", "다시 공개했어요.");
        return "redirect:/builds/" + id;
    }

    // ---- 내부 ----

    private static Long viewerId(MemberUserDetails viewer) {
        return viewer == null ? null : viewer.getId();
    }

    private String rerenderForm(Long id, Model model) {
        model.addAttribute("buildId", id);
        addOptions(model);
        return "builds/form";
    }

    private void addOptions(Model model) {
        model.addAttribute("housingOptions", options(BuildPart.HOUSING));
        model.addAttribute("plateOptions", options(BuildPart.PLATE));
        model.addAttribute("mountOptions", options(BuildPart.MOUNT));
        model.addAttribute("keycapOptions", options(BuildPart.KEYCAP));
    }

    private static List<Option> options(BuildPart part) {
        return part.choices().stream().map(c -> new Option(c.code(), c.label())).toList();
    }

    private static String fieldOf(BuildPart part) {
        return switch (part) {
            case HOUSING -> "housingMaterial";
            case PLATE -> "plateMaterial";
            case MOUNT -> "mountType";
            case KEYCAP -> "keycapMaterial";
        };
    }
}
