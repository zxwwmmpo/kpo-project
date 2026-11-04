package ru.ulstu.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
