package com.switchbase.collections;

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

/** 컬렉션 토글과 내 컬렉션 화면 (review-screens §2). */
@Controller
public class CollectionController {

    private static final int PAGE_SIZE = 20;

    private final CollectionService collectionService;

    CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping("/switches/{switchId}/collection/{status}")
    String toggle(@PathVariable Long switchId, @PathVariable CollectionStatus status,
                  @AuthenticationPrincipal MemberUserDetails member, RedirectAttributes redirect) {
        boolean on = collectionService.toggle(member.getId(), switchId, status);
        redirect.addFlashAttribute("message", status.getLabel() + (on ? "에 넣었어요." : "에서 뺐어요."));
        return "redirect:/switches/" + switchId;
    }

    @GetMapping("/me/collection")
    String mine(@RequestParam(value = "status", defaultValue = "ALL") String status,
                @RequestParam(value = "page", defaultValue = "0") int page,
                @AuthenticationPrincipal MemberUserDetails member, Model model) {
        CollectionStatus filter = parse(status);
        model.addAttribute("status", filter == null ? "ALL" : filter.name());
        model.addAttribute("items", collectionService.mine(member.getId(), filter,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE)));
        model.addAttribute("collectionPublic", collectionService.isPublic(member.getId()));
        return "collections/me";
    }

    @PostMapping("/me/collection/visibility")
    String visibility(@RequestParam(value = "collectionPublic", defaultValue = "false") boolean collectionPublic,
                      @AuthenticationPrincipal MemberUserDetails member, RedirectAttributes redirect) {
        collectionService.setPublic(member.getId(), collectionPublic);
        redirect.addFlashAttribute("message", collectionPublic ? "컬렉션을 공개했어요." : "컬렉션을 비공개로 바꿨어요.");
        return "redirect:/me/collection";
    }

    /** 타인의 컬렉션. 공개 설정이 꺼져 있으면 404. */
    @GetMapping("/members/{memberId}/collection")
    String publicCollection(@PathVariable Long memberId,
                     @RequestParam(value = "status", defaultValue = "ALL") String status,
                     @RequestParam(value = "page", defaultValue = "0") int page,
                     @AuthenticationPrincipal MemberUserDetails viewer, Model model) {
        CollectionStatus filter = parse(status);
        model.addAttribute("status", filter == null ? "ALL" : filter.name());
        model.addAttribute("items", collectionService.publicOf(memberId,
                viewer == null ? null : viewer.getId(), filter, PageRequest.of(Math.max(page, 0), PAGE_SIZE)));
        model.addAttribute("memberId", memberId);
        return "collections/public";
    }

    private static CollectionStatus parse(String status) {
        if (status == null || status.equals("ALL")) {
            return null;
        }
        return CollectionStatus.valueOf(status);
    }
}
