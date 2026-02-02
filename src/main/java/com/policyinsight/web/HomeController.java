package com.policyinsight.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for the root landing page.
 * Always returns the upload form template; when app.demo-sleep=true, the template shows a sleep banner.
 */
@Controller
public class HomeController {

    @Value("${app.demo-sleep:false}")
    private boolean demoSleep;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("demoSleep", demoSleep);
        return "index";
    }
}

