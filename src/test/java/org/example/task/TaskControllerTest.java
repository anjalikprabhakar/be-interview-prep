package org.example.task;

import org.example.common.exception.NotFoundException;
import org.example.task.dto.TaskRequest;
import org.example.task.dto.TaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TaskService service;

    @Test
    void create_validRequest_returns201WithLocation() throws Exception {
        LocalDate due = LocalDate.now().plusDays(1);
        given(service.create(any(TaskRequest.class))).willReturn(
                new TaskResponse(1L, "Write report", null, TaskStatus.TO_DO, due, Instant.now()));

        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "Write report", "status": "TO_DO", "dueDate": "%s"}
                        """.formatted(due)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/tasks/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("TO_DO"));
    }

    @Test
    void create_blankTitleMissingStatusAndPastDueDate_returns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": " ", "dueDate": "%s"}
                        """.formatted(LocalDate.now().minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/tasks"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(3)))
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("title", "status", "dueDate")));

        verifyNoInteractions(service);
    }

    @Test
    void create_title101Chars_returns400() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "%s", "status": "TO_DO"}
                        """.formatted("a".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("title must be at most 100 characters"));
    }

    @Test
    void create_unknownStatusValue_returns400WithAllowedValues() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "Write report", "status": "FINISHED"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"))
                .andExpect(jsonPath("$.fieldErrors[0].message", containsString("TO_DO, IN_PROGRESS, DONE")));
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void list_unknownStatusParam_returns400() throws Exception {
        mvc.perform(get("/api/tasks").param("status", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        given(service.get(99L)).willThrow(new NotFoundException("Task 99 not found"));

        mvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Task 99 not found"))
                .andExpect(jsonPath("$.path").value("/api/tasks/99"));
    }

    @Test
    void update_unknownId_returns404() throws Exception {
        given(service.update(eq(99L), any(TaskRequest.class))).willThrow(new NotFoundException("Task 99 not found"));

        mvc.perform(put("/api/tasks/99").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "Write report", "status": "DONE"}
                        """))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_blankTitleMissingStatusAndPastDueDate_returns400WithFieldErrors() throws Exception {
        mvc.perform(put("/api/tasks/1").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": " ", "dueDate": "%s"}
                        """.formatted(LocalDate.now().minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/tasks/1"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(3)))
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("title", "status", "dueDate")));

        verifyNoInteractions(service);
    }

    @Test
    void delete_unknownId_returns404() throws Exception {
        willThrow(new NotFoundException("Task 99 not found")).given(service).delete(99L);

        mvc.perform(delete("/api/tasks/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_unexpectedException_returns500WithoutInternalDetails() throws Exception {
        given(service.get(1L)).willThrow(new IllegalStateException("db password is hunter2"));

        mvc.perform(get("/api/tasks/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
