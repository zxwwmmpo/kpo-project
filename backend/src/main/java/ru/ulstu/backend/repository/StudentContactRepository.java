package ru.ulstu.backend.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.ulstu.backend.entity.StudentContact;

public interface StudentContactRepository extends JpaRepository<StudentContact, Long> {

  List<StudentContact> findByUserIdOrderByIdAsc(Long userId);

  Optional<StudentContact> findByIdAndUserId(Long id, Long userId);

  void deleteByUserId(Long userId);
}
