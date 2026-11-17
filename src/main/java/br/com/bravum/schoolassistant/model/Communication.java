package br.com.bravum.schoolassistant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Modelo persistido no schema existente; este serviço nunca cria tabelas. */
@Entity
@Table(schema = "school_assistant", name = "communications")
public class Communication {
    @Id
    @Column(name = "id")
    public UUID id;
    @Column(name = "tenant_id")
    public UUID tenantId;
    @Column(name = "connection_id")
    public UUID connectionId;
    @Column(name = "source_fingerprint")
    public String sourceFingerprint;
    @Column(name = "external_communication_id")
    public String externalCommunicationId;
    @Column(name = "title")
    public String title;
    @Column(name = "body")
    public String body;
    @Column(name = "category")
    public String category;
    @Column(name = "published_at")
    public Instant publishedAt;
    @Column(name = "event_at")
    public Instant eventAt;
    @Column(name = "captured_at")
    public Instant capturedAt;
    @Column(name = "source_type")
    public String sourceType;
    @Column(name = "content_complete")
    public Boolean contentComplete;
    @Column(name = "review_status")
    public String reviewStatus;
    @Column(name = "created_at")
    public Instant createdAt;
    @Column(name = "updated_at")
    public Instant updatedAt;
}
