package com.switchbase.keyswitch;

import com.switchbase.member.MemberUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** 로그인 회원의 수정, 이력·비교, 되돌리기, 최근 변경 화면 (wiki-design §5, wiki-screens). */
@Controller
public class WikiController {

    private static final int CHANGES_PAGE_SIZE = 20;

    private final SwitchService switchService;

    WikiController(SwitchService switchService) {
        this.switchService = switchService;
    }

    /** 수정 폼(공용 조각)의 종류 선택 옵션. 충돌 재렌더에서도 필요하다. */
    @ModelAttribute("types")
    SwitchType[] types() {
        return SwitchType.values();
    }

    // ---- 수정 (로그인 필요) ----

    @GetMapping("/switches/{id}/edit")
    String editForm(@PathVariable Long id, Model model) {
        KeyboardSwitch sw = switchService.get(id);
        model.addAttribute("form", SwitchForm.from(sw, switchService.aliasesOf(id)));
        model.addAttribute("switchId", id);
        return "switches/edit";
    }

    @PostMapping("/switches/{id}")
    String update(@PathVariable Long id,
                  @Valid @ModelAttribute("form") SwitchForm form, BindingResult binding,
                  @AuthenticationPrincipal MemberUserDetails editor,
                  RedirectAttributes redirect, HttpServletResponse response, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("switchId", id);
            return "switches/edit";
        }
        try {
            switchService.update(id, form.getVersion(), form.toSpec(), form.aliasList(),
                    form.getEditSummary(), editor.getId(), editor.getNickname(), form.getSourceUrl());
        } catch (OptimisticLockingFailureException e) {
            binding.reject("conflict", "그 사이 다른 사람이 먼저 고쳤어요. 새로고침 후 다시 해 주세요.");
            model.addAttribute("switchId", id);
            response.setStatus(HttpStatus.CONFLICT.value());
            return "switches/edit";
        }
        redirect.addFlashAttribute("message", "수정했어요.");
        return "redirect:/switches/" + id;
    }

    // ---- 이력·비교 (누구나) ----

    @GetMapping("/switches/{id}/history")
    String history(@PathVariable Long id, Model model) {
        List<SwitchRevision> revisions = switchService.revisionsOf(id);
        model.addAttribute("sw", switchService.get(id));
        model.addAttribute("revisions", revisions);
        model.addAttribute("latestNo", revisions.isEmpty() ? 0 : revisions.getFirst().getRevisionNo());
        return "switches/history";
    }

    @GetMapping("/switches/{id}/compare")
    String compare(@PathVariable Long id,
                   @RequestParam("from") int fromNo, @RequestParam("to") int toNo, Model model) {
        SwitchRevision from = switchService.revisionAt(id, fromNo);
        SwitchRevision to = switchService.revisionAt(id, toNo);
        model.addAttribute("sw", switchService.get(id));
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("rows", switchService.compareRows(from, to));
        model.addAttribute("descriptionFrom", from.getSpec().description());
        model.addAttribute("descriptionTo", to.getSpec().description());
        return "switches/compare";
    }

    // ---- 되돌리기 (로그인 필요, 서버에서 권한·최신 여부를 다시 확인) ----

    @GetMapping("/switches/{id}/revisions/{no}/revert")
    String revertForm(@PathVariable Long id, @PathVariable int no,
                      @AuthenticationPrincipal MemberUserDetails member, Model model) {
        populateRevert(id, no, member, model);
        RevertForm form = new RevertForm();
        form.setVersion(switchService.get(id).getVersion());
        model.addAttribute("form", form);
        return "switches/revert";
    }

    @PostMapping("/switches/{id}/revisions/{no}/revert")
    String revert(@PathVariable Long id, @PathVariable int no,
                  @Valid @ModelAttribute("form") RevertForm form, BindingResult binding,
                  @AuthenticationPrincipal MemberUserDetails member,
                  RedirectAttributes redirect, HttpServletResponse response, Model model) {
        if (binding.hasErrors()) {
            populateRevert(id, no, member, model);
            return "switches/revert";
        }
        String summary = (form.getEditSummary() == null || form.getEditSummary().isBlank())
                ? "#" + no + " 수정 취소"
                : form.getEditSummary().strip();
        try {
            switchService.revert(id, no, form.getVersion(), member.getId(), member.isAdmin(),
                    member.getNickname(), summary);
        } catch (RevertNotAllowedException e) {
            if (e.getBlock() == RevertBlock.NOT_ALLOWED) {
                throw e; // 403은 평문 응답(핸들러)
            }
            // 최신 아님·첫 등록: 되돌리기 화면을 사유와 함께 다시 그리고 409
            populateRevert(id, no, member, model);
            response.setStatus(HttpStatus.CONFLICT.value());
            return "switches/revert";
        } catch (OptimisticLockingFailureException e) {
            binding.reject("conflict", "그 사이 다른 사람이 먼저 고쳤어요. 새로고침 후 다시 해 주세요.");
            populateRevert(id, no, member, model);
            response.setStatus(HttpStatus.CONFLICT.value());
            return "switches/revert";
        }
        redirect.addFlashAttribute("message", "#" + no + " 수정을 취소했어요.");
        return "redirect:/switches/" + id;
    }

    @ExceptionHandler(RevertNotAllowedException.class)
    ResponseEntity<String> revertDenied(RevertNotAllowedException e) {
        HttpStatus status = e.getBlock() == RevertBlock.NOT_ALLOWED ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(denyReason(e.getBlock()));
    }

    // ---- 최근 변경 (누구나) ----

    @GetMapping("/changes")
    String changes(@RequestParam(value = "page", defaultValue = "0") int page, Model model) {
        Page<SwitchRevision> result = switchService.recentChanges(
                PageRequest.of(Math.max(page, 0), CHANGES_PAGE_SIZE));
        model.addAttribute("page", result);
        return "changes/list";
    }

    // ---- 내부 ----

    /** 되돌리기 화면에 필요한 모델을 채운다. 막혀 있으면 이전 스냅샷과 비교 행은 비워 둔다. */
    private void populateRevert(Long id, int no, MemberUserDetails member, Model model) {
        KeyboardSwitch sw = switchService.get(id);
        SwitchRevision target = switchService.revisionAt(id, no);
        RevertBlock block = switchService.revertBlock(id, no, member.getId(), member.isAdmin());
        boolean canRevert = block == null;

        model.addAttribute("sw", sw);
        model.addAttribute("target", target);
        model.addAttribute("canRevert", canRevert);
        model.addAttribute("denyReason", canRevert ? null : denyReason(block));
        if (canRevert) {
            SwitchRevision previous = switchService.revisionAt(id, no - 1);
            model.addAttribute("previous", previous);
            model.addAttribute("rows", switchService.compareRows(previous, target));
        } else {
            model.addAttribute("previous", null);
            model.addAttribute("rows", List.of());
        }
    }

    private static String denyReason(RevertBlock block) {
        return switch (block) {
            case NOT_ALLOWED -> "작성자 본인이나 관리자만 취소할 수 있어요.";
            case NOT_LATEST -> "뒤에 다른 수정이 있어서 취소할 수 없어요.";
            case FIRST_REVISION -> "처음 등록은 취소할 수 없어요.";
        };
    }
}
