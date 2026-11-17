package ru.ulstu.backend.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.ulstu.backend.entity.ProjectFile;

public interface ProjectFileRepository extends JpaRepository<ProjectFile, Long> {

  List<FileMetadata> findAllByProjectIdOrderById(Long projectId);

  Optional<ProjectFile> findByIdAndProjectId(Long id, Long projectId);

  long countByProjectId(Long projectId);

  @Modifying
  @Query("delete from ProjectFile file where file.project.id = :projectId")
  void deleteAllForProject(Long projectId);

  @Modifying
  @Query("delete from ProjectFile file where file.id = :id and file.project.id = :projectId")
  int deleteFromProject(Long id, Long projectId);

  record FileMetadata(Long id, String name, long size) {}
}
