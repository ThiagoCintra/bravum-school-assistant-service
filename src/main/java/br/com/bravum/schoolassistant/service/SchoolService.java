package br.com.bravum.schoolassistant.service;

import br.com.bravum.schoolassistant.api.ApiModels;
import br.com.bravum.schoolassistant.model.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SchoolService {
    @PersistenceContext
    private EntityManager em;

    private static final Set<String> CLASSIFICATIONS = Set.of(
        "HOMEWORK", "SCHOOL_EVENT", "MATERIAL_REQUEST", "GENERAL_NOTICE", "UNKNOWN");
    private static final Set<String> CHANNELS = Set.of("ALEXA", "MOBILE_PUSH", "CHATGPT", "EMAIL");

    @Transactional
    public Tenant initialize(UUID tenantId, ApiModels.TenantCreate input) {
        Tenant existing = em.find(Tenant.class, tenantId);
        if (existing != null) return existing;
        Instant now = Instant.now();
        Tenant tenant = new Tenant();
        tenant.id = tenantId;
        tenant.name = input.name().trim();
        tenant.status = "ACTIVE";
        tenant.createdAt = now;
        tenant.updatedAt = now;
        em.persist(tenant);
        return tenant;
    }

    @Transactional
    public Student createStudent(UUID tenantId, ApiModels.StudentCreate input) {
        requireTenant(tenantId);
        Student item = new Student();
        item.id = UUID.randomUUID();
        item.tenantId = tenantId;
        item.displayName = input.displayName().trim();
        item.status = "ACTIVE";
        item.createdAt = Instant.now();
        item.updatedAt = item.createdAt;
        em.persist(item);
        return item;
    }

    @Transactional(readOnly = true)
    public List<Student> students(UUID tenantId) {
        return em.createQuery("select s from Student s where s.tenantId=:t order by s.displayName", Student.class)
                .setParameter("t", tenantId).getResultList();
    }

    @Transactional
    public ProviderConnection createConnection(UUID tenantId, ApiModels.ConnectionCreate input) {
        requireTenant(tenantId);
        ProviderConnection c = new ProviderConnection();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId;
        c.providerCode = input.providerCode().trim().toUpperCase(Locale.ROOT);
        c.connectionName = input.connectionName();
        c.status = "PENDING";
        c.createdAt = Instant.now();
        c.updatedAt = c.createdAt;
        em.persist(c);
        return c;
    }

    @Transactional(readOnly = true)
    public List<ProviderConnection> connections(UUID tenantId) {
        return em.createQuery("select c from ProviderConnection c where c.tenantId=:t order by c.createdAt desc",
                        ProviderConnection.class)
                .setParameter("t", tenantId).getResultList();
    }

    @Transactional
    public ApiModels.ImportResult importCommunication(UUID tenantId, UUID connectionId, ApiModels.ImportRequest input) {
        ProviderConnection connection = em.find(ProviderConnection.class, connectionId);
        if (connection == null || !tenantId.equals(connection.tenantId)) throw notFound("Integração");
        if (!connection.providerCode.equalsIgnoreCase(input.source() == null ? "" : input.source()))
            throw badRequest("A origem não corresponde à integração");
        ApiModels.CommunicationInput payload = input.communication();
        if (payload == null || (blank(payload.title()) && blank(payload.body())))
            throw badRequest("Comunicado sem título ou texto");

        String fingerprint = Fingerprint.communication(connectionId, input.source(),
                input.externalCommunicationId(), payload.title(), payload.body(), payload.category(),
                payload.publishedAt());
        List<Communication> existing = em.createQuery(
                "select c from Communication c where c.tenantId=:t and c.sourceFingerprint=:f",
                Communication.class).setParameter("t", tenantId)
                .setParameter("f", fingerprint).setMaxResults(1).getResultList();

        Communication c;
        boolean created = existing.isEmpty();
        if (created) {
            c = new Communication();
            c.id = UUID.randomUUID();
            c.tenantId = tenantId;
            c.connectionId = connectionId;
            c.sourceFingerprint = fingerprint;
            c.externalCommunicationId = trimToNull(input.externalCommunicationId());
            c.title = trimToNull(payload.title());
            c.body = payload.body();
            c.category = trimToNull(payload.category());
            c.publishedAt = parseTimestamp(payload.publishedAt());
            c.eventAt = parseTimestamp(payload.eventDate());
            c.capturedAt = input.extraction() == null ? Instant.now()
                    : orNow(parseTimestamp(input.extraction().capturedAt()));
            c.sourceType = input.extraction() == null || blank(input.extraction().sourceType())
                    ? "VISIBLE_DOM" : input.extraction().sourceType().trim();
            c.contentComplete = input.extraction() != null && Boolean.TRUE.equals(input.extraction().contentComplete());
            c.reviewStatus = "PENDING";
            c.createdAt = Instant.now();
            c.updatedAt = c.createdAt;
            em.persist(c);
        } else {
            c = existing.getFirst();
        }

        StudentContext context = resolveContext(tenantId, connectionId, input.studentContext());
        if (context != null) {
            List<CommunicationRecipient> current = em.createQuery(
                    "select r from CommunicationRecipient r where r.tenantId=:t"
                    + " and r.communicationId=:c and r.studentContextId=:ctx",
                    CommunicationRecipient.class).setParameter("t", tenantId)
                    .setParameter("c", c.id).setParameter("ctx", context.id)
                    .setMaxResults(1).getResultList();
            if (current.isEmpty()) {
                CommunicationRecipient r = new CommunicationRecipient();
                r.id = UUID.randomUUID();
                r.tenantId = tenantId;
                r.communicationId = c.id;
                r.studentContextId = context.id;
                r.studentId = "CONFIRMED".equals(context.mappingStatus) ? context.studentId : null;
                r.mappingStatus = r.studentId == null ? "UNCONFIRMED" : "CONFIRMED";
                r.createdAt = Instant.now();
                em.persist(r);
            }
        }
        return new ApiModels.ImportResult(c.id, created, context == null ? null : context.id,
                context == null ? "UNCONFIRMED" : context.mappingStatus);
    }

    private StudentContext resolveContext(UUID tenantId, UUID connectionId, ApiModels.StudentContextInput source) {
        if (source == null || blank(source.contextKey())) return null;
        String key = source.contextKey().trim();
        if (key.length() > 200) throw badRequest("contextKey excede 200 caracteres");
        List<StudentContext> matches = em.createQuery(
                "select ctx from StudentContext ctx where ctx.tenantId=:t"
                + " and ctx.connectionId=:c and ctx.contextKey=:key", StudentContext.class)
                .setParameter("t", tenantId).setParameter("c", connectionId)
                .setParameter("key", key).setMaxResults(1).getResultList();
        if (!matches.isEmpty()) {
            StudentContext ctx = matches.getFirst();
            ctx.lastSeenAt = Instant.now();
            // Nunca atribuir aluno a partir de posição de componente JSF.
            return ctx;
        }
        StudentContext ctx = new StudentContext();
        ctx.id = UUID.randomUUID();
        ctx.tenantId = tenantId;
        ctx.connectionId = connectionId;
        ctx.contextKey = key;
        ctx.externalStudentId = trimToNull(source.stableStudentId());
        ctx.mappingStatus = "UNCONFIRMED";
        ctx.studentId = null;
        ctx.lastSeenAt = Instant.now();
        ctx.createdAt = ctx.lastSeenAt;
        em.persist(ctx);
        return ctx;
    }

    @Transactional
    public StudentContext confirmMapping(UUID tenantId, UUID contextId, UUID studentId) {
        StudentContext context = em.find(StudentContext.class, contextId);
        Student student = em.find(Student.class, studentId);
        if (context == null || !tenantId.equals(context.tenantId)) throw notFound("Contexto");
        if (student == null || !tenantId.equals(student.tenantId)) throw notFound("Aluna");
        if (blank(context.externalStudentId))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "É necessário um identificador externo estável; índices JSF não são suficientes");
        context.studentId = studentId;
        context.mappingStatus = "CONFIRMED";
        List<CommunicationRecipient> recipients = em.createQuery(
                "select r from CommunicationRecipient r where r.tenantId=:t and r.studentContextId=:ctx",
                CommunicationRecipient.class).setParameter("t", tenantId)
                .setParameter("ctx", context.id).getResultList();
        for (CommunicationRecipient recipient : recipients) {
            recipient.studentId = studentId;
            recipient.mappingStatus = "CONFIRMED";
        }
        List<Assignment> assignments = em.createQuery(
                "select a from Assignment a where a.tenantId=:t and a.studentContextId=:ctx",
                Assignment.class).setParameter("t", tenantId)
                .setParameter("ctx", context.id).getResultList();
        for (Assignment a : assignments) { a.studentId = studentId; a.updatedAt = Instant.now(); }
        return context;
    }

    @Transactional(readOnly = true)
    public List<StudentContext> contexts(UUID tenantId) {
        return em.createQuery("select c from StudentContext c where c.tenantId=:t order by c.createdAt",
                StudentContext.class).setParameter("t", tenantId).getResultList();
    }

    @Transactional(readOnly = true)
    public List<Communication> communications(UUID tenantId) {
        return em.createQuery(
                "select c from Communication c where c.tenantId=:t order by c.capturedAt desc",
                Communication.class).setParameter("t", tenantId).getResultList();
    }

    @Transactional
    public Assignment createAssignment(UUID tenantId, ApiModels.AssignmentCreate input) {
        requireTenant(tenantId);
        Communication communication = em.find(Communication.class, input.communicationId());
        if (communication == null || !tenantId.equals(communication.tenantId)) throw notFound("Comunicado");
        StudentContext context = null;
        if (input.studentContextId() != null) {
            context = em.find(StudentContext.class, input.studentContextId());
            if (context == null || !tenantId.equals(context.tenantId)
                    || !context.connectionId.equals(communication.connectionId))
                throw notFound("Contexto do comunicado");
        }
        if (input.dueAt() != null && blank(input.dueDateSource()))
            throw badRequest("Prazo requer evidência em dueDateSource");
        String type = blank(input.classification()) ? "UNKNOWN" : input.classification().trim().toUpperCase(Locale.ROOT);
        if (!CLASSIFICATIONS.contains(type)) throw badRequest("Classificação inválida");
        String key = blank(input.sourceItemKey()) ? "primary" : input.sourceItemKey().trim();
        if (key.length() > 100) throw badRequest("sourceItemKey excede 100 caracteres");
        List<Assignment> existing = em.createQuery(
                "select a from Assignment a where a.tenantId=:t and a.communicationId=:c and a.sourceItemKey=:k",
                Assignment.class).setParameter("t", tenantId)
                .setParameter("c", communication.id).setParameter("k", key)
                .setMaxResults(1).getResultList();
        if (!existing.isEmpty()) return existing.getFirst();

        Assignment a = new Assignment();
        a.id = UUID.randomUUID();
        a.tenantId = tenantId;
        a.communicationId = communication.id;
        a.studentContextId = context == null ? null : context.id;
        a.studentId = context != null && "CONFIRMED".equals(context.mappingStatus) ? context.studentId : null;
        a.sourceItemKey = key;
        a.title = input.title().trim();
        a.description = input.description();
        a.classification = type;
        a.dueAt = input.dueAt();
        a.dueDateSource = trimToNull(input.dueDateSource());
        a.requiresReview = true;
        a.status = "PENDING";
        a.createdAt = Instant.now();
        a.updatedAt = a.createdAt;
        em.persist(a);
        return a;
    }

    @Transactional(readOnly = true)
    public List<Assignment> assignments(UUID tenantId) {
        return em.createQuery("select a from Assignment a where a.tenantId=:t order by a.createdAt desc",
                Assignment.class).setParameter("t", tenantId).getResultList();
    }

    @Transactional(readOnly = true)
    public Assignment assignment(UUID tenantId, UUID assignmentId) {
        Assignment a = em.find(Assignment.class, assignmentId);
        if (a == null || !tenantId.equals(a.tenantId)) throw notFound("Atividade");
        return a;
    }

    @Transactional
    public Assignment confirmAssignment(UUID tenantId, UUID assignmentId) {
        Assignment a = assignment(tenantId, assignmentId);
        if (a.studentId == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aluna ainda não identificada");
        if ("COMPLETED".equals(a.status) || "CANCELLED".equals(a.status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Atividade já encerrada");
        a.requiresReview = false;
        a.updatedAt = Instant.now();
        if (a.dueAt != null) scheduleReminders(tenantId, a);
        return a;
    }

    private void scheduleReminders(UUID tenantId, Assignment a) {
        List<ReminderRule> rules = em.createQuery(
                "select r from ReminderRule r where r.tenantId=:t and r.enabled=true",
                ReminderRule.class).setParameter("t", tenantId).getResultList();
        for (ReminderRule rule : rules) {
            Instant scheduleAt = a.dueAt.minus(Duration.ofMinutes(rule.minutesBefore));
            if (!scheduleAt.isAfter(Instant.now())) continue;
            List<ReminderJob> existing = em.createQuery(
                    "select j from ReminderJob j where j.tenantId=:t and j.assignmentId=:a and j.ruleId=:r",
                    ReminderJob.class).setParameter("t", tenantId)
                    .setParameter("a", a.id).setParameter("r", rule.id)
                    .setMaxResults(1).getResultList();
            if (!existing.isEmpty()) continue;
            ReminderJob j = new ReminderJob();
            j.id = UUID.randomUUID();
            j.tenantId = tenantId;
            j.assignmentId = a.id;
            j.ruleId = rule.id;
            j.scheduledAt = scheduleAt;
            j.channel = rule.channel;
            j.status = "PENDING";
            j.attempts = 0;
            j.createdAt = Instant.now();
            j.updatedAt = j.createdAt;
            em.persist(j);
        }
    }

    @Transactional
    public Assignment completeAssignment(UUID tenantId, UUID assignmentId) {
        Assignment a = assignment(tenantId, assignmentId);
        a.status = "COMPLETED";
        a.completedAt = Instant.now();
        a.updatedAt = a.completedAt;
        List<ReminderJob> jobs = em.createQuery(
                "select j from ReminderJob j where j.tenantId=:t and j.assignmentId=:a and j.status='PENDING'",
                ReminderJob.class).setParameter("t", tenantId).setParameter("a", a.id).getResultList();
        for (ReminderJob j : jobs) { j.status = "CANCELLED"; j.updatedAt = Instant.now(); }
        return a;
    }

    @Transactional
    public ReminderRule createRule(UUID tenantId, ApiModels.ReminderRuleCreate input) {
        requireTenant(tenantId);
        String channel = input.channel().trim().toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(channel)) throw badRequest("Canal inválido");
        if (input.minutesBefore() == null || input.minutesBefore() < 0)
            throw badRequest("minutesBefore precisa ser não negativo");
        List<ReminderRule> existing = em.createQuery(
                "select r from ReminderRule r where r.tenantId=:t and r.channel=:c and r.minutesBefore=:m",
                ReminderRule.class).setParameter("t", tenantId)
                .setParameter("c", channel).setParameter("m", input.minutesBefore())
                .setMaxResults(1).getResultList();
        if (!existing.isEmpty()) return existing.getFirst();
        ReminderRule r = new ReminderRule();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId;
        r.channel = channel;
        r.minutesBefore = input.minutesBefore();
        r.enabled = true;
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        em.persist(r);
        return r;
    }

    @Transactional(readOnly = true)
    public List<ReminderRule> rules(UUID tenantId) {
        return em.createQuery("select r from ReminderRule r where r.tenantId=:t order by r.minutesBefore desc",
                ReminderRule.class).setParameter("t", tenantId).getResultList();
    }

    @Transactional(readOnly = true)
    public List<ReminderJob> reminders(UUID tenantId) {
        return em.createQuery("select j from ReminderJob j where j.tenantId=:t order by j.scheduledAt",
                ReminderJob.class).setParameter("t", tenantId).getResultList();
    }

    @Transactional(readOnly = true)
    public ApiModels.Summary summary(UUID tenantId) {
        long students = count("Student", tenantId, null);
        long communications = count("Communication", tenantId, null);
        long pending = count("Assignment", tenantId, "PENDING");
        long completed = count("Assignment", tenantId, "COMPLETED");
        long reminders = count("ReminderJob", tenantId, "PENDING");
        return new ApiModels.Summary(students, communications, pending, completed, reminders);
    }

    private long count(String entity, UUID tenantId, String status) {
        String jpql = "select count(e) from " + entity + " e where e.tenantId=:t"
                + (status == null ? "" : " and e.status=:status");
        var query = em.createQuery(jpql, Long.class).setParameter("t", tenantId);
        if (status != null) query.setParameter("status", status);
        return query.getSingleResult();
    }

    private void requireTenant(UUID id) {
        if (em.find(Tenant.class, id) == null) throw notFound("Família");
    }
    private static Instant orNow(Instant at) { return at == null ? Instant.now() : at; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trimToNull(String value) { return blank(value) ? null : value.trim(); }
    /** Data sem horário confirmado permanece nula; não inventar meia-noite ou 08:00. */
    private static Instant parseTimestamp(String value) {
        if (blank(value)) return null;
        try { return OffsetDateTime.parse(value.trim()).toInstant(); }
        catch (DateTimeParseException ignored) {
            try { return Instant.parse(value.trim()); }
            catch (DateTimeParseException ignoredAgain) { return null; }
        }
    }
    private static ResponseStatusException notFound(String name) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, name + " não encontrado(a)");
    }
    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
