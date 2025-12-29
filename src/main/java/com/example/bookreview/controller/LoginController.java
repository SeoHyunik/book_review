package com.example.bookreview.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(Model model) {
        log.debug("Rendering login page");
        model.addAttribute("pageTitle", "Login");
        return "auth/login";
    }
}
