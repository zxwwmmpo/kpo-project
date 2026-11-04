package ru.ulstu.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateStudentProfileRequest(
    @NotBlank @Size(max = 150) String fullName,
    @NotNull @Positive Long groupId,
    @Size(max = 500) String about) {}
