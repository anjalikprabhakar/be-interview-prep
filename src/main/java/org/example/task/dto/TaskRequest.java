package org.example.task.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.task.TaskStatus;

import java.time.LocalDate;

/** Used for both POST (create) and PUT (full replace), so both share the same rules. */
public record TaskRequest(
        @NotBlank(message = "title is required")
        @Size(max = 100, message = "title must be at most 100 characters")
        String title,

        String description,

        @NotNull(message = "status is required")
        TaskStatus status,

        @FutureOrPresent(message = "dueDate must be today or in the future")
        LocalDate dueDate
) {
}
