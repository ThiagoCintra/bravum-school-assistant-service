# Bravum School Assistant Service

API de agenda familiar e comunicados escolares da Bravum. Implementação inicial com **Java 21, Spring Boot 3.5.13, Spring Security, JPA, PostgreSQL 17 e OpenAPI**, no padrão dos serviços atuais.

## Status

MVP em desenvolvimento, **sem deploy**. Banco separado no Supabase: projeto `bravum-school-assistant`, schema `school_assistant` (22 tabelas: 18 originais + 4 auxiliares da Alexa). Os dados desse banco **não foram copiados de outro banco**.

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

Isso **não é** a autenticação familiar definitiva. Para a Alexa, a autenticação usa chave exclusiva da skill e associação persistente da conta Amazon ao responsável e à família; não confia em X-Tenant-Id da skill. Para outros canais, projetar autenticação própria.

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

## Alexa (MVP, sem publicação)

A skill Node.js em `alexa-skill/` oferece consultas por voz e captura de consentimento para avisos de novos comunicados. O backend tem endpoints segregados, pareamento com código temporário, associação família/conta Amazon, e outbox durável para `AMAZON.MessageAlert.Activated`. Os envios ficam **desativados** por padrão até o usuário autorizar no aplicativo Alexa e configurar a Amazon.

**DDL Alexa**: `database/ddl/alexa_addendum.sql` foi aplicado manualmente apenas no Supabase deste projeto, adicionando 4 tabelas privadas e vazias com RLS ativo. As 18 antigas continuam com RLS desativado, mas permissões públicas revogadas. Não publicar o schema.

**Documentação de instalação, segurança e testes**: `docs/ALEXA_INTEGRATION.md`.

PR de desenvolvimento sem testes Maven/Node completos ou deploy; `main` continua intacta.
