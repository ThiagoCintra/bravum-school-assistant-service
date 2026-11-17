# Criacao controlada da skill Alexa-hosted — Bravum School Assistant

## Objetivo e escopo

Criar somente **uma** skill **Alexa-hosted, custom, pt-BR**, nome de cadastro
`Bravum School Assistant`, hospedagem gerenciada pela Amazon na regiao
`US_EAST_1`, estagio **development**.

A skill e apenas criada na conta Amazon Developer. A Amazon provisiona a
infraestrutura da modalidade Alexa-hosted, dentro das cotas/limites do servico.
**Nao e uma Lambda na conta AWS conectada do titular**, nem e deploy do Java no Lenovo.

O fluxo nao carrega o modelo de interacao, `alexa-skill/lambda/`, senhas da
API, dados das alunas, nem aciona notificacoes. Nao publica na Alexa Skills Store,
nao promove para live e nao sincroniza o repositorio remoto da Amazon.

O nome de invocacao `assistente escolar`, intents de recados e tarefas e
modelo `pt-BR` continuam apenas na branch do PR #1; importacao futura em PR proprio.

## Regra de execucao e seguranca

Workflow: `.github/workflows/alexa-create-hosted-manual.yml`

- Apenas `workflow_dispatch`, sem `push`, `pull_request` ou `schedule`.
- Exige `main` e a confirmacao literal `CRIAR_BRAVUM_SCHOOL_ASSISTANT`.
- Usa os **mesmos** Secrets `ASK_REFRESH_TOKEN` e `ASK_VENDOR_ID`; nenhum novo token.
- ASK CLI `2.30.7` fixa, com perfil `~/.ask/cli_config` temporario `0600`,
  como no PR #7; o diretório e eliminado no fim.
- A Action consulta skills existentes de todas as paginas e **nao cria**
  segunda skill com o mesmo nome em `pt-BR`; se nao conseguir listar tudo,
  falha antes de criar (fail closed).
- Confere permissao `newSkill` de Alexa-hosted. Caso a conta exija CAPTCHA,
  para sem tentar contornar — o titular deve concluir esse requisito na Amazon.
- O fluxo executa exatamente uma criacao, sem repetir automaticamente o POST
  em caso de erro. Se falhar depois de aceitar a criacao, **confira a Developer
  Console antes de tentar novamente**: pode ter sido criada parcialmente.
- Imprime apenas resultado e Skill ID (identificador de desenvolvimento,
  **nao** token). Nunca imprime OAuth, Vendor ID, resposta completa, headers ou debug.
- Nenhum AWS IAM, Supabase, Cloudflare, Lenovo, Docker ou deploy.

## Passo do titular após revisar e aprovar o PR

1. Revisar e fazer merge **somente** deste PR na `main`.
2. Em **Actions > Alexa | criar skill Bravum (manual, development) > Run workflow**,
   selecionar `main` e digitar `CRIAR_BRAVUM_SCHOOL_ASSISTANT`.
3. Executar **uma unica vez** e compartilhar o link do job. O job registra
   `RESULTADO=CRIADA_EM_DEVELOPMENT` e `SKILL_ID`, ou
   `RESULTADO=JA_EXISTE_NENHUMA_SKILL_CRIADA`.

Não executar nem juntar com o PR #1; a criação da skill não depende do backend
estabelecido no Lenovo e nao o disponibiliza automaticamente.

## Limitacoes importantes

- A criacao apenas reserva e configura a skill basica Alexa-hosted. Ainda nao
  e possivel conversar com o backend Bravum ou disparar notificacoes.
- Runtime e regiao sao parametros da plataforma Alexa-hosted, sujeitos a
  mudancas do suporte Amazon. O runtime `NodeJS` e resolvido internamente
  pela versao fixada da ASK CLI.
- A Amazon pode impor limite de recursos hospedados, CAPTCHA ou permissoes.
  A criacao na modalidade Alexa-hosted **nao elimina** limites de uso da Amazon.
- O projeto usa, em `alexa-skill/lambda/`, variaveis de ambiente para o
  endpoint Java e a credencial da skill. A forma segura de armazenar essas
  configuracoes na Alexa-hosted ainda precisa ser projetada; **nao colocar
  SCHOOL_ALEXA_SKILL_KEY no codigo nem no GitHub**.
- A documentacao do runtime do provedor pode estar desatualizada e as quotas
  mudam; nao presumir gratuidade irrestrita.

## Referencias

- https://developer.amazon.com/en-US/docs/alexa/hosted-skills/alexa-hosted-skills-ask-cli.html
- https://developer.amazon.com/en-US/docs/alexa/smapi/skill-operations.html
- https://github.com/alexa/ask-cli/blob/develop/lib/clients/smapi-client/resources/alexa-hosted.js
- https://github.com/alexa/ask-cli/blob/develop/lib/utils/profile-helper.js
