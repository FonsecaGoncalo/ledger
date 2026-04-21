package io.gfonseca.ledger.api.dto;

import io.gfonseca.ledger.domain.model.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record SubmitTransactionRequest(
        @NotBlank String idempotencyKey,
        @NotNull TransactionType type,
        String description,
        @NotEmpty @Valid List<PostingInput> postings,
        String reversesTransactionId,
        Map<String, Object> metadata) {

    public record PostingInput(
            @NotBlank String accountId,
            long amount,

            @NotNull @Pattern(regexp = "[A-Z]{3}") @Size(min = 3, max = 3)
            String currency) {}
}
