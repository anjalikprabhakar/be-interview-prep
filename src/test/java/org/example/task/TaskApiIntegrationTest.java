package org.example.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full stack against the real (H2) database: controller → service → JPA → Flyway schema. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class TaskApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TaskRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void crudFlow_createGetUpdateDelete_works() throws Exception {
        JsonNode created = createTask("Write report", "TO_DO");
        long id = created.get("id").asLong();
        String createdAt = created.get("createdAt").asText();

        mvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Write report"));

        // createdAt sent by the client is ignored, and the stored one never changes on update.
        mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "Write final report", "status": "IN_PROGRESS",
                         "dueDate": "%s", "createdAt": "2000-01-01T00:00:00Z"}
                        """.formatted(LocalDate.now().plusDays(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Write final report"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.createdAt").value(createdAt));

        assertThat(repository.findById(id).orElseThrow().getCreatedAt().toString()).isEqualTo(createdAt);

        mvc.perform(delete("/api/tasks/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void list_filterByStatus_returnsOnlyMatching() throws Exception {
        createTask("A", "TO_DO");
        createTask("B", "DONE");
        createTask("C", "TO_DO");

        mvc.perform(get("/api/tasks").param("status", "TO_DO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("A"))
                .andExpect(jsonPath("$[1].title").value("C"));

        mvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void unknownPath_returns404InSameErrorFormat() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/does-not-exist"));
    }

    private JsonNode createTask(String title, String status) throws Exception {
        String body = mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "%s", "status": "%s"}
                        """.formatted(title, status)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
