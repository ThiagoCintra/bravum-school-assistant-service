# Bravum School Assistant — Alexa (pt-BR)

## Estado da entrega
- API Java: endpoints administrativos e de skill separados por chaves distintas; vínculo de conta Amazon por código de uso único; consultas de comunicados, resumo e atividades.
- Notificações: fila durável em PostgreSQL, idempotente por vínculo e comunicado; envio Proactive Events para contas que habilitaram notificações; chave de ativação global OFF por padrão.
- Skill Node.js: intents pt-BR, vínculo, consulta, evento de alteração de consentimento. Código-fonte em alexa-skill/ no mesmo repositório.
- DDL aplicado manualmente no projeto Supabase exclusivo: quatro tabelas alexa_* com RLS ativo. Não existem contas, alunos nem comunicados reais cadastrados.
- Não há skill criada na conta Amazon nem backend publicado nesta entrega. Ambos dependem de credenciais e acesso do titular.

## Como funciona o acesso
1. O operador cria tenant com POST /api/v1/setup/tenant e um responsável por POST /api/v1/alexa/admin/guardians, autorizado somente pelo token administrativo.
2. Gera código com POST /api/v1/alexa/admin/link-codes/{guardianId}; código de 8 dígitos, uso único, expira em 10 minutos. O código não vai para GitHub, logs nem mensagens públicas.
3. O usuário habilita a skill Amazon, diz 'vincular' e o código. A skill confirma o applicationId da Amazon, transmite o userId recebido da Alexa com a credencial exclusiva da skill.
4. A API associa o ID Amazon à família após validar o código. Guarda HMAC-SHA256 para busca e cópia criptografada AES-256-GCM do ID para notificações unicast. Não aceita tenant ID na chamada da skill.
5. Todas as consultas da skill resolvem o tenant a partir do vínculo cadastrado. Revogação pelo DELETE /api/v1/alexa/admin/links/{linkId} cancela os avisos pendentes.
6. O userId de uma Alexa identifica a conta Amazon, NÃO identifica separadamente a voz do pai ou da mãe. Qualquer pessoa com acesso ao Echo da conta vinculada pode ouvir os títulos retornados. Use uma conta/dispositivo confiável.

## Contrato REST (caminhos após /api/v1)
Admin: Authorization Bearer SCHOOL_ASSISTANT_API_KEY e X-Tenant-Id SCHOOL_ASSISTANT_TENANT_ID.
- POST /alexa/admin/guardians com JSON fullName => guardianId
- POST /alexa/admin/link-codes/{guardianId} => code, expiresInSeconds=600
- GET /alexa/admin/links => vínculos sem segredos
- DELETE /alexa/admin/links/{linkId} => revogação
- GET /alexa/admin/notifications => contagens por status

Skill: Authorization Bearer SCHOOL_ALEXA_SKILL_KEY e X-Alexa-User-Id do envelope Amazon validado. Nunca recebe a chave administrativa.
- POST /alexa/skill/link com code => LINKED ou INVALID_CODE
- GET /alexa/skill/summary => comunicados últimos 14 dias e atividades pendentes revisadas
- GET /alexa/skill/messages => até três títulos recentes e total
- GET /alexa/skill/agenda?day=hoje ou day=amanhã => até três tarefas com prazo confirmado
- POST /alexa/skill/subscription com enabled e eventTime => opt-in, exige vínculo prévio

## Implantação manual no Lenovo
- Backend: Java 21 / Spring Boot; banco PostgreSQL do Supabase privado; HTTPS válido no domínio publicado pelo usuário.
- O DDL está em database/ddl/alexa_addendum.sql, JÁ APLICADO ao Supabase exclusivo. Não reaplicar nem executar automaticamente por Docker ou CI.
- Variáveis obrigatórias: DB_URL, DB_USERNAME, DB_PASSWORD, SCHOOL_ASSISTANT_TENANT_ID (UUID), SCHOOL_ASSISTANT_API_KEY (pelo menos 32 caracteres).
- Para Alexa: SCHOOL_ALEXA_SKILL_KEY (outro segredo >=32 caracteres), ALEXA_LINK_PEPPER (32+ caracteres aleatórios), ALEXA_ENCRYPTION_KEY_BASE64 (32 bytes aleatórios em base64). Persistir esses segredos fora do repositório para não invalidar os vínculos após reinícios.
- ALEXA_NOTIFICATIONS_ENABLED deve continuar false até consentimento real e credenciais Amazon confirmados. ALEXA_PUBLISH_INTERVAL_MS define periodicidade (padrão 60000).
- Quando preparado: ALEXA_LWA_CLIENT_ID, ALEXA_LWA_CLIENT_SECRET, ALEXA_EVENT_REGION=NA|EU|FE e ALEXA_EVENT_STAGE=development|production. Produção somente com skill certificada.
- Banco: acesso JDBC por role de menor privilégio ao schema school_assistant. NUNCA usar service_role Supabase em cliente Alexa. Não expor Data API pública do schema.

## Instalação na Alexa
- Criar skill custom em pt-BR e modalidade Alexa-hosted Node.js no Amazon Developer Console, com conta Amazon do titular.
- Importar arquivos de alexa-skill/lambda, dependências Node, o modelo de voz alexa-skill/skill-package/interactionModels/custom/pt-BR.json e configurar ALEXA_SKILL_ID, SCHOOL_API_BASE_URL com https://SEU_DOMINIO/api/v1, SCHOOL_ALEXA_SKILL_KEY na Lambda.
- Restringir trigger da Lambda ao Skill ID. Usar skill-manifest-template.json como ponto de partida e substituir o ARN fictício pelo ARN real.
- Publicar AMAZON.MessageAlert.Activated e receber SKILL_PROACTIVE_SUBSCRIPTION_CHANGED conforme requisitos da Amazon. Skill events podem exigir gerenciamento via ASK CLI, e habilitação da notificação no app Alexa.
- Primeiro vincular via código, depois habilitar as notificações no app Alexa para disparar o evento de assinatura. A API ignora eventos antigos e trata ausência de vínculo como não autorizado.
- Proactive Events não equivale a lembretes de horário marcado. A API de Reminders exige outro fluxo e consentimento por lembrete; os jobs antigos não são enviados como Reminders.

## Testes a passar antes de usar dados reais
1. node --test em alexa-skill/lambda; mvn test na raiz (sem executar GitHub Actions ou builds no Lenovo automaticamente).
2. Recusar tokens ausentes e cruzados (401), conta sem vínculo (403), tenant inválido (403), códigos expirados e segunda utilização.
3. Vincular conta de teste, listar resumo vazio; importar comunicado sintético duas vezes e conferir deduplicação.
4. Habilitar notificações na Alexa, receber subscription event; conferir UMA linha em alexa_notice_outbox por comunicado e vínculo.
5. Manter envio desativado até estar pronto; testar Proactive Events somente no estágio development, sem conteúdo pessoal.
6. Revogar vínculo e verificar cancelamento dos eventos ainda pendentes.

## Documentação Amazon
https://developer.amazon.com/en-US/docs/alexa/smapi/proactive-events-api.html
https://developer.amazon.com/en-US/docs/alexa/smapi/proactive-events-api-reference.html
https://developer.amazon.com/en-US/docs/alexa/smapi/skill-events-in-alexa-skills.html
https://developer.amazon.com/en-US/docs/alexa/hosted-skills/alexa-hosted-skills-create.html
https://developer.amazon.com/en-US/docs/alexa/smapi/alexa-reminders-guidelines-for-usage.html

## Segurança do banco
- As 4 tabelas alexa_* têm RLS habilitado e papéis públicos sem privileges.
- 18 tabelas legadas do schema school_assistant permanecem sem RLS: a configuração atual nega USAGE/SELECT para anon e authenticated, mas manter o schema fora da API pública e avaliar ativação de RLS em revisão futura.
- Sem dados reais, sem novos recursos com cobrança, sem deploy ou pipelines de CI nesta PR.
