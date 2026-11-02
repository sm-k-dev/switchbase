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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;

@Controller
@RequestMapping("/admin/switches")
class AdminSwitchController {

    private final SwitchService switchService;
    private final SwitchCsvImporter csvImporter;

    AdminSwitchController(SwitchService switchService, SwitchCsvImporter csvImporter) {
        this.switchService = switchService;
        this.csvImporter = csvImporter;
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
        if (!binding.hasErrors() && switchService.exists(form.getManufacturer().strip(), form.getModelName().strip(), form.getVariant())) {
            binding.rejectValue("modelName", "duplicate", "이미 등록된 스위치예요");
        }
        if (binding.hasErrors()) {
            return "admin/switch-form";
        }
        KeyboardSwitch saved = switchService.create(
                form.toSpec(), form.aliasList(), form.getEditSummary(), principal.getName(), form.getSourceUrl());
        return "redirect:/switches/" + saved.getId();
    }

    @GetMapping("/{id}/edit")
    String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", SwitchForm.from(switchService.get(id), switchService.aliasesOf(id)));
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
            switchService.update(id, form.getVersion(), form.toSpec(), form.aliasList(),
                    form.getEditSummary(), principal.getName(), form.getSourceUrl());
        } catch (OptimisticLockingFailureException e) {
            binding.reject("conflict", "그 사이 다른 사람이 먼저 고쳤어요. 새로고침 후 다시 해 주세요.");
            model.addAttribute("switchId", id);
            return "admin/switch-form";
        }
        return "redirect:/switches/" + id;
    }

    @GetMapping("/import")
    String importForm() {
        return "admin/switch-import";
    }

    @PostMapping("/import")
    String importCsv(@RequestParam("file") MultipartFile file, Principal principal,
                     RedirectAttributes redirect) throws IOException {
        SwitchCsvImporter.Result result = csvImporter.importCsv(file.getInputStream(), principal.getName());
        redirect.addFlashAttribute("result", result);
        return "redirect:/admin/switches/import";
    }
}
