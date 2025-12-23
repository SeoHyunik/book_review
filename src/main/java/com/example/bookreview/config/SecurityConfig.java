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
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
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
            // CSRF는 기본 활성화 상태를 유지하고, 서버 간 API 호출로 설계된 "/api/**"만 예외로 둔다.
            // HTML 폼(/reviews) 요청은 토큰을 포함해 전송하도록 유도해 CSRF 보호를 그대로 받는다.
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                PathPatternRequestMatcher.withDefaults().matcher("/api/**")
            ))
            // AccessDeniedException으로 HTML 에러 페이지가 반환되던 원인: anonymous 사용자가 AccessDeniedHandler로 위임됨.
            // 익명 인증을 비활성화해 인증되지 않은 사용자는 AccessDenied가 아닌 인증 진입점으로 흐른다.
            .anonymous(anon -> anon.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/error", "/access-denied").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/reviews/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/reviews").authenticated()
                .requestMatchers("/api/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/reviews/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/reviews/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/reviews/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/reviews", true)
                .permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/reviews"))
            // 로그인하지 않은 사용자는 /login 으로 이동시키고, 인증된 사용자 권한 부족만 AccessDeniedHandler가 처리한다.
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