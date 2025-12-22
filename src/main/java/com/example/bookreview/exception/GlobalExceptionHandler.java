package com.example.bookreview.exception;

import com.example.bookreview.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        log.warn("Method Not Supported: {} {}\n{}", servletRequest.getMethod(), servletRequest.getRequestURI(),
                getLimitedStackTrace(ex));
        ErrorResponse error = new ErrorResponse(HttpStatus.METHOD_NOT_ALLOWED.value(), ex.getMessage(),
                servletRequest.getRequestURI(), false, OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + "=" + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation Error: {}\n{}", servletRequest.getRequestURI(), getLimitedStackTrace(ex));
        ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), message,
                servletRequest.getRequestURI(), false, OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        log.warn("Message Not Readable: {}\n{}", servletRequest.getRequestURI(), getLimitedStackTrace(ex));
        ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Malformed request payload",
                servletRequest.getRequestURI(), false, OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled Exception at {}\n{}", request.getRequestURI(), getLimitedStackTrace(ex));
        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                request.getRequestURI(), false, OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private String getLimitedStackTrace(Throwable throwable) {
        return throwable.getClass().getName() + ": " + throwable.getMessage() + "\n" +
                Arrays.stream(throwable.getStackTrace())
                        .limit(5)
                        .map(StackTraceElement::toString)
                        .collect(Collectors.joining("\n"));
    }
}
