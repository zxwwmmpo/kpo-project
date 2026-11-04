package ru.ulstu.backend.dto;

public record StudentProfileResponse(
    String email,
    String fullName,
    StudyEntryResponse faculty,
    StudyEntryResponse direction,
    StudyEntryResponse studyGroup,
    String about) {}
