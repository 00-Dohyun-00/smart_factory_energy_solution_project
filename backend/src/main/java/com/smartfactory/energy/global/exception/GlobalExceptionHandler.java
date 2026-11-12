package com.smartfactory.energy.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모든 Controller에서 발생한 예외를 한곳에서 처리합니다.
 * 각 기능에서는 예외를 던지기만 하면 되고, 이 클래스는 수정하지 않아도 됩니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 데이터 없음 → 404
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return toResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // 잘못된 요청 값 → 400 (예: throw new IllegalArgumentException("이미 등록된 설비입니다."))
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return toResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // @Valid 검증 실패 (@NotBlank 등) → 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        var fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : "입력값이 올바르지 않습니다.";
        return toResponse(HttpStatus.BAD_REQUEST, message);
    }

    // JSON 형식 오류 → 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return toResponse(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
    }

    // 주소/파라미터 값의 타입 오류 (예: /api/devices/abc) → 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return toResponse(HttpStatus.BAD_REQUEST, e.getName() + ": 값의 형식이 올바르지 않습니다.");
    }

    // 그 외 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        // 스프링이 상태 코드를 정해둔 예외 (없는 주소 404, 허용되지 않은 메서드 405 등)
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
            String message = switch (status) {
                case NOT_FOUND -> "요청한 주소를 찾을 수 없습니다.";
                case METHOD_NOT_ALLOWED -> "지원하지 않는 요청 방식입니다.";
                default -> springError.getBody().getDetail();
            };
            return toResponse(status, message);
        }

        // 예상하지 못한 서버 오류 → 500 (원인은 서버 로그에서 확인)
        log.error("서버 오류", e);
        return toResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    private ResponseEntity<ErrorResponse> toResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message));
    }
}
