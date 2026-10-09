package Elramy.Group.MafroshartElramyz.exception;

import Elramy.Group.MafroshartElramyz.exception.ApiError;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {

        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleEntityNotFound(EntityNotFoundException ex) {

        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "Account Disabled",
                "Your account has been disabled");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        // The raw database message names tables/constraints and echoes row
        // data, so it is logged here and never sent to the client.
        String detail = ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();
        log.warn("Data integrity violation: {}", detail);
        String msg = detail != null && (detail.contains("duplicate key") || detail.contains("unique"))
                ? "هذا السجل موجود بالفعل (قيمة مكررة)."
                : "تعذر حفظ البيانات لأنها تتعارض مع بيانات أخرى.";
        return build(HttpStatus.CONFLICT, "Conflict", msg);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    // Another sale/purchase is holding the same stock rows (or Postgres
    // broke a deadlock). Nothing was saved; retrying is safe.
    @ExceptionHandler({PessimisticLockingFailureException.class, CannotAcquireLockException.class})
    public ResponseEntity<ApiError> handleLockFailure(RuntimeException ex) {
        log.warn("Lock failure: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Conflict", "النظام مشغول بعملية أخرى على نفس المنتج. حاول مرة أخرى.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : "Validation failed";
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Error",
                message);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("Validation failed");
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Error",
                message);
        return ResponseEntity.badRequest().body(error);
    }

    // The domain exceptions below extend RuntimeException directly, so
    // without these handlers they surfaced as a generic 500 and the client
    // could not tell "not found" or "duplicate" apart from a crash.

    @ExceptionHandler({
            BranchNotFoundException.class,
            CustomerNotFoundException.class,
            CustomerPhoneNotFoundException.class,
            ProductNotFoundException.class,
            PurchaseInvoiceNotFoundException.class,
            SalesInvoiceNotFoundException.class,
            UserNotFoundException.class
    })
    public ResponseEntity<ApiError> handleDomainNotFound(RuntimeException ex) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler({
            DuplicateBranchException.class,
            DuplicateCustomerException.class,
            DuplicatePurchaseInvoiceException.class
    })
    public ResponseEntity<ApiError> handleDuplicate(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler({
            BranchAccessDeniedException.class,
            UnauthorizedException.class
    })
    public ResponseEntity<ApiError> handleForbidden(RuntimeException ex) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    // Selling a product that has no price yet is a request problem, not a missing resource.
    @ExceptionHandler(PriceNotFoundException.class)
    public ResponseEntity<ApiError> handlePriceMissing(PriceNotFoundException ex) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid username or password");
    }

    // A rejected bulk import: nothing was saved, every row problem is listed.
    @ExceptionHandler(ProductImportValidationException.class)
    public ResponseEntity<ImportErrorResponse> handleImportValidation(ProductImportValidationException ex) {
        return ResponseEntity.badRequest().body(new ImportErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Import Validation Error",
                ex.getMessage(),
                ex.getErrors()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large", "حجم الملف أكبر من الحد المسموح (5 ميجابايت).");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(LocalDateTime.now(), status.value(), error, message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(LoginBlockedException.class)
    public ResponseEntity<ApiError> handleLoginBlocked(LoginBlockedException ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", ex.getMessage());
    }

    // Security and Spring MVC exceptions keep their own status; anything
    // else is a bug: log it in full and send a generic message.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) throw ex;
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
            if (status == null) status = HttpStatus.BAD_REQUEST;
            return build(status, status.getReasonPhrase(), status.is4xxClientError() ? "طلب غير صالح." : "حدث خطأ غير متوقع.");
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return build(HttpStatus.BAD_REQUEST, "Bad Request", "صيغة البيانات المرسلة غير صحيحة.");
        }
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "حدث خطأ غير متوقع. حاول مرة أخرى.");
    }
}
