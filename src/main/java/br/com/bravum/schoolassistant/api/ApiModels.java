package br.com.bravum.schoolassistant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {}

    public record TenantCreate(@NotBlank @Size(max = 180) String name) {}
    public record StudentCreate(@NotBlank @Size(max = 200) String displayName) {}
    public record ConnectionCreate(@NotBlank @Size(max = 60) String providerCode,
                                   @Size(max = 180) String connectionName) {}

    // Os objetos aninhados seguem o formato JSONL que o extrator do Mac produzirá.
    public record StudentContextInput(String stableStudentId, String contextKey,
                                      String displayName, String classroom, String mappingStatus) {}
    public record CommunicationInput(String title, String body, String category, String author,
                                     String publishedAt, String eventDate, String dueAt,
                                     List<String> attachments, String sourceUrl) {}
    public record ExtractionInput(String capturedAt, String sourceType,
                                  Boolean contentComplete, Boolean requiresReview) {}
    public record ImportRequest(String source, String externalCommunicationId,
                                StudentContextInput studentContext,
                                @NotNull CommunicationInput communication,
                                ExtractionInput extraction) {}
    public record ImportResult(UUID communicationId, boolean created, UUID contextId, String mappingStatus) {}

    public record MappingConfirm(@NotNull UUID studentId) {}
    public record AssignmentCreate(@NotNull UUID communicationId, UUID studentContextId,
                                   @NotBlank @Size(max = 350) String title, String description,
                                   String classification, Instant dueAt, String dueDateSource,
                                   String sourceItemKey) {}
    public record ReminderRuleCreate(@NotBlank String channel, @NotNull Integer minutesBefore) {}
    public record Summary(long students, long communications, long pendingAssignments,
                          long completedAssignments, long scheduledReminders) {}
}
