package com.example.bookreview.controller;

import com.example.bookreview.service.UserService;
import com.example.bookreview.web.request.RegistrationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/register")
    public String registerForm(Model model) {
        log.debug("Rendering registration page");
        if (!model.containsAttribute("registrationRequest")) {
            model.addAttribute("registrationRequest", RegistrationRequest.empty());
        }
        model.addAttribute("pageTitle", "Register");
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationRequest") RegistrationRequest registrationRequest,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        log.info("Attempting to register user '{}'", registrationRequest.username());

        if (!registrationRequest.password().equals(registrationRequest.confirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Register");
            return "auth/register";
        }

        try {
            userService.registerUser(registrationRequest);
        } catch (DuplicateKeyException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("email")) {
                bindingResult.rejectValue("email", "email.duplicate", "This email is already in use.");
            } else {
                bindingResult.rejectValue("username", "username.duplicate", "This username is already taken.");
            }
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("registration.invalid", ex.getMessage());
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Register");
            return "auth/register";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Account created. Please sign in.");
        return "redirect:/login?registered";
    }
}
