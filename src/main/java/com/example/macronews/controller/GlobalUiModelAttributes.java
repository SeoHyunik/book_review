package com.example.macronews.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class GlobalUiModelAttributes {

    @ModelAttribute
    public void addLocaleAttributes(Model model, HttpServletRequest request, Locale locale) {
        model.addAttribute("currentPath", request.getRequestURI());
        model.addAttribute("currentStatus", request.getParameter("status"));
        model.addAttribute("currentSort", request.getParameter("sort"));
        model.addAttribute("currentPage", request.getParameter("page"));
        model.addAttribute("currentLang", locale.getLanguage());
    }
}
