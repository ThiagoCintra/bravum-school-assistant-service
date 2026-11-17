package br.com.bravum.schoolassistant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Modelo persistido no schema existente; este serviço nunca cria tabelas. */
@Entity
@Table(schema = "school_assistant", name = "provider_connections")
public class ProviderConnection {
    @Id
    @Column(name = "id")
    public UUID id;
    @Column(name = "tenant_id")
    public UUID tenantId;
    @Column(name = "provider_code")
    public String providerCode;
    @Column(name = "connection_name")
    public String connectionName;
    @Column(name = "status")
    public String status;
    @Column(name = "credential_ref")
    public String credentialRef;
    @Column(name = "created_at")
    public Instant createdAt;
    @Column(name = "updated_at")
    public Instant updatedAt;
}
