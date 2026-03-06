package com.example.bookreview.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.example.bookreview.filter.LogUuidFilter;
import com.example.bookreview.security.CustomAccessDeniedHandler;
import com.example.bookreview.security.CustomUserDetailsService;
import com.example.bookreview.security.LoggingAuthenticationFailureHandler;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final LoggingAuthenticationFailureHandler loggingAuthenticationFailureHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        log.info(
                "Configuring SecurityFilterChain with authentication, CSRF protection, and role-based access control");
        http
                // CSRF??疫꿸퀡????뽮쉐???怨밴묶???醫???뺣뼄. ?????곷섧?紐껊뮉 筌롫?? ??볥젃????곸뒠???醫뤾쿃???袁⑸꽊??뺣뼄.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/**"))
                // ??ъ구 ????癒? 筌뤿굞??怨몄몵嚥???뽮쉐?酉釉????쀫탣?깆슦肉??isAnonymous()/isAuthenticated() ?브쑨由곁몴???而?몴?우쓺 ?????뺣뼄.
                .anonymous(Customizer.withDefaults())
                .authenticationProvider(daoAuthenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/register", "/error", "/access-denied")
                        .permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**")
                        .permitAll()
                        .requestMatchers("/api/users/check-username", "/api/users/check-email")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/reviews/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/reviews").authenticated()
                        .requestMatchers(HttpMethod.GET, "/news/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/admin/news/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/admin/news/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/reviews/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/reviews/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/reviews/**").authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/news", true)
                        .failureHandler(loggingAuthenticationFailureHandler)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/news"))
                // 嚥≪뮄??紐낅릭筌왖 ??? ????癒?뮉 /login ??곗쨮 ??猷??쀪텕?? ?紐꾩쵄???????亦낅슦釉??봔鈺곌퉭彛?AccessDeniedHandler揶쎛 筌ｌ꼶???뺣뼄.
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

