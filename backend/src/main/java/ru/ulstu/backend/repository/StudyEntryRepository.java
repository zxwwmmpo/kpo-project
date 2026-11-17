package ru.ulstu.backend.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.ulstu.backend.entity.StudyEntry;
import ru.ulstu.backend.entity.StudyEntryType;

public interface StudyEntryRepository extends JpaRepository<StudyEntry, Long> {

  List<StudyEntry> findByTypeOrderByNameAsc(StudyEntryType type);

  List<StudyEntry> findByTypeAndParentIdOrderByNameAsc(StudyEntryType type, Long parentId);

  List<StudyEntry> findByParentIdOrderByIdAsc(Long parentId);

  boolean existsByCatalogKey(String catalogKey);

  boolean existsByCatalogKeyAndIdNot(String catalogKey, Long id);
}
