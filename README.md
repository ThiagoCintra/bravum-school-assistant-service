# Bravum School Assistant Service

API de agenda familiar e comunicados escolares da Bravum. Implementação inicial com **Java 21, Spring Boot 3.5.13, Spring Security, JPA, PostgreSQL 17 e OpenAPI**, no padrão dos serviços atuais.

## Status

MVP em desenvolvimento, **sem deploy**. Banco separado no Supabase: projeto `bravum-school-assistant`, schema `school_assistant` (18 tabelas já criadas). Os dados desse banco **não foram copiados de outro banco**.

- Comunicação importada apenas por JSON estruturado produzido pelo extrator autorizado no Mac.
- Identificação do aluno exige vínculo explícito; índices JSF **não são IDs estáveis**.
- Data de entrega não é inferida de data de evento/publicação.
- Lembretes são **agendamentos internos**: não existe envio real para Alexa, ChatGPT ou celular nesta versão.
- Nenhuma integração contínua com endpoints privados ou WebSocket do ClipEscola foi implementada.

## Padrão de governança Bravum

- Branch de implementação e PR para `main`; sem alteração de infra, deploy ou workflows.
- `spring.jpa.hibernate.ddl-auto=validate`: **não executar migrations/DDL automaticamente**.
- DDL de referência em `database/ddl/school_assistant.sql`. O schema já foi criado manualmente no Supabase.
- Credenciais exclusivamente por variáveis de ambiente, nunca em código ou no repositório.
- Serviço fechado por padrão: nenhuma operação de dados é pública.
- `/api/v1/actuator/health` liberado para monitoramento.

## Segurança no MVP

Esta primeira versão usa autenticação **machine-to-machine**, com `Authorization: Bearer` configurado por `SCHOOL_ASSISTANT_API_KEY` e `X-Tenant-Id` exatamente igual a `SCHOOL_ASSISTANT_TENANT_ID`. Sem ambas as variáveis corretas, acesso negado.

Isso **não é** a autenticação familiar definitiva. Antes de expor APIs ao frontend, Alexa ou ChatGPT, integrar o login/contexto ativo da Bravum, autorização por família e, se aplicável, RLS no Supabase.

O schema tem os privilégios dos papéis `anon` e `authenticated` revogados; RLS ainda não foi habilitado. Não expor o schema diretamente via cliente Supabase.

## Configuração (somente após autorização para execução)

Veja `.env.example`. Ajustar URL JDBC, credenciais e um UUID de tenant para o serviço:

```sh
mvn test
mvn spring-boot:run
```

Os comandos acima são ilustrativos; **não foram executados no Lenovo, nem em GitHub Actions**.

O fluxo de bootstrap é propositalmente explícito:
1. `POST /api/v1/setup/tenant` com `{"name":"Família de teste"}`;
2. `POST /api/v1/students` para cadastrar cada filha;
3. `POST /api/v1/integrations` com `{"providerCode":"CLIPESCOLA"}`;
4. `POST /api/v1/integrations/{connectionId}/communications/import` com JSON extraído;
5. Revisar contexto e, apenas com ID estável, confirmar vínculo;
6. Criar e confirmar atividades; o agendamento de lembretes ocorre se houver destinatária e prazo real.

**Não foram cadastrados alunos nem inseridos comunicados no Supabase.**

Mais detalhes em `docs/API.md`, `docs/CLIPESCOLA_CONTRACT.md` e `docs/BRAVUM_STANDARDS.md`.
