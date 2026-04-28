package com.policyinsight.controller;

import com.policyinsight.service.BadUploadException;
import com.policyinsight.service.AccessDeniedException;
import com.policyinsight.service.NotFoundException;
import com.policyinsight.service.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.util.HtmlUtils;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadUploadException.class)
    public Object badUpload(BadUploadException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.BAD_REQUEST, "Upload could not be processed", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object accessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.FORBIDDEN, "Access unavailable", ex.getMessage(), request);
    }

    @ExceptionHandler(NotFoundException.class)
    public Object notFound(NotFoundException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public Object rateLimited(RateLimitExceededException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.TOO_MANY_REQUESTS, "Try again shortly", ex.getMessage(), request);
    }

    private Object errorResponse(HttpStatus status, String title, String message, HttpServletRequest request) {
        if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) {
            String safeTitle = HtmlUtils.htmlEscape(title);
            String safeMessage = HtmlUtils.htmlEscape(message);
            return ResponseEntity.status(status).body("""
                    <div class="status-card status-card-error">
                        <p class="section-kicker">%s</p>
                        <h3>%s</h3>
                        <p>%s</p>
                    </div>
                    """.formatted(status.value(), safeTitle, safeMessage));
        }
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(status);
        modelAndView.addObject("statusCode", status.value());
        modelAndView.addObject("errorTitle", title);
        modelAndView.addObject("safeErrorMessage", message);
        return modelAndView;
    }
}
