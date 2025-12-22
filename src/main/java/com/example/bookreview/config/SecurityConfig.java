package com.example.bookreview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.http.HttpMethod;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import com.example.bookreview.filter.LogUuidFilter;
import com.example.bookreview.security.CustomAccessDeniedHandler;

@Slf4j
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("Configuring SecurityFilterChain with authentication, CSRF protection, and role-based access control");
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                PathPatternRequestMatcher.withDefaults().matcher("/api/**")
            ))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/error", "/access-denied").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/reviews/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/reviews/new").permitAll()
                .requestMatchers(HttpMethod.POST, "/reviews").permitAll()
                .requestMatchers(HttpMethod.PUT, "/reviews/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/reviews/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/reviews/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/reviews", true)
                .permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/reviews"))
            .exceptionHandling(exceptions -> exceptions.accessDeniedHandler(customAccessDeniedHandler()));
        SecurityFilterChain chain = http.build();
        log.debug("SecurityFilterChain initialized: {}", chain);
        return chain;
    }

    @Bean(name = "customAccessDeniedHandlerBean")
    public AccessDeniedHandler customAccessDeniedHandler() {
        return new CustomAccessDeniedHandler();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails user = User.withUsername("user")
            .password(passwordEncoder.encode("password"))
            .roles("USER")
            .build();

        UserDetails admin = User.withUsername("admin")
            .password(passwordEncoder.encode("password"))
            .roles("USER", "ADMIN")
            .build();

        return new InMemoryUserDetailsManager(user, admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<LogUuidFilter> logUuidFilterRegistration() {
        FilterRegistrationBean<LogUuidFilter> registration = new FilterRegistrationBean<>(new LogUuidFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}