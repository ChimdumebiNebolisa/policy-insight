package com.policyinsight.controller;

import com.policyinsight.config.PasteProperties;
import com.policyinsight.config.UploadProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final UploadProperties uploadProperties;
    private final PasteProperties pasteProperties;

    public HomeController(UploadProperties uploadProperties, PasteProperties pasteProperties) {
        this.uploadProperties = uploadProperties;
        this.pasteProperties = pasteProperties;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("maxUploadMb", Math.max(1, uploadProperties.maxBytes() / 1024 / 1024));
        model.addAttribute("maxPasteChars", pasteProperties.maxChars());
        return "index";
    }
}
