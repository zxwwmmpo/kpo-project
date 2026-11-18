package ru.ulstu.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import ru.ulstu.backend.entity.StudentAchievement;

public interface StudentAchievementRepository extends JpaRepository<StudentAchievement, Long> {

  @Query(
      """
      select a.id, a.title, a.date, a.description, a.certificateName
      from StudentAchievement a where a.user.id = :userId
      order by a.date desc, a.id desc
      """)
  List<Summary> findSummaries(Long userId);

  Optional<StudentAchievement> findByIdAndUserIdAndCertificateNameIsNotNull(Long id, Long userId);

  @Transactional
  @Modifying
  @Query("delete from StudentAchievement a where a.id = :id and a.user.id = :userId")
  int deleteOwned(Long id, Long userId);

  record Summary(
      Long id, String title, LocalDate date, String description, String certificateName) {
    public static Summary from(StudentAchievement achievement) {
      return new Summary(
          achievement.getId(),
          achievement.getTitle(),
          achievement.getDate(),
          achievement.getDescription(),
          achievement.getCertificateName());
    }
  }
}
