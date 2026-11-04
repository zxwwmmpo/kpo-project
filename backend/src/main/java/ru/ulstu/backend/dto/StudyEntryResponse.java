package ru.ulstu.backend.dto;

import ru.ulstu.backend.entity.StudyEntryType;

public record StudyEntryResponse(Long id, StudyEntryType type, String name, Long parentId) {}
