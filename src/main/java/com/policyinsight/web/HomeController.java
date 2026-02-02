package com.policyinsight.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for the root landing page.
 * Returns the upload form template, or the sleep landing page when app.demo-sleep=true.
 */
@Controller
public class HomeController {

    @Value("${app.demo-sleep:false}")
    private boolean demoSleep;

    @GetMapping("/")
    public String index() {
        return demoSleep ? "landing-sleep" : "index";
    }
}

