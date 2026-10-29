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

@Controller
@RequestMapping("/switches")
public class SwitchController {

    private final SwitchService switchService;

    SwitchController(SwitchService switchService) {
        this.switchService = switchService;
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
    String detail(@PathVariable Long id, Model model) {

        model.addAttribute("sw", switchService.get(id));
        model.addAttribute("sources", switchService.sourcesOf(id));
        model.addAttribute("revisions", switchService.revisionsOf(id));

        return "switches/detail";
    }
}
