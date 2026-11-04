package ru.ulstu.backend.service;

import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.ulstu.backend.dto.CreateStudyEntryRequest;
import ru.ulstu.backend.dto.RenameStudyEntryRequest;
import ru.ulstu.backend.dto.StudyEntryResponse;
import ru.ulstu.backend.entity.StudyEntry;
import ru.ulstu.backend.entity.StudyEntryType;
import ru.ulstu.backend.repository.StudentProfileRepository;
import ru.ulstu.backend.repository.StudyEntryRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyCatalogService {

  private final StudentProfileRepository profileRepository;

  private final StudyEntryRepository repository;

  public List<StudyEntryResponse> list(StudyEntryType type, Long parentId) {
    if (parentId == null) {
      return repository.findByTypeOrderByNameAsc(type).stream().map(this::toResponse).toList();
    }

    requireParent(type, parentId);

    return repository.findByTypeAndParentIdOrderByNameAsc(type, parentId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public StudyEntryResponse create(StudyEntryType type, CreateStudyEntryRequest request) {
    StudyEntry parent = requireParent(type, request.parentId());
    String name = normalizeName(request.name());
    String key = catalogKey(type, parent, name);

    if (repository.existsByCatalogKey(key)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Запись с таким названием уже существует");
    }

    StudyEntry entry =
        StudyEntry.builder().type(type).name(name).parent(parent).catalogKey(key).build();

    return toResponse(repository.saveAndFlush(entry));
  }

  @Transactional
  public StudyEntryResponse rename(StudyEntryType type, Long id, RenameStudyEntryRequest request) {
    StudyEntry entry = requireEntry(type, id);
    String name = normalizeName(request.name());
    String key = catalogKey(type, entry.getParent(), name);

    if (repository.existsByCatalogKeyAndIdNot(key, id)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Запись с таким названием уже существует");
    }

    entry.rename(name, key);
    repository.flush();

    return toResponse(entry);
  }

  @Transactional
  public void delete(StudyEntryType type, Long id) {
    StudyEntry entry = requireEntry(type, id);
    ensureTreeUnused(entry);
    deleteTree(entry);
  }

  private void ensureTreeUnused(StudyEntry entry) {
    if (entry.getType() == StudyEntryType.GROUP
        && profileRepository.existsByStudyGroupId(entry.getId())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Удаление запрещено: в группе «" + entry.getName() + "» есть студенты");
    }

    for (StudyEntry child : repository.findByParentIdOrderByIdAsc(entry.getId())) {
      ensureTreeUnused(child);
    }
  }

  private void deleteTree(StudyEntry entry) {
    for (StudyEntry child : repository.findByParentIdOrderByIdAsc(entry.getId())) {
      deleteTree(child);
    }

    repository.delete(entry);
    repository.flush();
  }

  private StudyEntry requireEntry(StudyEntryType type, Long id) {
    StudyEntry entry =
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Запись справочника не найдена"));

    if (entry.getType() != type) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись справочника не найдена");
    }

    return entry;
  }

  private StudyEntry requireParent(StudyEntryType type, Long parentId) {
    if (type == StudyEntryType.FACULTY) {
      if (parentId != null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "У факультета не должно быть родителя");
      }
      return null;
    }

    if (parentId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необходимо выбрать родителя");
    }

    StudyEntryType expectedType =
        type == StudyEntryType.DIRECTION ? StudyEntryType.FACULTY : StudyEntryType.DIRECTION;

    StudyEntry parent =
        repository
            .findById(parentId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Родительская запись не найдена"));

    if (parent.getType() != expectedType) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Родитель не соответствует уровню справочника");
    }

    return parent;
  }

  private String normalizeName(String value) {
    if (value == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Введите название");
    }

    String name = value.strip().replaceAll("\\s+", " ");

    if (name.isBlank() || name.length() > 150) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Название должно содержать от 1 до 150 символов");
    }

    return name;
  }

  private String catalogKey(StudyEntryType type, StudyEntry parent, String name) {
    String parentKey = parent == null ? "root" : parent.getId().toString();
    return type.name() + ":" + parentKey + ":" + name.toLowerCase(Locale.ROOT);
  }

  private StudyEntryResponse toResponse(StudyEntry entry) {
    Long parentId = entry.getParent() == null ? null : entry.getParent().getId();
    return new StudyEntryResponse(entry.getId(), entry.getType(), entry.getName(), parentId);
  }
}
