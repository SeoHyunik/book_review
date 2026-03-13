package com.example.macronews.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.macronews.controller.AdminNewsController;
import com.example.macronews.controller.LoginController;
import com.example.macronews.service.macro.MacroAiService;
import com.example.macronews.service.news.source.NewsSourceProviderSelector;
import com.example.macronews.service.news.NewsIngestionService;
import com.example.macronews.service.news.NewsQueryService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

class RedirectHardeningTest {

    private final AdminNewsController adminNewsController = new AdminNewsController(
            mock(NewsIngestionService.class),
            mock(NewsSourceProviderSelector.class),
            mock(MacroAiService.class),
            mock(NewsQueryService.class));

    @Test
    @DisplayName("Admin redirect should fall back to /news and drop invalid status and sort")
    void adminRedirect_fallsBackToNewsAndDropsInvalidContext() {
        String redirect = ReflectionTestUtils.invokeMethod(
                adminNewsController,
                "resolveAdminRedirect",
                "https://evil.example/news",
                "bogus",
                "bogus");

        assertThat(redirect).isEqualTo("/news");
    }

    @Test
    @DisplayName("Admin redirect should preserve valid news list status and sort context")
    void adminRedirect_preservesValidNewsContext() {
        String redirect = ReflectionTestUtils.invokeMethod(
                adminNewsController,
                "resolveAdminRedirect",
                "/news",
                "analyzed",
                "published_desc");

        assertThat(redirect).isEqualTo("/news?status=ANALYZED&sort=published_desc");
    }

    @Test
    @DisplayName("Login controller should ignore unsafe continue targets")
    void login_ignoresUnsafeContinueTarget() {
        LoginController loginController = new LoginController(mock(Environment.class));
        Model model = new ConcurrentModel();
        HttpSession session = new MockHttpSession();

        String viewName = loginController.login("//evil.example", model, session);

        assertThat(viewName).isEqualTo("auth/login");
        assertThat(model.getAttribute("continueUrl")).isEqualTo("");
        assertThat(session.getAttribute(ContinueAwareAuthenticationSuccessHandler.CONTINUE_URL_SESSION_KEY))
                .isNull();
    }

    @Test
    @DisplayName("Login controller should preserve safe relative continue targets")
    void login_preservesSafeRelativeContinueTarget() {
        LoginController loginController = new LoginController(mock(Environment.class));
        Model model = new ConcurrentModel();
        HttpSession session = new MockHttpSession();

        String viewName = loginController.login("/news/abc123", model, session);

        assertThat(viewName).isEqualTo("auth/login");
        assertThat(model.getAttribute("continueUrl")).isEqualTo("/news/abc123");
        assertThat(session.getAttribute(ContinueAwareAuthenticationSuccessHandler.CONTINUE_URL_SESSION_KEY))
                .isEqualTo("/news/abc123");
    }

    @Test
    @DisplayName("Authentication success handler should ignore unsafe continue targets and fall back to /news")
    void authenticationSuccessHandler_ignoresUnsafeContinueTarget() throws Exception {
        ContinueAwareAuthenticationSuccessHandler handler = new ContinueAwareAuthenticationSuccessHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication authentication = new TestingAuthenticationToken("user", "password");
        request.setParameter("continue", "https://evil.example");

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/news");
    }

    @Test
    @DisplayName("Authentication failure handler should preserve only safe relative continue targets")
    void authenticationFailureHandler_preservesOnlySafeRelativeContinueTarget() throws Exception {
        LoggingAuthenticationFailureHandler handler = new LoggingAuthenticationFailureHandler();
        AuthenticationException failure = new BadCredentialsException("bad credentials");

        MockHttpServletRequest safeRequest = new MockHttpServletRequest();
        MockHttpServletResponse safeResponse = new MockHttpServletResponse();
        safeRequest.setMethod("POST");
        safeRequest.setParameter("continue", "/news/abc123");
        handler.onAuthenticationFailure(safeRequest, safeResponse, failure);

        assertThat(safeResponse.getRedirectedUrl()).isEqualTo("/login?error&continue=/news/abc123");

        MockHttpServletRequest unsafeRequest = new MockHttpServletRequest();
        MockHttpServletResponse unsafeResponse = new MockHttpServletResponse();
        unsafeRequest.setMethod("POST");
        unsafeRequest.setParameter("continue", "https://evil.example");
        handler.onAuthenticationFailure(unsafeRequest, unsafeResponse, failure);

        assertThat(unsafeResponse.getRedirectedUrl()).isEqualTo("/login?error");
    }
}
