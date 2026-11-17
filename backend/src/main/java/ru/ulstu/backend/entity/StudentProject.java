package ru.ulstu.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "student_projects")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StudentProject {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(nullable = false, length = 3000)
  private String description;

  @Column(name = "project_role", nullable = false, length = 100)
  private String role;

  @ElementCollection
  @OrderColumn(name = "link_index")
  @Builder.Default
  private List<ProjectLink> links = new ArrayList<>();

  public void update(String title, String description, String role) {
    this.title = title;
    this.description = description;
    this.role = role;
  }

  @Embeddable
  public record ProjectLink(
      @Column(nullable = false, length = 2048) String url,
      @Column(nullable = false, length = 100) String label) {}
}
