package com.op1ed.appointmentmanager.identity.web.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.op1ed.appointmentmanager.identity.application.IdentityService;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;
import com.op1ed.appointmentmanager.shared.web.SecurityProblemWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class BearerTokenFilter extends OncePerRequestFilter {
    private final IdentityService identityService;
    private final SecurityProblemWriter problemWriter;

    BearerTokenFilter(IdentityService identityService, SecurityProblemWriter problemWriter) {
        this.identityService = identityService;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        Optional<UserIdentity> found = header.regionMatches(true, 0, "Bearer ", 0, 7)
                ? identityService.authenticate(header.substring(7).strip()) : Optional.empty();
        if (found.isEmpty()) {
            problemWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                    ErrorCode.UNAUTHENTICATED, "登录令牌无效或已过期");
            return;
        }
        UserIdentity user = found.orElseThrow();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority(
                        user.administrator() ? "ROLE_ADMIN" : "ROLE_USER"
                ))
        );
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
