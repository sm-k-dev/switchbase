package com.switchbase.ratings;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 개조 태그 자동완성 조각(htmx). 로그인 필요(SecurityConfig). */
@Controller
public class ModTagController {

    private final RatingService ratingService;

    ModTagController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @GetMapping("/mod-tags/suggest")
    String suggest(@RequestParam(value = "modTags", defaultValue = "") String modTags, Model model) {
        model.addAttribute("tags", ratingService.suggestTags(modTags));
        return "ratings/tag-suggest";
    }
}
