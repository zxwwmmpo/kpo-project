package ru.ulstu.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.ulstu.backend.entity.ContactType;

public record StudentContactRequest(
    @NotNull ContactType type, @NotBlank @Size(max = 2048) String value) {}
