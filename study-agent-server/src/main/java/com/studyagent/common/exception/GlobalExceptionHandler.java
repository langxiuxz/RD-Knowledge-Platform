package com.studyagent.common.exception;

import com.studyagent.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Result.failure(ErrorCode.VALIDATION_FAILED.code(),
                "请求 JSON 格式不合法"));
    }

    @ExceptionHandler({TypeMismatchException.class, HandlerMethodValidationException.class})
    public ResponseEntity<Result<Void>> handleInvalidParameter(Exception exception) {
        return ResponseEntity.badRequest().body(Result.failure(ErrorCode.VALIDATION_FAILED.code(),
                "请求参数格式不合法"));
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
            case USERNAME_EXISTS, PROJECT_CODE_EXISTS, PROJECT_MEMBER_EXISTS, PROJECT_LAST_OWNER,
                 PROJECT_ARCHIVED -> HttpStatus.CONFLICT;
            case DOCUMENT_VERSION_NOT_FOUND, KNOWLEDGE_SNAPSHOT_NOT_FOUND, KNOWLEDGE_BASE_NOT_FOUND, DOCUMENT_NOT_FOUND, FILE_NOT_FOUND, CONVERSATION_NOT_FOUND, MESSAGE_NOT_FOUND, QUIZ_NOT_FOUND,
                 PROJECT_NOT_FOUND, PROJECT_MEMBER_NOT_FOUND, PROJECT_MEMBER_USER_NOT_FOUND, ONBOARDING_PATH_NOT_FOUND, ONBOARDING_ASSIGNMENT_NOT_FOUND, CAPABILITY_REPORT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case DOCUMENT_STATE_CONFLICT, DOCUMENT_VERSION_REFERENCED, DOCUMENT_PROCESSING, GENERATION_CANCELLED, QUIZ_ALREADY_SUBMITTED, ONBOARDING_PREREQUISITE_NOT_MET, ONBOARDING_PATH_INVALID, ONBOARDING_ASSIGNMENT_INCOMPLETE -> HttpStatus.CONFLICT;
            case PROJECT_NOT_MEMBER, PROJECT_ROLE_INSUFFICIENT -> HttpStatus.FORBIDDEN;
            case AI_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case AI_SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DOCUMENT_PARSE_FAILED, VECTOR_RETRIEVAL_FAILED, VECTOR_DELETE_FAILED, QUIZ_GENERATION_FAILED,
                 STUDY_PLAN_GENERATION_FAILED, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
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
