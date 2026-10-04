package com.studyagent.common.exception;

import com.studyagent.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<List<String>>> handleBodyValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(new Result<>(ErrorCode.VALIDATION_FAILED.code(),
                String.join("; ", details), details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(Result.failure(ErrorCode.VALIDATION_FAILED.code(),
                exception.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case UNAUTHORIZED, INVALID_CREDENTIALS, REFRESH_TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case USERNAME_EXISTS -> HttpStatus.CONFLICT;
            case KNOWLEDGE_BASE_NOT_FOUND, DOCUMENT_NOT_FOUND, CONVERSATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case DOCUMENT_PROCESSING, GENERATION_CANCELLED -> HttpStatus.CONFLICT;
            case AI_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case AI_SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DOCUMENT_PARSE_FAILED, VECTOR_RETRIEVAL_FAILED, VECTOR_DELETE_FAILED, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(Result.failure(exception.errorCode().code(), exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.failure(ErrorCode.FILE_TOO_LARGE.code(), ErrorCode.FILE_TOO_LARGE.message()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failure(ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.message()));
    }
}
