# ASK CLI no Mac — autorização Amazon Developer (sem deploy)
**Atualização:** 2026-11-17 | **Projeto:** Bravum School Assistant | **Status:** procedimento preparado, **não executado no Mac**.

## Objetivo
Autorizar a ASK CLI oficial a consultar/gerenciar skills na conta Amazon Developer do titular, sem entregar tokens ao ChatGPT, sem depender de ações AWS e sem publicar código. O GitHub contém o backend e os arquivos da skill em `alexa-skill/`; não é uma conexão direta do ChatGPT com Amazon Developer.

**Pré-requisitos:** registro Amazon Developer concluído e Node.js/npm instalados no Mac. A conta AWS existente não substitui o consentimento Amazon Developer. O fluxo de Alexa-hosted **não exige associar conta AWS** à CLI.

## Execução local — um passo por vez
- Verificação: `node -v` e `npm -v`. Se algum faltar, instalar Node LTS antes de continuar.
- Método recomendado oficialmente: `npm install -g ask-cli`.
- Autorizar no navegador: `ask configure --profile bravum-school-assistant`.
- Quando houver tela OAuth, confirmar login Amazon Developer no navegador do **próprio Mac** e autorizar a ASK CLI. Se pedir AWS para Alexa-hosted, selecionar não associar/pular, se disponível.
- Verificação de acesso **somente leitura**: `ask smapi list-skills-for-vendor --profile bravum-school-assistant`. Uma lista vazia também é resultado válido (conta sem skills).
- Alternativa consolidada, após revisão: executar `scripts/alexa-auth-mac.sh` localmente. O script verifica Mac/Node, instala ASK CLI se necessário, autentica interativamente e lista skills. **Não executá-lo no Lenovo, em CI ou em runner.**

## Segurança
1. Os tokens locais ficam gerenciados pela CLI (normalmente em `~/.ask/`), **nunca devem ser versionados, mostrados em logs ou enviados ao ChatGPT**. Não enviar senha, código OAuth, refresh token, `cli_config` nem print com tokens.
2. Não rodar `ask new`, `ask deploy`, `ask smapi create-skill` ou `git push` para repositórios Alexa-hosted nesta etapa. Esses comandos alteram recursos remotos.
3. **Alexa-hosted skills v2:** após criação autorizada, o projeto tem seu próprio Git remoto gerenciado pela Amazon. O deploy de desenvolvimento é por push no branch remoto `master`; `ask deploy` não serve para esse tipo. Nunca presumir que um push no GitHub do Bravum publicará a skill.
4. Se a autenticação apresentar erro, registrar **apenas mensagem genérica e versão da CLI**, ocultando códigos, URLs e credenciais.
5. Não criar novos recursos pagáveis, não executar deploy no Lenovo, não ativar Proactive Events; `ALEXA_NOTIFICATIONS_ENABLED` permanece `false`.

## Após autorização confirmada
- Usar ID da skill e descrição dos recursos **sem segredos** para adaptar o projeto Alexa existente.
- Primeiro revisar e testar o backend/skill, depois **solicitar autorização separada para criar** a Alexa-hosted skill em modo desenvolvimento.
- Configurar endpoint HTTPS do Lenovo apenas quando estiver validado; usar somente fixtures sintéticas e consentimento explícito.
- Permanecem pendentes a publicação real da API, testes Maven e Node, Skill ID, Amazon Lambda ARN e aprovação do PR.

## Fontes oficiais
- https://developer.amazon.com/en-US/docs/alexa/smapi/quick-start-alexa-skills-kit-command-line-interface.html
- https://developer.amazon.com/en-US/docs/alexa/smapi/ask-cli-command-reference.html#configure-command
- https://developer.amazon.com/en-US/docs/alexa/hosted-skills/alexa-hosted-skills-ask-cli.html
