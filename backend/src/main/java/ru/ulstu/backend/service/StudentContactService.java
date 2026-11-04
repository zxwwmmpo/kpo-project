package ru.ulstu.backend.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.ulstu.backend.dto.StudentContactRequest;
import ru.ulstu.backend.dto.StudentContactResponse;
import ru.ulstu.backend.entity.StudentContact;
import ru.ulstu.backend.entity.User;
import ru.ulstu.backend.repository.StudentContactRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentContactService {

  private final StudentContactRepository repository;
  private final ContactValueValidator validator;

  public List<StudentContactResponse> list(User user) {
    return repository.findByUserIdOrderByIdAsc(user.getId()).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public StudentContactResponse create(User user, StudentContactRequest request) {
    String value = validator.normalize(request.type(), request.value());

    StudentContact contact =
        StudentContact.builder().user(user).type(request.type()).value(value).build();

    return toResponse(repository.saveAndFlush(contact));
  }

  @Transactional
  public StudentContactResponse update(User user, Long id, StudentContactRequest request) {
    StudentContact contact = requireContact(user.getId(), id);
    String value = validator.normalize(request.type(), request.value());

    contact.update(request.type(), value);
    repository.flush();

    return toResponse(contact);
  }

  @Transactional
  public void delete(User user, Long id) {
    StudentContact contact = requireContact(user.getId(), id);
    repository.delete(contact);
    repository.flush();
  }

  private StudentContact requireContact(Long userId, Long id) {
    return repository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Контакт не найден"));
  }

  private StudentContactResponse toResponse(StudentContact contact) {
    return new StudentContactResponse(contact.getId(), contact.getType(), contact.getValue());
  }
}
