package br.com.bravum.schoolassistant.api;

import br.com.bravum.schoolassistant.model.*;
import br.com.bravum.schoolassistant.security.TenantAccess;
import br.com.bravum.schoolassistant.service.SchoolService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class SchoolController {
    private final SchoolService service;
    public SchoolController(SchoolService service) { this.service = service; }

    @PostMapping("/setup/tenant")
    public Tenant initialize(@Valid @RequestBody ApiModels.TenantCreate request) {
        return service.initialize(TenantAccess.tenantId(), request);
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    public Student createStudent(@Valid @RequestBody ApiModels.StudentCreate request) {
        return service.createStudent(TenantAccess.tenantId(), request);
    }

    @GetMapping("/students")
    public List<Student> students() { return service.students(TenantAccess.tenantId()); }

    @PostMapping("/integrations")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderConnection createIntegration(@Valid @RequestBody ApiModels.ConnectionCreate request) {
        return service.createConnection(TenantAccess.tenantId(), request);
    }

    @GetMapping("/integrations")
    public List<ProviderConnection> integrations() { return service.connections(TenantAccess.tenantId()); }

    @PostMapping("/integrations/{connectionId}/communications/import")
    public ApiModels.ImportResult importCommunication(
            @PathVariable UUID connectionId, @Valid @RequestBody ApiModels.ImportRequest request) {
        return service.importCommunication(TenantAccess.tenantId(), connectionId, request);
    }

    @GetMapping("/student-contexts")
    public List<StudentContext> contexts() { return service.contexts(TenantAccess.tenantId()); }

    @PostMapping("/student-contexts/{contextId}/confirm")
    public StudentContext confirmMapping(@PathVariable UUID contextId,
            @Valid @RequestBody ApiModels.MappingConfirm request) {
        return service.confirmMapping(TenantAccess.tenantId(), contextId, request.studentId());
    }

    @GetMapping("/communications")
    public List<Communication> communications() { return service.communications(TenantAccess.tenantId()); }

    @PostMapping("/communications/{communicationId}/review")
    public Communication reviewCommunication(@PathVariable UUID communicationId) {
        return service.reviewCommunication(TenantAccess.tenantId(), communicationId);
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public Assignment createAssignment(@Valid @RequestBody ApiModels.AssignmentCreate request) {
        return service.createAssignment(TenantAccess.tenantId(), request);
    }

    @GetMapping("/assignments")
    public List<Assignment> assignments() { return service.assignments(TenantAccess.tenantId()); }

    @GetMapping("/assignments/{id}")
    public Assignment assignment(@PathVariable UUID id) {
        return service.assignment(TenantAccess.tenantId(), id);
    }

    @GetMapping("/agenda")
    public List<Assignment> agenda() { return service.assignments(TenantAccess.tenantId()); }

    @PostMapping("/assignments/{id}/confirm")
    public Assignment confirmAssignment(@PathVariable UUID id) {
        return service.confirmAssignment(TenantAccess.tenantId(), id);
    }

    @PostMapping("/assignments/{id}/complete")
    public Assignment completeAssignment(@PathVariable UUID id) {
        return service.completeAssignment(TenantAccess.tenantId(), id);
    }

    @PostMapping("/reminder-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderRule createRule(@Valid @RequestBody ApiModels.ReminderRuleCreate request) {
        return service.createRule(TenantAccess.tenantId(), request);
    }

    @GetMapping("/reminder-rules")
    public List<ReminderRule> rules() { return service.rules(TenantAccess.tenantId()); }

    @GetMapping("/reminders")
    public List<ReminderJob> reminders() { return service.reminders(TenantAccess.tenantId()); }

    @GetMapping("/assistants/summary")
    public ApiModels.Summary summary() { return service.summary(TenantAccess.tenantId()); }
}
