'use strict';
const Alexa = require('ask-sdk-core');
const { api } = require('./api');

function identity(h) {
  const e = h.requestEnvelope;
  const appId = e?.context?.System?.application?.applicationId || e?.session?.application?.applicationId;
  const userId = e?.context?.System?.user?.userId || e?.session?.user?.userId;
  // O trigger Alexa da Lambda deve estar restrito ao ID cadastrado na skill.
  if (!process.env.ALEXA_SKILL_ID || appId !== process.env.ALEXA_SKILL_ID) throw new Error('SKILL_ID_MISMATCH');
  return userId;
}
function escapeSpeech(value) {
  return String(value || '').slice(0, 140).replace(/&/g,'&amp;').replace(/</g,'&lt;')
    .replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&apos;');
}
const speak = (h,text) => h.responseBuilder.speak(text).getResponse();
const matches = (h,name) =>
  Alexa.getRequestType(h.requestEnvelope) === 'IntentRequest' && Alexa.getIntentName(h.requestEnvelope) === name;
async function answer(h, action) {
  try { return speak(h,await action(identity(h))); }
  catch (error) {
    if (error.message === 'UNLINKED')
      return speak(h,'Esta conta Amazon ainda não foi vinculada à sua família. Solicite um código temporário e diga vincular, seguido do código.');
    return speak(h,'Não consegui consultar o assistente escolar. Tente novamente mais tarde.');
  }
}
const Launch = { canHandle:h=>Alexa.getRequestType(h.requestEnvelope)==='LaunchRequest',
  handle:h=>speak(h,'Assistente escolar. Você pode pedir o resumo, os recados ou as tarefas de amanhã.') };
const Summary = {canHandle:h=>matches(h,'ResumoIntent'),
  handle:h=>answer(h,async id=>{
    const d=await api(id,'alexa/skill/summary');
    return 'Há '+Number(d.recentCommunications||0)+' comunicados recentes e '+
      Number(d.pendingAssignments||0)+' atividades pendentes confirmadas.';
  })};
const Messages = {canHandle:h=>matches(h,'RecadosIntent'),
  handle:h=>answer(h,async id=>{
    const d=await api(id,'alexa/skill/messages');
    if (!d.items?.length) return 'Nenhum comunicado recente cadastrado.';
    return 'Há '+Number(d.total||d.items.length)+' comunicados recentes. '+
      d.items.slice(0,3).map(x=>escapeSpeech(x.title)).join('. ')+'.';
  })};
const Agenda = {canHandle:h=>matches(h,'TarefasIntent'),
  handle:h=>answer(h,async id=>{
    const slot=Alexa.getSlotValue(h.requestEnvelope,'dia');
    const day=slot === 'hoje' ? 'hoje' : 'amanhã';
    const query=day==='hoje'?'hoje':'amanh%C3%A3';
    const d=await api(id,'alexa/skill/agenda?day='+query);
    if (!d.items?.length) return 'Não encontrei atividade com prazo confirmado para '+day+'.';
    return 'Atividades para '+day+': '+d.items.map(x=>escapeSpeech(x.title)).join('. ')+'.';
  })};
const Link = {canHandle:h=>matches(h,'VincularIntent'),
  handle:h=>answer(h,async id=>{
    const code=String(Alexa.getSlotValue(h.requestEnvelope,'codigo')||'').replace(/\D/g,'');
    if (!/^\d{8}$/.test(code)) return 'O código temporário deve ter oito dígitos.';
    const response=await api(id,'alexa/skill/link',{code});
    return response.status==='LINKED'
      ? 'A conta Amazon foi vinculada. Você já pode consultar os recados.'
      : 'O código não foi aceito. Confira se ainda está válido.';
  })};
const Subscription = {
  canHandle:h=>Alexa.getRequestType(h.requestEnvelope)==='AlexaSkillEvent.ProactiveSubscriptionChanged',
  handle:async h=>{
    try {
      const events=h.requestEnvelope.request?.body?.subscriptions||[];
      const enabled=events.some(e=>e.eventName==='AMAZON.MessageAlert.Activated');
      await api(identity(h),'alexa/skill/subscription',{
        enabled,eventTime:h.requestEnvelope.request.timestamp
      });
    } catch (_error) {
      // Evento pode preceder o vínculo. Permitir a subscrição depois do pareamento.
      // Não registrar identificadores pessoais ou tokens em logs.
    }
    return h.responseBuilder.getResponse();
  }};
const Help = {canHandle:h=>matches(h,'AMAZON.HelpIntent'),
  handle:h=>speak(h,'Você pode perguntar: resumo escolar, quais são os recados ou tarefas para amanhã.')};
const Stop = {canHandle:h=>matches(h,'AMAZON.StopIntent')||matches(h,'AMAZON.CancelIntent'),
  handle:h=>speak(h,'Até mais.')};
const Fallback = {canHandle:h=>Alexa.getRequestType(h.requestEnvelope)==='IntentRequest',
  handle:h=>speak(h,'Não entendi. Peça os recados ou as tarefas de amanhã.')};
const ErrorHandler = {canHandle:()=>true, handle:h=>speak(h,'O assistente escolar está indisponível.')};
exports.handler=Alexa.SkillBuilders.custom()
  .addRequestHandlers(Launch,Summary,Messages,Agenda,Link,Subscription,Help,Stop,Fallback)
  .addErrorHandlers(ErrorHandler).lambda();
