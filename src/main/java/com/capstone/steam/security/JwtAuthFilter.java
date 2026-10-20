package com.capstone.steam.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lee "Authorization: Bearer ..." y, si el token es válido, autentica la petición.
 * Un token inválido no corta la cadena: la petición sigue como anónima y las reglas
 * de SecurityConfig devuelven 401 si el endpoint lo requiere.
 * No es un @Component a propósito: así Spring Boot no lo registra también como filtro de servlet.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AccessTokenRevocations revocations;

    public JwtAuthFilter(JwtService jwtService, AccessTokenRevocations revocations) {
        this.jwtService = jwtService;
        this.revocations = revocations;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                AppUserPrincipal principal = jwtService.parseToken(header.substring(BEARER_PREFIX.length()));
                if (revocations.isRevoked(principal.getId(), principal.getIssuedAt())) {
                    log.debug("JWT rechazado: emitido antes de una revocación de la cuenta");
                    chain.doFilter(request, response);
                    return;
                }
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("JWT rechazado: {}", ex.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
