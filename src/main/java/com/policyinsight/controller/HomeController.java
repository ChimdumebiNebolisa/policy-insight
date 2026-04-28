package com.policyinsight.controller;

import com.policyinsight.config.UploadProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final UploadProperties uploadProperties;

    public HomeController(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("maxUploadMb", Math.max(1, uploadProperties.maxBytes() / 1024 / 1024));
        return "index";
    }
}
