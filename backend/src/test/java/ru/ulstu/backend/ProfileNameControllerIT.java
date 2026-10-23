package ru.ulstu.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileNameControllerIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void returnsNormalizedName() throws Exception {
    mockMvc
        .perform(
            post("/api/profile/name/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  Иван Иванов  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Иван Иванов"));
  }

  @Test
  void rejectsBlankName() throws Exception {
    mockMvc
        .perform(
            post("/api/profile/name/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \"}"))
        .andExpect(status().isBadRequest());
  }
}
