package ru.ulstu.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateStudyEntryRequest(
    @NotBlank @Size(max = 150) String name, @Positive Long parentId) {}
