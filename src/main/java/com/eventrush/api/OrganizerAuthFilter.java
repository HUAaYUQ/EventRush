package com.eventrush.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class OrganizerAuthFilter extends OncePerRequestFilter {

    private static final String ORGANIZER_KEY_HEADER = "X-Organizer-Key";

    private final ObjectMapper objectMapper;
    private final String organizerKey;

    OrganizerAuthFilter(
            ObjectMapper objectMapper,
            @Value("${eventrush.organizer.key:eventrush-organizer-key}") String organizerKey
    ) {
        this.objectMapper = objectMapper;
        this.organizerKey = organizerKey;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/organizer/")
                || organizerKey.equals(request.getHeader(ORGANIZER_KEY_HEADER))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                "UNAUTHORIZED", "主办方访问密钥无效"));
    }
}
