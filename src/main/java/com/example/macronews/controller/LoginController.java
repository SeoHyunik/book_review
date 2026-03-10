package com.example.macronews.controller;

import com.example.macronews.security.ContinueAwareAuthenticationSuccessHandler;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
public class LoginController {

    private final Environment environment;

    public LoginController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/login")
    public String login(@RequestParam(name = "continue", required = false) String continueUrl, Model model,
            HttpSession session) {
        log.debug("Rendering login page");
        model.addAttribute("pageTitle", "Login");

        if (StringUtils.hasText(continueUrl) && continueUrl.startsWith("/")) {
            model.addAttribute("continueUrl", continueUrl);
            session.setAttribute(ContinueAwareAuthenticationSuccessHandler.CONTINUE_URL_SESSION_KEY,
                    continueUrl);
        } else {
            model.addAttribute("continueUrl", "");
            session.removeAttribute(ContinueAwareAuthenticationSuccessHandler.CONTINUE_URL_SESSION_KEY);
        }

        model.addAttribute("googleLoginEnabled", isGoogleLoginEnabled());
        return "auth/login";
    }

    private boolean isGoogleLoginEnabled() {
        boolean featureEnabled = Boolean.parseBoolean(environment.getProperty(
                "app.auth.google-login-enabled", "false"));
        boolean configured = StringUtils.hasText(environment.getProperty(
                "spring.security.oauth2.client.registration.google.client-id"))
                && StringUtils.hasText(environment.getProperty(
                        "spring.security.oauth2.client.registration.google.client-secret"));
        return featureEnabled && configured;
    }
}