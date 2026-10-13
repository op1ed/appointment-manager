package com.op1ed.appointmentmanager.identity.web.security;

import com.op1ed.appointmentmanager.identity.application.IdentityService;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;
import com.op1ed.appointmentmanager.shared.web.SecurityProblemWriter;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, IdentityService identityService,
            SecurityProblemWriter problemWriter) throws Exception {
        return http
                // 只接受显式 Authorization Bearer 凭证，不使用 Cookie 或 HTTP Session 登录。
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/doctors", "/api/doctors/*", "/api/doctors/*/slots").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/doctors", "/api/doctors/*/slots").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                        .requestMatchers("/api/appointments", "/api/appointments/**").authenticated()
                        .requestMatchers("/api/agent/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> problemWriter.write(
                                request, response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "请先登录"
                        ))
                        .accessDeniedHandler((request, response, exception) -> problemWriter.write(
                                request, response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "需要管理员权限"
                        ))
                )
                .addFilterBefore(new BearerTokenFilter(identityService, problemWriter), AnonymousAuthenticationFilter.class)
                .build();
    }
}
