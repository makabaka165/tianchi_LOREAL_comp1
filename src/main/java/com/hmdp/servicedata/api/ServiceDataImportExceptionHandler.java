package com.hmdp.servicedata.api;

import com.hmdp.common.ErrorCode;
import com.hmdp.dto.Result;
import com.hmdp.exception.BusinessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import javax.validation.ConstraintViolationException;

/** Maps the import contract's canonical errors to its documented HTTP statuses. */
@RestControllerAdvice(assignableTypes = ServiceDataImportController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ServiceDataImportExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result> handleBusinessException(BusinessException error) {
        ErrorCode code = error.getErrorCode();
        return ResponseEntity.status(statusFor(code))
                .body(Result.fail(code, code.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result> handleOversizedUpload(MaxUploadSizeExceededException error) {
        ErrorCode code = ErrorCode.CS_IMPORT_VALIDATION_FAILED;
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Result.fail(code, code.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    public ResponseEntity<Result> handleInvalidRequest(Exception error) {
        ErrorCode code = ErrorCode.PARAM_ERROR;
        return ResponseEntity.badRequest().body(Result.fail(code, code.getMessage()));
    }

    private HttpStatus statusFor(ErrorCode code) {
        switch (code) {
            case CS_RESOURCE_NOT_FOUND:
                return HttpStatus.NOT_FOUND;
            case CS_IMPORT_CONFLICT:
                return HttpStatus.CONFLICT;
            case CS_IMPORT_VALIDATION_FAILED:
                return HttpStatus.UNPROCESSABLE_ENTITY;
            case CS_FEATURE_DISABLED:
                return HttpStatus.SERVICE_UNAVAILABLE;
            case PARAM_ERROR:
                return HttpStatus.BAD_REQUEST;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
