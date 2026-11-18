package ru.ulstu.backend;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.ulstu.backend.entity.User;
import ru.ulstu.backend.repository.ProjectFileRepository;
import ru.ulstu.backend.repository.UserRepository;

@SpringBootTest(properties = "app.admin.enabled=false")
@AutoConfigureMockMvc
class StudentAccountIT {

  private static final String PASSWORD = "Student123!";
  private static final String AJAX_HEADER = "X-Requested-With";
  private static final String AJAX_VALUE = "XMLHttpRequest";

  @TempDir static Path photoDirectory;

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ProjectFileRepository projectFileRepository;

  private MockHttpSession admin;
  private MockHttpSession student;

  @DynamicPropertySource
  static void configurePhotoDirectory(DynamicPropertyRegistry registry) {
    registry.add("app.profile-photo.directory", () -> photoDirectory.toString());
  }

  @BeforeEach
  void setUp() throws Exception {
    String adminEmail = newEmail();

    userRepository.saveAndFlush(User.administrator(adminEmail, passwordEncoder.encode(PASSWORD)));

    admin = authenticate("/api/auth/login", adminEmail, 200);
    student = authenticate("/api/auth/register", newEmail(), 201);
  }

  @Test
  void savesAchievementsAtDateBoundariesAndRestrictsCertificateDownload() throws Exception {
    String path = "/api/profile/achievements";
    byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
    var certificate = new MockMultipartFile("certificate", "Диплом.pdf", "application/pdf", pdf);
    var other = authenticate("/api/auth/register", newEmail(), 201);
    mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(path).session(admin)).andExpect(status().isForbidden());

    var created =
        mockMvc
            .perform(
                multipart(path)
                    .file(certificate)
                    .param("title", " Олимпиада ")
                    .param("date", "2026-12-31")
                    .param("description", "Первое место")
                    .session(student)
                    .header(AJAX_HEADER, AJAX_VALUE))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Олимпиада"))
            .andExpect(jsonPath("$.date").value("2026-12-31"))
            .andReturn();
    int id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    mockMvc
        .perform(
            multipart(path)
                .file(certificate)
                .param("title", "Выставка")
                .param("date", "1926-01-01")
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.description").value(""));

    mockMvc
        .perform(get(path).session(student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].certificateName").value("Диплом.pdf"))
        .andExpect(jsonPath("$[0].description").value("Первое место"))
        .andExpect(jsonPath("$[1].title").value("Выставка"));
    mockMvc.perform(get(path).session(other)).andExpect(jsonPath("$.length()").value(0));
    String download = path + "/" + id + "/certificate";
    mockMvc.perform(get(download).session(other)).andExpect(status().isNotFound());
    mockMvc
        .perform(get(download).session(student))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", startsWith("attachment")))
        .andExpect(content().bytes(pdf));
    mockMvc
        .perform(delete(path + "/" + id).session(other).header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isNotFound());
    mockMvc.perform(get(download).session(student)).andExpect(status().isOk());
    mockMvc
        .perform(delete(path + "/" + id).session(student).header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isNoContent());
    mockMvc.perform(get(download).session(student)).andExpect(status().isNotFound());
    mockMvc
        .perform(get(path).session(student))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].title").value("Выставка"));
  }

  @Test
  void rejectsInvalidAchievementsAndCertificatesWithoutSaving() throws Exception {
    String path = "/api/profile/achievements";
    var certificate =
        new MockMultipartFile(
            "certificate",
            "Диплом.pdf",
            "application/pdf",
            "%PDF-1.7".getBytes(StandardCharsets.UTF_8));
    for (String date : new String[] {"1111-11-11", "1925-12-31", "2027-01-01"}) {
      mockMvc
          .perform(
              multipart(path)
                  .file(certificate)
                  .param("title", "Олимпиада")
                  .param("date", date)
                  .session(student)
                  .header(AJAX_HEADER, AJAX_VALUE))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("Укажите дату с 01.01.1926 по 31.12.2026."));
    }
    mockMvc
        .perform(
            multipart(path)
                .param("title", "Олимпиада")
                .param("date", "2026-11-17")
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Прикрепите сертификат."));
    var invalid =
        new MockMultipartFile("certificate", "fake.pdf", "application/pdf", new byte[] {1});
    var oversized =
        new MockMultipartFile(
            "certificate", "big.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1]);
    mockMvc
        .perform(
            multipart(path)
                .param("title", "Олимпиада")
                .param("date", "invalid")
                .param("description", "Первое место")
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            multipart(path)
                .file(invalid)
                .param("title", "Олимпиада")
                .param("date", "2026-11-17")
                .param("description", "Первое место")
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            multipart(path)
                .file(oversized)
                .param("title", "Олимпиада")
                .param("date", "2026-11-17")
                .param("description", "Первое место")
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().is(413));
    mockMvc.perform(get(path).session(student)).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void accessDependsOnAuthenticationAndRole() throws Exception {
    mockMvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());

    mockMvc.perform(get("/api/profile").session(admin)).andExpect(status().isForbidden());

    mockMvc.perform(get("/api/catalog/faculties").session(student)).andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/catalog/faculties")
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ФИСТ\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void studentsCanCreateAndListOnlyTheirOwnProjects() throws Exception {
    String path = "/api/profile/projects";
    var other = authenticate("/api/auth/register", newEmail(), 201);
    mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(path).session(admin)).andExpect(status().isForbidden());
    mockMvc.perform(get(path).session(student)).andExpect(jsonPath("$.length()").value(0));

    mockMvc
        .perform(
            post(path)
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  \",\"description\":\"Описание\"}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post(path)
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  Расписание  \",\"description\":\"Описание\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.title").value("Расписание"))
        .andExpect(jsonPath("$.role").value(""));

    mockMvc
        .perform(get(path).session(student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].description").value("Описание"));
    mockMvc
        .perform(get(path).session(other))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void ownerUpdatesProjectAndManagesMaterials() throws Exception {
    String collection = "/api/profile/projects";
    var other = authenticate("/api/auth/register", newEmail(), 201);
    String projectBody = "{\"title\":\"Расписание\",\"description\":\"Описание\"}";
    MvcResult created =
        mockMvc
            .perform(
                post(collection)
                    .session(student)
                    .header(AJAX_HEADER, AJAX_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(projectBody))
            .andExpect(status().isCreated())
            .andReturn();
    int projectId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    String path = collection + "/" + projectId;
    String links = path + "/links";
    String files = path + "/files";

    mockMvc.perform(get(path).session(other)).andExpect(status().isNotFound());
    mockMvc
        .perform(
            put(path)
                .session(other)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(projectBody))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(path).session(other).header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            put(path)
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"Новый проект\",\"description\":\"Описание\",\"role\":\"Автор\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Новый проект"));

    for (String invalidUrl :
        new String[] {"javascript:alert(1)", "https:example.org", "https://["}) {
      mockMvc
          .perform(
              post(links)
                  .session(student)
                  .header(AJAX_HEADER, AJAX_VALUE)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"url\":\"" + invalidUrl + "\"}"))
          .andExpect(status().isBadRequest());
    }
    mockMvc
        .perform(
            post(links)
                .session(other)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.org\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post(links)
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.org\",\"label\":\"Демо\"}"))
        .andExpect(status().isCreated());

    byte[] pdf = "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.UTF_8);
    var file = new MockMultipartFile("file", "Отчёт.pdf", "application/pdf", pdf);
    var fake = new MockMultipartFile("file", "fake.pdf", "application/pdf", new byte[] {1, 2});
    var oversized =
        new MockMultipartFile(
            "file", "large.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1]);
    mockMvc
        .perform(multipart(files).file(fake).session(student).header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(multipart(files).file(oversized).session(student).header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().is(413));
    mockMvc
        .perform(multipart(files).file(file).session(other).header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isNotFound());

    int fileId = uploadProjectFile(files, file);
    mockMvc
        .perform(get(collection).session(student))
        .andExpect(jsonPath("$[0].fileCount").value(1))
        .andExpect(jsonPath("$[0].linkCount").value(1));
    String download = files + "/" + fileId;
    mockMvc
        .perform(get(path).session(student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.project.title").value("Новый проект"))
        .andExpect(jsonPath("$.files[0].name").value("Отчёт.pdf"))
        .andExpect(jsonPath("$.files[0].size").value(pdf.length))
        .andExpect(jsonPath("$.files[0].content").doesNotExist())
        .andExpect(jsonPath("$.links[0].url").value("https://example.org"))
        .andExpect(jsonPath("$.links[0].label").value("Демо"));
    mockMvc
        .perform(get(download).session(student))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", startsWith("attachment")))
        .andExpect(content().bytes(pdf));
    mockMvc.perform(get(download).session(other)).andExpect(status().isNotFound());
    mockMvc
        .perform(delete(download).session(other).header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(download).session(student).header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isNoContent());
    mockMvc.perform(get(download).session(student)).andExpect(status().isNotFound());

    for (int i = 0; i < 5; i++) {
      uploadProjectFile(files, file);
    }
    mockMvc
        .perform(multipart(files).file(file).session(student).header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isConflict());
    mockMvc
        .perform(delete(path).session(student).header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isNoContent());
    mockMvc.perform(get(path).session(student)).andExpect(status().isNotFound());
    assertEquals(0L, projectFileRepository.countByProjectId((long) projectId));
  }

  private int uploadProjectFile(String path, MockMultipartFile file) throws Exception {
    var result =
        mockMvc
            .perform(multipart(path).file(file).session(student).header(AJAX_HEADER, AJAX_VALUE))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  void deletingFacultyDeletesItsUnusedDirectionsAndGroups() throws Exception {
    long facultyId = createEntry("faculties", "Факультет " + UUID.randomUUID(), null);
    long directionId = createEntry("directions", "Программная инженерия", facultyId);
    long groupId = createEntry("groups", "ПИбд-41", directionId);

    deleteEntry("faculties", facultyId, 204);
    deleteEntry("directions", directionId, 404);
    deleteEntry("groups", groupId, 404);
  }

  @Test
  void profileUsesCatalogNamesAndPreventsDeletionOfOccupiedGroup() throws Exception {
    long facultyId = createEntry("faculties", "Факультет " + UUID.randomUUID(), null);
    long directionId = createEntry("directions", "Программная инженерия", facultyId);
    long groupId = createEntry("groups", "ПИбд-41", directionId);

    mockMvc
        .perform(
            put("/api/profile")
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileBody(groupId, "Изучаю Java.")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Иванов Иван Иванович"))
        .andExpect(jsonPath("$.studyGroup.id").value(groupId))
        .andExpect(jsonPath("$.direction.id").value(directionId))
        .andExpect(jsonPath("$.faculty.id").value(facultyId));

    mockMvc
        .perform(
            patch("/api/catalog/groups/{id}", groupId)
                .session(admin)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ПИбд-42\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/profile").session(student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studyGroup.name").value("ПИбд-42"));

    deleteEntry("groups", groupId, 409);
    deleteEntry("directions", directionId, 409);
    deleteEntry("faculties", facultyId, 409);

    mockMvc
        .perform(get("/api/profile").session(student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studyGroup.id").value(groupId))
        .andExpect(jsonPath("$.faculty.id").value(facultyId));

    mockMvc
        .perform(
            put("/api/profile")
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileBody(groupId, "Я".repeat(501))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void contactsAreValidatedAndAccessibleOnlyToTheirOwner() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/profile/contacts")
                    .session(student)
                    .header(AJAX_HEADER, AJAX_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"PHONE\",\"value\":\"+7 (999) 123-45-67\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.value").value("+79991234567"))
            .andReturn();

    long contactId = responseId(created);
    MockHttpSession anotherStudent = authenticate("/api/auth/register", newEmail(), 201);

    mockMvc
        .perform(get("/api/profile/contacts").session(anotherStudent))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc
        .perform(
            delete("/api/profile/contacts/{id}", contactId)
                .session(anotherStudent)
                .header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            put("/api/profile/contacts/{id}", contactId)
                .session(anotherStudent)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"URL\",\"value\":\"https://example.com\"}"))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            put("/api/profile/contacts/{id}", contactId)
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"GITHUB\",\"value\":\"https://example.com/student\"}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            put("/api/profile/contacts/{id}", contactId)
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"URL\",\"value\":\"javascript:alert(1)\"}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            put("/api/profile/contacts/{id}", contactId)
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"GITHUB\",\"value\":\"https://github.com/student\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("GITHUB"));

    mockMvc
        .perform(
            delete("/api/profile/contacts/{id}", contactId)
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/profile/contacts").session(student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void photoCanBeUploadedReplacedAndDeletedWithoutAffectingAnotherStudent() throws Exception {
    MockHttpSession anotherStudent = authenticate("/api/auth/register", newEmail(), 201);

    uploadPhoto(student, 0xFF0000);
    uploadPhoto(anotherStudent, 0x00FF00);
    uploadPhoto(student, 0x0000FF);

    assertEquals(0x0000FF, photoColor(student));
    assertEquals(0x00FF00, photoColor(anotherStudent));

    MockMultipartFile invalid =
        new MockMultipartFile("file", "fake.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1, 2, 3});

    mockMvc
        .perform(
            multipart("/api/profile/photo")
                .file(invalid)
                .session(student)
                .header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isBadRequest());

    assertEquals(0x0000FF, photoColor(student));

    mockMvc
        .perform(delete("/api/profile/photo").session(student).header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/profile/photo").session(student)).andExpect(status().isNotFound());

    assertEquals(0x00FF00, photoColor(anotherStudent));
  }

  private MockHttpSession authenticate(String endpoint, String email, int expectedStatus)
      throws Exception {
    String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);

    MvcResult result =
        mockMvc
            .perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is(expectedStatus))
            .andReturn();

    return (MockHttpSession) result.getRequest().getSession(false);
  }

  private long createEntry(String type, String name, Long parentId) throws Exception {
    String body = "{\"name\":\"%s\",\"parentId\":%s}".formatted(name, parentId);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/catalog/{type}", type)
                    .session(admin)
                    .header(AJAX_HEADER, AJAX_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    return responseId(result);
  }

  private void deleteEntry(String type, long id, int expectedStatus) throws Exception {
    mockMvc
        .perform(
            delete("/api/catalog/{type}/{id}", type, id)
                .session(admin)
                .header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().is(expectedStatus));
  }

  private String profileBody(long groupId, String about) {
    return """
        {"fullName":"Иванов Иван Иванович","groupId":%d,"about":"%s"}
        """
        .formatted(groupId, about);
  }

  private long responseId(MvcResult result) throws Exception {
    Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    return id.longValue();
  }

  private void uploadPhoto(MockHttpSession session, int color) throws Exception {
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, color);

    byte[] bytes;
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", output);
      bytes = output.toByteArray();
    } finally {
      image.flush();
    }

    MockMultipartFile file =
        new MockMultipartFile("file", "photo.png", MediaType.IMAGE_PNG_VALUE, bytes);

    mockMvc
        .perform(
            multipart("/api/profile/photo")
                .file(file)
                .session(session)
                .header(AJAX_HEADER, AJAX_VALUE))
        .andExpect(status().isNoContent());
  }

  private int photoColor(MockHttpSession session) throws Exception {
    byte[] bytes =
        mockMvc
            .perform(get("/api/profile/photo").session(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
      BufferedImage image = ImageIO.read(input);
      try {
        return image.getRGB(0, 0) & 0xFFFFFF;
      } finally {
        image.flush();
      }
    }
  }

  private String newEmail() {
    return "student-" + UUID.randomUUID() + "@example.com";
  }
}
