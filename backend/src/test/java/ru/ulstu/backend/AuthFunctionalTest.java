package ru.ulstu.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFunctionalTest {

  private static final String PASSWORD = "Student123!";

  @LocalServerPort private int port;

  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void registrationImmediatelyAllowsAccountDeletion() throws Exception {
    String email = newEmail();

    HttpResponse<String> registration = postCredentials("/auth/register", email, PASSWORD);
    assertEquals(201, registration.statusCode());

    String cookie = sessionCookie(registration);
    assertEquals(204, deleteAccount(cookie).statusCode());

    assertEquals(401, postCredentials("/auth/login", email, PASSWORD).statusCode());
    assertEquals(401, deleteAccount(cookie).statusCode());
  }

  @Test
  void logoutEndsSessionAndUserCanLogInAgain() throws Exception {
    String email = newEmail();

    HttpResponse<String> registration = postCredentials("/auth/register", email, PASSWORD);
    assertEquals(201, registration.statusCode());
    String oldCookie = sessionCookie(registration);

    HttpRequest logout =
        request("/auth/logout")
            .header("Cookie", oldCookie)
            .header("X-Requested-With", "XMLHttpRequest")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    assertEquals(204, send(logout).statusCode());
    assertEquals(401, deleteAccount(oldCookie).statusCode());

    HttpResponse<String> login = postCredentials("/auth/login", email, PASSWORD);
    assertEquals(200, login.statusCode());

    assertEquals(204, deleteAccount(sessionCookie(login)).statusCode());
  }

  @Test
  void duplicateRegistrationReturnsConflict() throws Exception {
    String email = newEmail();

    assertEquals(201, postCredentials("/auth/register", email, PASSWORD).statusCode());
    assertEquals(409, postCredentials("/auth/register", email, PASSWORD).statusCode());
  }

  @Test
  void wrongPasswordReturnsUnauthorized() throws Exception {
    String email = newEmail();

    assertEquals(201, postCredentials("/auth/register", email, PASSWORD).statusCode());
    assertEquals(401, postCredentials("/auth/login", email, "WrongPassword!").statusCode());
  }

  @Test
  void shortPasswordReturnsBadRequest() throws Exception {
    assertEquals(400, postCredentials("/auth/register", newEmail(), "123").statusCode());
  }

  private HttpResponse<String> postCredentials(String path, String email, String password)
      throws Exception {
    String json =
        """
                {"email":"%s","password":"%s"}
                """
            .formatted(email, password);

    HttpRequest httpRequest =
        request(path)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

    return send(httpRequest);
  }

  private HttpResponse<String> deleteAccount(String cookie) throws Exception {
    HttpRequest httpRequest =
        request("/account")
            .header("Cookie", cookie)
            .header("X-Requested-With", "XMLHttpRequest")
            .DELETE()
            .build();

    return send(httpRequest);
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path));
  }

  private HttpResponse<String> send(HttpRequest httpRequest) throws Exception {
    return client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  private String sessionCookie(HttpResponse<String> response) {
    return response.headers().allValues("Set-Cookie").stream()
        .map(value -> value.split(";", 2)[0])
        .filter(value -> value.startsWith("JSESSIONID="))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Сервер не вернул cookie JSESSIONID"));
  }

  private String newEmail() {
    return "student-" + UUID.randomUUID() + "@example.com";
  }
}
