'use strict';
// Cria SOMENTE a definicao Alexa-hosted development. Sem deploy de codigo, certificados ou publicacao.
// Executado apenas por workflow_dispatch apos confirmacao explicita.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const cp = require('node:child_process');

const SKILL_NAME = 'Bravum School Assistant';
const LOCALE = 'pt-BR';
const CONFIRMATION = 'CRIAR_BRAVUM_SCHOOL_ASSISTANT';
const REGION = 'US_EAST_1'; // Amazon gerenciada, nao a conta AWS pessoal.
let temporaryHome;

function safeError(message) {
  // Nao exibir err.message/stack das bibliotecas, pois respostas brutas podem conter dados da conta.
  console.error('ERRO_SKILL=' + message);
  process.exitCode = 1;
}

function call(operation) {
  return new Promise((resolve, reject) => {
    operation((err, response) => {
      if (err) return reject(new Error('SMAPI_FALHOU'));
      resolve(response);
    });
  });
}

async function listAllSkills(client, vendorId) {
  const skills = [];
  let nextToken;
  // Fail closed para evitar duplicacao por paginacao ou resposta inesperada.
  for (let page = 0; page < 30; page += 1) {
    const options = { maxResults: 50 };
    if (nextToken) options.nextToken = nextToken;
    const response = await call((cb) => client.skill.listSkills(vendorId, options, cb));
    if (!response || response.statusCode !== 200 || !Array.isArray(response.body?.skills)) {
      throw new Error('LISTAGEM_INCOMPLETA');
    }
    skills.push(...response.body.skills);
    if (!response.body.isTruncated && !response.body.nextToken) return skills;
    if (nextToken && response.body.nextToken === nextToken) throw new Error('PAGINACAO_INCOMPLETA');
    nextToken = response.body.nextToken;
    if (!nextToken) throw new Error('PAGINACAO_INCOMPLETA');
  }
  throw new Error('MUITAS_PAGINAS');
}

function metadataFound(skill) {
  const localizedName = skill && skill.nameByLocale && skill.nameByLocale[LOCALE];
  return typeof localizedName === 'string' &&
    localizedName.trim().toLocaleLowerCase('pt-BR') === SKILL_NAME.toLocaleLowerCase('pt-BR');
}

async function main() {
  if (process.env.GITHUB_REF !== 'refs/heads/main' ||
      process.env.ALEXA_CREATE_CONFIRMATION !== CONFIRMATION) {
    throw new Error('CONFIRMACAO_OU_BRANCH_INVALIDA');
  }
  const token = process.env.ASK_REFRESH_TOKEN;
  const vendorId = process.env.ASK_VENDOR_ID;
  if (!token || !vendorId || token === 'undefined') throw new Error('SECRETS_AUSENTES');
  if (!/^M[A-Z0-9]{10,20}$/.test(vendorId)) throw new Error('VENDOR_ID_FORMATO_INVALIDO');

  // O modo somente-env da ASK CLI requer tambem credenciais AWS. Nao criamos nem fingimos
  // ter essas credenciais: o perfil privado temporario reproduz a solucao validada no PR #7.
  temporaryHome = fs.mkdtempSync(path.join(os.tmpdir(), 'bravum-ask-create-'));
  fs.chmodSync(temporaryHome, 0o700);
  const askDir = path.join(temporaryHome, '.ask');
  fs.mkdirSync(askDir, {mode: 0o700});
  fs.writeFileSync(path.join(askDir, 'cli_config'), JSON.stringify({
    profiles: {
      default: {
        token: { access_token: '', refresh_token: token, expires_at: '2000-01-01T00:00:00.000Z' },
        vendor_id: vendorId
      }
    }
  }), {mode: 0o600, flag: 'wx'});
  process.env.HOME = temporaryHome;
  delete process.env.AWS_ACCESS_KEY_ID;
  delete process.env.AWS_SECRET_ACCESS_KEY;
  delete process.env.AWS_SESSION_TOKEN;
  delete process.env.ASK_REFRESH_TOKEN;
  delete process.env.ASK_VENDOR_ID;
  delete process.env.ASK_ACCESS_TOKEN;
  delete process.env.ASK_CLI_PROXY;
  delete process.env.HTTP_PROXY;
  delete process.env.HTTPS_PROXY;
  delete process.env.ALL_PROXY;
  delete process.env.http_proxy;
  delete process.env.https_proxy;
  delete process.env.all_proxy;

  const globalModules = cp.execFileSync('npm', ['root', '-g'], {
    encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore']
  }).trim();
  const askPath = path.join(globalModules, 'ask-cli');
  // A interface interna abaixo corresponde a ASK CLI 2.30.7; nao usar @latest.
  // A publicacao npm da ASK CLI compila lib/ para dist/lib/.
  // Nunca importar fontes lib/ de checkout: elas nao existem no pacote instalado.
  const AppConfig = require(path.join(askPath, 'dist/lib/model/app-config'));
  const HostedSkillController = require(path.join(askPath, 'dist/lib/controllers/hosted-skill-controller'));
  new AppConfig(); // Carrega o perfil temporario na instancia compartilhada da CLI.
  const controller = new HostedSkillController({profile: 'default', doDebug: false});

  console.log('FASE=VERIFICAR_DUPLICATAS');
  const existing = await listAllSkills(controller.smapiClient, vendorId);
  const alreadyThere = existing.find(metadataFound);
  if (alreadyThere) {
    console.log('RESULTADO=JA_EXISTE_NENHUMA_SKILL_CRIADA');
    console.log('SKILL_ID=' + alreadyThere.skillId);
    return;
  }

  console.log('FASE=VERIFICAR_PERMISSAO_ALEXA_HOSTED');
  const permission = await call((cb) =>
    controller.getHostedSkillPermission(vendorId, 'newSkill', cb));
  if (permission?.status !== 'ALLOWED') {
    if (permission?.status === 'NEW_USER_REGISTRATION_REQUIRED') {
      throw new Error('CAPTCHA_AMAZON_PENDENTE');
    }
    throw new Error('PERMISSAO_ALEXA_HOSTED_NAO_LIBERADA');
  }

  // Mesma estrutura de payload usada pelo fluxo de criacao Alexa-hosted da ASK CLI.
  // Nao definir ARN ou nome de endpoint: eles serao provisionados pela Amazon.
  const manifest = {
    publishingInformation: {locales: {[LOCALE]: {name: SKILL_NAME}}},
    apis: {custom: {}}
  };
  console.log('FASE=CRIAR_SKILL_ALEXA_HOSTED_DEVELOPMENT');
  const skillId = await call((cb) =>
    controller.createSkill({
      vendorId, manifest, runtime: 'NodeJS', region: REGION
    }, cb));
  if (!/^amzn1\.ask\.skill\.[a-zA-Z0-9-]+$/.test(skillId)) {
    throw new Error('RESPOSTA_SKILL_ID_INESPERADA');
  }
  console.log('RESULTADO=CRIADA_EM_DEVELOPMENT');
  console.log('SKILL_ID=' + skillId);
  console.log('SEM_PUBLICACAO_SEM_DEPLOY_CODIGO_SEM_ALTERAR_LENOVO');
  if (process.env.GITHUB_STEP_SUMMARY) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,
      '## Bravum School Assistant — skill criada (development)\n\n' +
      '- Skill ID: \x60' + skillId + '\x60\n' +
      '- Idioma: pt-BR; hospedagem Alexa-hosted (Amazon)\n' +
      '- Modelo pt-BR e codigo Node ainda **nao enviados**. Nenhuma publicacao live.\n');
  }
}

main().catch((err) => {
  const permitted = [
    'CONFIRMACAO_OU_BRANCH_INVALIDA', 'SECRETS_AUSENTES', 'VENDOR_ID_FORMATO_INVALIDO',
    'LISTAGEM_INCOMPLETA', 'PAGINACAO_INCOMPLETA', 'MUITAS_PAGINAS',
    'CAPTCHA_AMAZON_PENDENTE', 'PERMISSAO_ALEXA_HOSTED_NAO_LIBERADA',
    'RESPOSTA_SKILL_ID_INESPERADA'
  ];
  safeError(permitted.includes(err.message) ? err.message : 'CONSULTA_OU_PROVISIONAMENTO_AMAZON_FALHOU');
}).finally(() => {
  if (temporaryHome) {
    try { fs.rmSync(temporaryHome, {recursive:true, force:true}); }
    catch (_) { console.error('AVISO=FALHA_LIMPEZA_DIRETORIO_TEMPORARIO'); }
  }
});
