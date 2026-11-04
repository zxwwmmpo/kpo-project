package ru.ulstu.backend.dto;

import ru.ulstu.backend.entity.ContactType;

public record StudentContactResponse(Long id, ContactType type, String value) {}
