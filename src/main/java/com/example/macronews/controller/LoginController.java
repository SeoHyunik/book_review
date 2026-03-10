package com.example.macronews.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(@RequestParam(name = "continue", required = false) String continueUrl, Model model) {
        log.debug("Rendering login page");
        model.addAttribute("pageTitle", "Login");
        model.addAttribute("continueUrl", StringUtils.hasText(continueUrl) ? continueUrl : "");
        return "auth/login";
    }
}
