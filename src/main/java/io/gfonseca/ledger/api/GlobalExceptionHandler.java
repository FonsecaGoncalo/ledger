package io.gfonseca.ledger.api;

import io.gfonseca.ledger.api.dto.ErrorResponse;
import io.gfonseca.ledger.domain.exception.AccountNotFoundException;
import io.gfonseca.ledger.domain.exception.AlreadyReversedException;
import io.gfonseca.ledger.domain.exception.CurrencyMismatchException;
import io.gfonseca.ledger.domain.exception.DuplicateAccountException;
import io.gfonseca.ledger.domain.exception.DuplicateTransactionException;
import io.gfonseca.ledger.domain.exception.InsufficientFundsException;
import io.gfonseca.ledger.domain.exception.InvalidReversalException;
import io.gfonseca.ledger.domain.exception.InvalidTransactionException;
import io.gfonseca.ledger.domain.exception.OriginalTransactionNotFoundException;
import io.gfonseca.ledger.domain.exception.TransactionNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleAccountNotFound(AccountNotFoundException ex, HttpServletRequest req) {
        return error("ACCOUNT_NOT_FOUND", ex.getMessage(), Map.of("accountId", ex.accountId()), req);
    }

    @ExceptionHandler(DuplicateAccountException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse handleDuplicateAccount(DuplicateAccountException ex, HttpServletRequest req) {
        return error("DUPLICATE_ACCOUNT", ex.getMessage(), Map.of("externalRef", ex.externalRef()), req);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse handleInsufficientFunds(InsufficientFundsException ex, HttpServletRequest req) {
        return error(
                "INSUFFICIENT_FUNDS",
                ex.getMessage(),
                Map.of("accountId", ex.accountId(), "requested", ex.requested(), "available", ex.available()),
                req);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    ErrorResponse handleCurrencyMismatch(CurrencyMismatchException ex, HttpServletRequest req) {
        return error(
                "CURRENCY_MISMATCH",
                ex.getMessage(),
                Map.of(
                        "accountId",
                        ex.accountId(),
                        "requestedCurrency",
                        ex.requestedCurrency(),
                        "accountCurrency",
                        ex.accountCurrency()),
                req);
    }

    @ExceptionHandler(InvalidTransactionException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    ErrorResponse handleInvalidTransaction(InvalidTransactionException ex, HttpServletRequest req) {
        return error("INVALID_TRANSACTION", ex.getMessage(), Map.of(), req);
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse handleDuplicateTransaction(DuplicateTransactionException ex, HttpServletRequest req) {
        return error("DUPLICATE_TRANSACTION", ex.getMessage(), Map.of("idempotencyKey", ex.idempotencyKey()), req);
    }

    @ExceptionHandler(InvalidReversalException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    ErrorResponse handleInvalidReversal(InvalidReversalException ex, HttpServletRequest req) {
        return error("INVALID_REVERSAL", ex.getMessage(), Map.of(), req);
    }

    @ExceptionHandler(AlreadyReversedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse handleAlreadyReversed(AlreadyReversedException ex, HttpServletRequest req) {
        return error(
                "ALREADY_REVERSED", ex.getMessage(), Map.of("originalTransactionId", ex.originalTransactionId()), req);
    }

    @ExceptionHandler(OriginalTransactionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleOriginalNotFound(OriginalTransactionNotFoundException ex, HttpServletRequest req) {
        return error(
                "ORIGINAL_TRANSACTION_NOT_FOUND", ex.getMessage(), Map.of("transactionId", ex.transactionId()), req);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleTransactionNotFound(TransactionNotFoundException ex, HttpServletRequest req) {
        return error("TRANSACTION_NOT_FOUND", ex.getMessage(), Map.of("transactionId", ex.transactionId()), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, Object> details = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (a, b) -> a,
                        HashMap::new));
        return error("VALIDATION_ERROR", "Request validation failed", details, req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return error("BAD_REQUEST", ex.getMessage(), Map.of(), req);
    }

    private ErrorResponse error(String code, String message, Map<String, Object> details, HttpServletRequest req) {
        String traceId = (String) req.getAttribute("X-Request-Id");
        if (traceId == null) traceId = UUID.randomUUID().toString();
        log.atWarn()
                .addKeyValue("event", "request.rejected")
                .addKeyValue("error_code", code)
                .addKeyValue("http_method", req.getMethod())
                .addKeyValue("http_path", req.getRequestURI())
                .addKeyValue("trace_id", traceId)
                .addKeyValue("details", details)
                .setMessage(message)
                .log();
        return new ErrorResponse(code, message, details, Instant.now(), traceId);
    }
}
