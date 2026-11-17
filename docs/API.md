# Contrato da API — MVP

Base local: `http://localhost:8088/api/v1` (apenas quando implantado). JWT do login Bravum será integrado posteriormente; por ora, credencial de serviço + tenant fixo no ambiente.

| Método | Caminho | Descrição |
|---|---|---|
| POST | /setup/tenant | Criar explicitamente o tenant configurado |
| GET / POST | /students | Consultar / cadastrar alunas |
| GET / POST | /integrations | Consultar / cadastrar uma conexão |
| POST | /integrations/{id}/communications/import | Importar comunicado idempotente |
| GET | /communications | Listar comunicados do tenant |
| GET | /student-contexts | Contextos observados |
| POST | /student-contexts/{id}/confirm | Confirmar mapeamento com ID externo estável |
| GET / POST | /assignments | Consultar / registrar atividades |
| GET | /assignments/{id} | Consultar atividade |
| POST | /assignments/{id}/confirm | Concluir revisão e agendar lembretes |
| POST | /assignments/{id}/complete | Concluir atividade e cancelar avisos pendentes |
| GET | /agenda | Agenda de atividades |
| GET / POST | /reminder-rules | Regras de antecedência por canal |
| GET | /reminders | Jobs de lembrete ainda sem entrega real |
| GET | /assistants/summary | Contagens e resumo |
| GET | /actuator/health | Saúde da aplicação |

Todos os caminhos são relativos a `/api/v1`. Requisições de dados exigem `Authorization: Bearer <SCHOOL_ASSISTANT_API_KEY>` e `X-Tenant-Id: <SCHOOL_ASSISTANT_TENANT_ID>`.

O bootstrap não é público e não cria automaticamente tenants. O usuário deve configurar o UUID do tenant no servidor.

Exemplo de importação:

```json
{
  "source": "CLIPESCOLA",
  "externalCommunicationId": null,
  "studentContext": {
    "stableStudentId": null,
    "contextKey": "contexto-1",
    "mappingStatus": "UNCONFIRMED"
  },
  "communication": {
    "title": "Comunicado de exemplo",
    "body": "Atividade ilustrativa, sem aluna identificada.",
    "category": "COMUNICADO",
    "publishedAt": null,
    "eventDate": null,
    "dueAt": null,
    "attachments": []
  },
  "extraction": {
    "capturedAt": "2026-11-17T13:00:00-03:00",
    "sourceType": "VISIBLE_DOM",
    "contentComplete": false,
    "requiresReview": true
  }
}
```

Resposta: `{"communicationId":"uuid","created":true,"contextId":"uuid","mappingStatus":"UNCONFIRMED"}`.

Atividades são criadas separadamente pelo endpoint `POST /assignments`; o importador **não inventa atividades nem prazos**. `dueAt` somente aceita uma data/hora exata confirmada, acompanhada de `dueDateSource`. No MVP, comunicação pode ser compartilhada entre vários contextos e deduplicada pelo conteúdo ou ID externo.

Swagger/OpenAPI dinâmico, quando a aplicação estiver rodando, em `/api/v1/swagger-ui/index.html` e `/api/v1/v3/api-docs`, também protegidos pela autenticação interna.
