package com.switchbase.keyswitch;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/switches")
public class SwitchController {

    private final SwitchService switchService;

    SwitchController(SwitchService switchService) {
        this.switchService = switchService;
    }

    @GetMapping
    String list(@PageableDefault(size = 20, sort = {"spec.manufacturer", "spec.modelName"})Pageable pageable, Model model) {

        model.addAttribute("page", switchService.list(pageable));
        return "switches/list";
    }

    @GetMapping("/{id}")
    String detail(@PathVariable Long id, Model model) {

        model.addAttribute("sw", switchService.get(id));
        model.addAttribute("sources", switchService.sourcesOf(id));
        model.addAttribute("revisions", switchService.revisionsOf(id));

        return "switches/detail";
    }
}
