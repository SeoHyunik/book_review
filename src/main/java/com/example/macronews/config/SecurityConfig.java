package com.example.macronews.config;

import com.example.macronews.filter.LogUuidFilter;
import com.example.macronews.security.CustomAccessDeniedHandler;
import com.example.macronews.security.CustomUserDetailsService;
import com.example.macronews.security.LoggingAuthenticationFailureHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Slf4j
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final LoggingAuthenticationFailureHandler loggingAuthenticationFailureHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        log.info(
                "Configuring SecurityFilterChain with authentication, CSRF protection, and role-based access control");
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/**"))
                .anonymous(Customizer.withDefaults())
                .authenticationProvider(daoAuthenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/register", "/error", "/access-denied")
                        .permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**")
                        .permitAll()
                        .requestMatchers("/api/users/check-username", "/api/users/check-email")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/news/**").authenticated()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/news", true)
                        .failureHandler(loggingAuthenticationFailureHandler)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/news"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                        .accessDeniedHandler(customAccessDeniedHandler()));
        SecurityFilterChain chain = http.build();
        log.debug("SecurityFilterChain initialized: {}", chain);
        return chain;
    }

    @Bean(name = "customAccessDeniedHandlerBean")
    public AccessDeniedHandler customAccessDeniedHandler() {
        return new CustomAccessDeniedHandler();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(
                customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public FilterRegistrationBean<LogUuidFilter> logUuidFilterRegistration() {
        FilterRegistrationBean<LogUuidFilter> registration = new FilterRegistrationBean<>(
                new LogUuidFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}