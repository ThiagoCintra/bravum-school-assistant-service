# Alexa via GitHub Actions — preparacao sem instalar no Mac

**Data:** 2026-11-17  
**Branch:** `feat/school-assistant-mvp` / PR #1 (draft)  
**Status:** workflow criado, **nao executado**; nenhuma skill criada, modificada ou publicada.

## Decisao operacional

- O GitHub e a fonte de versao do codigo Bravum. Para a Alexa-hosted Skill, a Amazon opera **outro repositorio Git**, com estagios `dev`, `master` (deploy de desenvolvimento) e `prod` (live). Um push no GitHub Bravum **nao atualiza** por si so a Alexa.
- A ASK CLI roda apenas no **runner efemero** do GitHub Actions quando a verificacao Amazon for selecionada. Nao exige npm, ASK CLI ou instalacao no Mac/Lenovo.
- **Este workflow nao publica nada.** Nenhum `ask deploy`, `ask new`, `ask smapi create-skill`, `git push` remoto Amazon, AWS, Supabase ou Lenovo.
- A API Java no Lenovo e o runtime da skill sao etapas futuras e independentes da verificacao de acesso ao Developer Console.
- Os dados das alunas e o material ClipEscola **nao** fazem parte da pipeline. Nao inserir fixtures reais nem tokens no Git.

## Workflow pronto

Arquivo: [`.github/workflows/alexa-validate-manual.yml`](../.github/workflows/alexa-validate-manual.yml)

Disparo **somente manual** por `workflow_dispatch`, sem gatilho por push, pull request ou cron. Tem estes trabalhos:

1. `offline-validation`: valida sintaxe dos JavaScript, formato JSON do modelo `pt-BR` e manifesto-template, instala dependencias no runner (sem scripts de instalacao) e tenta executar os testes Node.
2. `amazon-readonly`: **opcional**, somente quando selecionado **na branch `main`**, depois de revisao/merge e de existirem os secrets. Consulta apenas a lista de skills da conta Amazon por SMAPI. Descarta stdout/stderr para nao exibir IDs/nomes/codigos.
3. Se a opcao Amazon for selecionada em outra branch, mostra mensagem informativa, **sem usar secrets**.

O campo `check_amazon_readonly` vem **desligado** por padrao. O runner exige apenas `contents: read`. Cada execucao consome minutos do GitHub Actions; nao disparar automaticamente.

### Dependencia de GitHub

O workflow esta **apenas na branch do PR draft**. O GitHub em geral exige que o arquivo de `workflow_dispatch` exista na branch padrao para poder inicia-lo pelo painel Actions. **Nao promover este workflow a main sem revisar o PR e autorizar o merge.** Nenhum workflow foi iniciado neste preparo.

## Autorizacao Amazon — etapa obrigatoria e separada

Ter a AWS conectada **nao fornece** a autorizacao Amazon Developer/SMAPI. O cadastro na Developer Console e necessario, mas nao basta para autenticar um runner. O workflow usa variaveis ambientais oficialmente suportadas pela ASK CLI, configuradas **somente como GitHub Actions Secrets**:

- `ASK_REFRESH_TOKEN`: token Amazon/LWA do titular com escopos apropriados para a API da Alexa.
- `ASK_VENDOR_ID`: identificador do fornecedor na conta Amazon Developer (a documentacao oficial indica `https://developer.amazon.com/mycid.html`).

**Nao gerar, colar nem enviar tokens, cookies, senha, codigos de autorizacao ou `~/.ask/cli_config` ao ChatGPT, em issues, commits, chat ou logs.** O token precisa ser obtido por fluxo oficial de consentimento LWA/ASK CLI ou integracao autorizada. **Nao ha atualmente conector direto Amazon Developer neste chat** para completar essa parte por conta propria; a autorizacao inicial exige acao do titular. Nao iniciar um login interativo em runner publico nem imprimir URLs/codigos OAuth nos logs.

Quando o titular autorizar, inserir os secrets diretamente em **Settings > Secrets and variables > Actions** (apenas na interface GitHub). Verificar o escopo antes de conceder direitos de escrita: o *comando* deste workflow e somente leitura, mas credenciais SMAPI podem ter permissoes amplas.

### Sequencia para uso futuro

1. Revisar e aprovar o PR #1, quando testes/requisitos estiverem prontos; sem merge automatico.
2. Com o workflow disponivel na `main`, abrir **Actions > Alexa | validacao manual (sem deploy) > Run workflow**.
3. Primeira rodada: deixar `check_amazon_readonly=false`; executar somente teste da skill.
4. Apos autorização Amazon e insercao segura dos dois secrets: selecionar `check_amazon_readonly=true` e confirmar a consulta somente leitura.
5. So apos passar nos testes, **desenhar em PR separado** o provisionamento/deploy da Alexa-hosted skill no repositório Git gerenciado pela Amazon. Nenhum passo de publicacao/live faz parte desta etapa.

Se a conta Amazon ainda nao tiver skills, a consulta pode retornar uma lista vazia com sucesso; isso nao significa skill criada. A verificacao de acesso nao valida Lambda, pareamento familiar, HTTPS, respostas por voz ou notificacoes.

## Estado honesto (na data acima)

- [x] Codigo da API/skill existente no PR #1, sem publicacao.
- [x] Workflow manual e modo de consulta Amazon **codificados**, sem executa-los.
- [ ] Testes do workflow executados no GitHub.
- [ ] Credenciais Amazon Developer autorizadas e configuradas como secrets.
- [ ] Consulta SMAPI confirmada.
- [ ] Skill Alexa-hosted criada na conta do titular e repositório remoto Amazon identificado.
- [ ] API Lenovo, HTTPS e testes ponta a ponta validados.

## Referencias oficiais

- https://developer.amazon.com/en-US/docs/alexa/smapi/manage-credentials-with-ask-cli.html
- https://developer.amazon.com/en-US/docs/alexa/hosted-skills/alexa-hosted-skills-ask-cli.html
- https://github.com/alexa/ask-cli/blob/develop/docs/concepts/CI-CD.md
- https://developer.amazon.com/en-US/docs/alexa/smapi/ask-cli-command-reference.html

**Nota:** `docs/ASK_CLI_MAC_AUTH.md` descreve uma opcao local **antiga/alternativa**, nao mais a rota principal do projeto.
