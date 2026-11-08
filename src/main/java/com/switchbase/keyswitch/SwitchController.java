package com.switchbase.keyswitch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.switchbase.member.MemberUserDetails;
import com.switchbase.ratings.RatingService;

@Controller
@RequestMapping("/switches")
public class SwitchController {

    private final SwitchService switchService;

    private static final int RATING_PAGE_SIZE = 20;

    private final RatingService ratingService;

    private final com.switchbase.reviews.ReviewService reviewService;
    private final com.switchbase.collections.CollectionService collectionService;

    SwitchController(SwitchService switchService, RatingService ratingService,
                     com.switchbase.reviews.ReviewService reviewService,
                     com.switchbase.collections.CollectionService collectionService) {
        this.switchService = switchService;
        this.ratingService = ratingService;
        this.reviewService = reviewService;
        this.collectionService = collectionService;
    }

    @GetMapping
    String list(SwitchSearch search,
                @PageableDefault(size = 20) Pageable pageable,
                @RequestHeader(value = "HX-Request", required = false) String htmx,
                Model model) {
        Page<KeyboardSwitch> page = switchService.search(search, pageable);
        model.addAttribute("search", search);
        model.addAttribute("page", page);
        model.addAttribute("types", SwitchType.values());

        // htmx가 보낸 요청이면 결과 표 부분만, 아니면 페이지 전체
        return htmx != null ? "switches/list :: results" : "switches/list";
    }

    @GetMapping("/{id}")
    String detail(@PathVariable Long id,
                  @RequestParam(value = "ratingPage", defaultValue = "0") int ratingPage,
                  @RequestParam(value = "sort", defaultValue = "recent") String sort,
                  @RequestParam(value = "reviewPage", defaultValue = "0") int reviewPage,
                  @AuthenticationPrincipal MemberUserDetails viewer, Model model) {

        model.addAttribute("sw", switchService.get(id));
        model.addAttribute("sources", switchService.sourcesOf(id));
        model.addAttribute("revisions", switchService.revisionsOf(id));
        model.addAttribute("aliases", switchService.aliasesOf(id));
        model.addAttribute("switchId", id);
        String normalizedSort = "helpful".equals(sort) ? "helpful" : "recent";
        model.addAttribute("sort", normalizedSort);
        model.addAttribute("reviewPage", reviewService.list(id, viewer == null ? null : viewer.getId(),
                viewer != null && viewer.isAdmin(), normalizedSort,
                PageRequest.of(Math.max(reviewPage, 0), RATING_PAGE_SIZE)));
        model.addAttribute("collection", viewer == null ? null : collectionService.state(viewer.getId(), id));
        model.addAttribute("summary", ratingService.summary(id));
        model.addAttribute("ratingPage", ratingService.list(id,
                viewer == null ? null : viewer.getId(), viewer != null && viewer.isAdmin(),
                PageRequest.of(Math.max(ratingPage, 0), RATING_PAGE_SIZE)));

        return "switches/detail";
    }
}
