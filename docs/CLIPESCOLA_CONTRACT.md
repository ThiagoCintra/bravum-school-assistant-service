# Adaptador ClipEscola — limites e contrato

## O que foi observado no Mac

- O portal autenticado expôs feeds HTML e respostas AJAX/JSF em `/acesso/mobile/feed.xhtml`.
- O parâmetro `q` e índices de componentes JSF não são identificadores permanentes demonstrados.
- Foram vistos dois contextos de feed diferentes; associação estável às duas filhas ainda não foi validada.
- `/acesso/ws/feed` foi observado, mas não foi comprovada a chegada espontânea de novos comunicados.
- Não se confirmou que o canal WebSocket e as páginas privadas sejam API oficial para integração contínua.

## Design seguro para importação

1. **Codex no Mac consulta, não desenvolve**: executa apenas a leitura autorizada da sessão do responsável e produz `communications.jsonl`.
2. O serviço Java importa registros sanitizados pelo contrato em `docs/API.md`, com autenticação M2M.
3. O identificador `sourceFingerprint` é SHA-256 de origem + conexão + (ID externo quando comprovado, senão conteúdo visível).
4. Uma comunicação pode ter vários destinatários/contextos; nenhum contexto vira uma filha automaticamente.
5. Datas de cerimônia, publicação e prazo devem permanecer separadas. Sem evidência de prazo, `dueAt = null`.
6. O usuário revisa possíveis atividades, confirma vínculos, e só então gera lembretes.
7. Não se usa cookies ou tokens do ClipEscola no backend. Não automatizar endpoints privados sem contrato/autorização do fornecedor.

## Integração futura

Criar conector oficial ou mecanismo permitido pela ClipEscola, mediante documentação e autorização. Até lá, importação manual/assistida do JSONL, **sem promessas de push em tempo real**.
