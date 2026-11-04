package ru.ulstu.backend.dto;

import ru.ulstu.backend.entity.Role;

public record CurrentUserResponse(Long id, String email, Role role) {}
