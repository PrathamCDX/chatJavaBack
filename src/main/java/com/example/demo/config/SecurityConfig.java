package com.example.demo.config;

import com.example.demo.service.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

// Baseline only: public paths, and a login for everything else. Endpoints that need more
// (roles, headers) declare it on the controller method with @RoleFilter / @HeaderFilter.
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                // Token comes in the Authorization header, not a cookie, so CSRF doesn't apply
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .authorizeHttpRequests(a -> a
                        // /error must be public or 400/409 responses on public routes turn into 401
                        .requestMatchers("/auth/**", "/health/**", "/error").permitAll()
                        // WebSocket chat: browsers can't send the Authorization header on the handshake,
                        // so the chat handler checks a one-time ticket (?ticket=) when the connection opens.
                        // Only the handshake path: /ws/ticket, which issues tickets, needs the JWT
                        .requestMatchers("/ws/chat").permitAll()
                        // Default for every other endpoint: logged in
                        .anyRequest().authenticated())
                // Write ApiResponse-shaped JSON for security errors too
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // Messages are fixed strings, so building the JSON by hand needs no escaping
    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\",\"data\":null}");
    }

    // The filter is a @Component, so stop Spring Boot from also running it as a plain servlet filter
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
