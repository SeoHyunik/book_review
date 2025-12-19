package com.example.bookreview.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
        log.info("[AUTH] Rendering custom login page");
        return "auth/login";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        log.warn("[AUTH] Access denied page rendered due to insufficient privileges");
        return "error/403";
    }
}
