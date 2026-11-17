# Convenções adotadas da Bravum

Referências: `agenda-service` e `organization-service`.

- Java 21 / Spring Boot 3.5.13 / Maven.
- `br.com.bravum` como groupId; aplicação em `br.com.bravum.schoolassistant`.
- API versionada por `server.servlet.context-path=/api/v1`.
- Porta dedicada 8088 (sujeita a confirmação de disponibilidade no Lenovo).
- PostgreSQL e JPA, `open-in-view=false`, `ddl-auto=validate`.
- DDL explícito mantido em `database/ddl/`; sem DDL executado em build/startup.
- Container Docker multistage; sem GitHub Actions e sem deploy.
- `/actuator/health`, Swagger e documentação.
- Autenticação obrigatória em qualquer operação sobre dados.

**Adaptação provisória explicitamente diferente:** enquanto não houver contexto de família no login Bravum, o MVP usa credencial de serviço por tenant fixo em configuração. Não expor dados familiares em produção até integrar autorização robusta e políticas adequadas.
