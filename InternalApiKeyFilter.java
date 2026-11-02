package com.frauddetect.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * El endpoint /api/alerts no lo usa un usuario humano sino el servicio Python
 * (comunicacion servicio-a-servicio). En vez de pedirle un JWT de usuario,
 * exigimos una API key compartida via variable de entorno.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    @Value("${app.internal-api-key}")
    private String internalApiKey;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/alerts")) {
            String key = request.getHeader("X-Internal-Api-Key");
            if (key == null || !key.equals(internalApiKey)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "API key interna invalida o ausente");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
