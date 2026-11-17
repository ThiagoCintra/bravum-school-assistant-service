#!/usr/bin/env bash
# Bootstrap seguro da ASK CLI no Mac. Nao cria, atualiza, publica ou faz deploy de skills.
set -euo pipefail
umask 077
PROFILE="bravum-school-assistant"

if [[ "${OSTYPE:-}" != darwin* ]]; then
  printf '%s\n' "Este procedimento e destinado ao macOS; nenhum comando foi executado."
  exit 1
fi
if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  printf '%s\n' "Node.js e npm nao encontrados. Instale Node.js LTS por um metodo confiavel antes de continuar."
  exit 2
fi

printf '%s\n' "Instalando/verificando a ASK CLI oficial do pacote npm ask-cli..."
if ! command -v ask >/dev/null 2>&1; then
  npm install --global ask-cli
fi
ask --version

printf '%s\n' "A autorizacao sera aberta no navegador deste Mac."
printf '%s\n' "Use a conta Amazon Developer. Para Alexa-hosted nao e preciso vincular a conta AWS."
printf '%s\n' "Nao copie tokens, senhas, arquivos ~/.ask ou URLs OAuth para o chat/GitHub."
ask configure --profile "$PROFILE"

printf '%s\n' "Validacao SOMENTE LEITURA: listagem das skills da conta autorizada."
ask smapi list-skills-for-vendor --profile "$PROFILE"
printf '%s\n' "Configuracao concluida. Nenhuma skill foi criada, implantada ou publicada."
