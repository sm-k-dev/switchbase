package com.switchbase.keyswitch;

import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

@Controller
@RequestMapping("/admin/switches")
class AdminSwitchController {

    private final SwitchService switchService;

    AdminSwitchController(SwitchService switchService) {
        this.switchService = switchService;
    }

    @ModelAttribute("types")
    SwitchType[] types() {
        return SwitchType.values();
    }

    @GetMapping("/new")
    String newForm(Model model) {
        model.addAttribute("form", new SwitchForm());
        return "admin/switch-form";
    }

    @PostMapping
    String create(@Valid @ModelAttribute("form") SwitchForm form, BindingResult binding, Principal principal) {
        if (!binding.hasErrors() && switchService.exists(form.getManufacturer().strip(), form.getModelName().strip())) {
            binding.rejectValue("modelName", "duplicate", "이미 등록된 스위치예요");
        }
        if (binding.hasErrors()) {
            return "admin/switch-form";
        }
        KeyboardSwitch saved = switchService.create(
                form.toSpec(), form.getEditSummary(), principal.getName(), form.getSourceUrl());
        return "redirect:/switches/" + saved.getId();
    }

    @GetMapping("/{id}/edit")
    String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", SwitchForm.from(switchService.get(id)));
        model.addAttribute("switchId", id);
        return "admin/switch-form";
    }

    @PostMapping("/{id}")
    String update(@PathVariable Long id,
                  @Valid @ModelAttribute("form") SwitchForm form, BindingResult binding,
                  Principal principal, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("switchId", id);
            return "admin/switch-form";
        }
        try {
            switchService.update(id, form.getVersion(), form.toSpec(),
                    form.getEditSummary(), principal.getName(), form.getSourceUrl());
        } catch (OptimisticLockingFailureException e) {
            binding.reject("conflict", "그 사이 다른 사람이 먼저 고쳤어요. 새로고침 후 다시 해 주세요.");
            model.addAttribute("switchId", id);
            return "admin/switch-form";
        }
        return "redirect:/switches/" + id;
    }
}