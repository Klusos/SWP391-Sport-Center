package group5.example.sportCenter.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import group5.example.sportCenter.exception.ApiErrorResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ApiErrorResponseWriter errors;

    public ApiSecurityErrorHandler(ApiErrorResponseWriter errors) {
        this.errors = errors;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        errors.write(response, HttpStatus.UNAUTHORIZED,
                "Authentication is required or the access token is invalid");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        errors.write(response, HttpStatus.FORBIDDEN,
                "You are not allowed to perform this action");
    }
}
