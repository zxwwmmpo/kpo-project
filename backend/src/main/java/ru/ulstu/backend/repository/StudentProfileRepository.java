package ru.ulstu.backend.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.ulstu.backend.entity.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

  Optional<StudentProfile> findByUserId(Long userId);

  boolean existsByStudyGroupId(Long groupId);

  void deleteByUserId(Long userId);
}
