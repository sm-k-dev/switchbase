package com.switchbase.keyswitch;

import com.switchbase.member.MemberUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

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
    String create(@Valid @ModelAttribute("form") SwitchForm form, BindingResult binding,
                  @AuthenticationPrincipal MemberUserDetails editor) {
        if (!binding.hasErrors() && switchService.exists(form.getManufacturer().strip(), form.getModelName().strip(), form.getVariant())) {
            binding.rejectValue("modelName", "duplicate", "이미 등록된 스위치예요");
        }
        if (binding.hasErrors()) {
            return "admin/switch-form";
        }
        KeyboardSwitch saved = switchService.create(
                form.toSpec(), form.aliasList(), form.getEditSummary(),
                editor.getId(), editor.getNickname(), form.getSourceUrl());
        return "redirect:/switches/" + saved.getId();
    }

    @GetMapping("/import")
    String importForm() {
        return "admin/switch-import";
    }

    @PostMapping("/import")
    String importCsv(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal MemberUserDetails editor,
                     RedirectAttributes redirect) throws IOException {
        SwitchCsvImporter.Result result = csvImporter.importCsv(file.getInputStream(), editor.getId(), editor.getNickname());
        redirect.addFlashAttribute("result", result);
        return "redirect:/admin/switches/import";
    }
}
