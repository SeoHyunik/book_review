package com.example.bookreview.exception;

import com.example.bookreview.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
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
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final int MAX_LOG_VALUE_LENGTH = 400;

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
        @NonNull HttpRequestMethodNotSupportedException ex,
        @NonNull HttpHeaders headers,
        @NonNull HttpStatusCode status,
        @NonNull WebRequest request) {

        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();

        // 로그에는 사용자 입력이 섞일 수 있는 값(URI 등)을 안전하게 정리해서 출력
        log.warn(
            "Method Not Supported: {} {} | trace={}",
            safeForLog(servletRequest.getMethod()),
            safeForLog(servletRequest.getRequestURI()),
            limitedStackTraceForLog(ex)
        );

        // 응답에는 예외 메시지(ex.getMessage())를 그대로 노출하지 않는다 (XSS/정보노출 방지)
        ErrorResponse error = new ErrorResponse(
            HttpStatus.METHOD_NOT_ALLOWED.value(),
            "Method Not Allowed",
            safeForResponsePath(servletRequest.getRequestURI()),
            false,
            OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        @NonNull HttpHeaders headers,
        @NonNull HttpStatusCode status,
        @NonNull WebRequest request) {

        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();

        // defaultMessage는 보통 서버가 정의한 문구지만, 안전을 위해 응답에 넣을 때 escape 처리
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> safeForResponseText(error.getField()) + "=" + safeForResponseText(error.getDefaultMessage()))
            .collect(Collectors.joining(", "));

        log.warn(
            "Validation Error: path={} | trace={}",
            safeForLog(servletRequest.getRequestURI()),
            limitedStackTraceForLog(ex)
        );

        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            message,
            safeForResponsePath(servletRequest.getRequestURI()),
            false,
            OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
        @NonNull HttpMessageNotReadableException ex,
        @NonNull HttpHeaders headers,
        @NonNull HttpStatusCode status,
        @NonNull WebRequest request) {

        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();

        log.warn(
            "Message Not Readable: path={} | trace={}",
            safeForLog(servletRequest.getRequestURI()),
            limitedStackTraceForLog(ex)
        );

        // payload 파싱 실패 시에도 내부 예외 메시지를 그대로 노출하지 않는다.
        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Malformed request payload",
            safeForResponsePath(servletRequest.getRequestURI()),
            false,
            OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {

        log.error(
            "Unhandled Exception: path={} | trace={}",
            safeForLog(request.getRequestURI()),
            limitedStackTraceForLog(ex)
        );

        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            safeForResponsePath(request.getRequestURI()),
            false,
            OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * XSS/로그 인젝션 방지:
     * - CR/LF/TAB 등 제어문자를 공백으로 치환
     * - 길이 제한
     */
    private String safeForLog(String value) {
        if (value == null) {
            return "-";
        }
        String cleaned = value
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\t", " ");
        if (cleaned.length() > MAX_LOG_VALUE_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LOG_VALUE_LENGTH) + "...";
        }
        return cleaned;
    }

    /**
     * 응답에 문자열을 포함해야 할 경우:
     * - HTML escape를 적용해, 템플릿/프론트에서 실수로 innerHTML로 렌더링하더라도 안전하게 만든다.
     */
    private String safeForResponseText(String value) {
        if (value == null) {
            return "";
        }
        return HtmlUtils.htmlEscape(value);
    }

    /**
     * path 역시 사용자 입력이 섞일 수 있으므로 escape 처리한다.
     */
    private String safeForResponsePath(String requestUri) {
        if (requestUri == null) {
            return "/";
        }
        return HtmlUtils.htmlEscape(requestUri);
    }

    /**
     * 스택트레이스 출력은 최소화하되, throwable.getMessage()는 포함하지 않는다.
     * (사용자 입력 반사/XSS/민감정보 노출 가능성 차단)
     */
    private String limitedStackTraceForLog(Throwable throwable) {
        if (throwable == null) {
            return "-";
        }
        return throwable.getClass().getName() + "\n" +
            Arrays.stream(throwable.getStackTrace())
                .limit(5)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining("\n"));
    }
}
