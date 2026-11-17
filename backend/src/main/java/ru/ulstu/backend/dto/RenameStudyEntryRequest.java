package ru.ulstu.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameStudyEntryRequest(@NotBlank @Size(max = 150) String name) {}
