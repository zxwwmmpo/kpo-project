package ru.ulstu.backend.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.ulstu.backend.entity.StudentProject;

public interface StudentProjectRepository extends JpaRepository<StudentProject, Long> {

  record Summary(
      Long id, String title, String description, String role, Long fileCount, Integer linkCount) {}

  @Query(
      """
      select p.id, p.title, p.description, p.role,
        (select count(f) from ProjectFile f where f.project = p), size(p.links)
      from StudentProject p where p.user.id = :userId order by p.id desc
      """)
  List<Summary> findSummaries(Long userId);

  Optional<StudentProject> findByIdAndUserId(Long id, Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select project from StudentProject project where project.id = :id and project.user.id = :userId")
  Optional<StudentProject> lockOwnedProject(Long id, Long userId);
}
