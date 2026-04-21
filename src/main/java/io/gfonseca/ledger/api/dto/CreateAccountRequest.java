package io.gfonseca.ledger.api.dto;

import io.gfonseca.ledger.domain.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateAccountRequest(
        @NotBlank String externalRef,
        @NotNull AccountType type,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @Size(max = 255) String ownerName,
        Map<String, Object> metadata) {}
