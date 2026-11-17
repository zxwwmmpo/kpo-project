package ru.ulstu.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.ulstu.backend.dto.StudentProfileResponse;
import ru.ulstu.backend.dto.StudyEntryResponse;
import ru.ulstu.backend.dto.UpdateStudentProfileRequest;
import ru.ulstu.backend.entity.StudentProfile;
import ru.ulstu.backend.entity.StudyEntry;
import ru.ulstu.backend.entity.StudyEntryType;
import ru.ulstu.backend.entity.User;
import ru.ulstu.backend.repository.StudentProfileRepository;
import ru.ulstu.backend.repository.StudyEntryRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentProfileService {

  private final StudentProfileRepository profileRepository;
  private final StudyEntryRepository studyEntryRepository;

  public StudentProfileResponse get(User user) {
    return profileRepository
        .findByUserId(user.getId())
        .map(profile -> toResponse(profile, user.getEmail()))
        .orElseGet(() -> new StudentProfileResponse(user.getEmail(), null, null, null, null, null));
  }

  @Transactional
  public StudentProfileResponse update(User user, UpdateStudentProfileRequest request) {
    StudyEntry group = requireGroup(request.groupId());
    String fullName = normalizeFullName(request.fullName());
    String about = normalizeAbout(request.about());

    StudentProfile profile =
        profileRepository
            .findByUserId(user.getId())
            .orElseGet(() -> StudentProfile.builder().user(user).build());

    profile.update(fullName, group, about);
    profileRepository.saveAndFlush(profile);

    return toResponse(profile, user.getEmail());
  }

  private StudyEntry requireGroup(Long groupId) {
    if (groupId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите группу");
    }

    StudyEntry group =
        studyEntryRepository
            .findById(groupId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Группа не найдена"));

    if (group.getType() != StudyEntryType.GROUP) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Выбранная запись не является группой");
    }

    return group;
  }

  private String normalizeFullName(String value) {
    if (value == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Введите ФИО");
    }

    String fullName = value.strip().replaceAll("\\s+", " ");

    if (fullName.isBlank() || fullName.length() > 150) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "ФИО должно содержать от 1 до 150 символов");
    }

    return fullName;
  }

  private String normalizeAbout(String value) {
    if (value == null) {
      return null;
    }

    if (value.length() > 500) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Поле «О себе» не должно превышать 500 символов");
    }

    String about = value.strip();
    return about.isBlank() ? null : about;
  }

  private StudentProfileResponse toResponse(StudentProfile profile, String email) {
    StudyEntry group = profile.getStudyGroup();
    StudyEntry direction = group.getParent();
    StudyEntry faculty = direction.getParent();

    return new StudentProfileResponse(
        email,
        profile.getFullName(),
        toStudyResponse(faculty),
        toStudyResponse(direction),
        toStudyResponse(group),
        profile.getAbout());
  }

  private StudyEntryResponse toStudyResponse(StudyEntry entry) {
    Long parentId = entry.getParent() == null ? null : entry.getParent().getId();
    return new StudyEntryResponse(entry.getId(), entry.getType(), entry.getName(), parentId);
  }
}
