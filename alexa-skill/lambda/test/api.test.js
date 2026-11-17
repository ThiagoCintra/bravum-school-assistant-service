'use strict';
const {test,afterEach}=require('node:test');
const assert=require('node:assert/strict');
const {api}=require('../api');
const originalFetch=global.fetch;
const originalEnv={...process.env};
const id='amzn1.ask.account.TEST12345';
afterEach(()=>{
  global.fetch=originalFetch;
  for(const key of ['SCHOOL_API_BASE_URL','SCHOOL_ALEXA_SKILL_KEY']){
    if(originalEnv[key]===undefined)delete process.env[key];
    else process.env[key]=originalEnv[key];
  }
});
function configure(){
  process.env.SCHOOL_API_BASE_URL='https://family.example/api/v1';
  process.env.SCHOOL_ALEXA_SKILL_KEY='a'.repeat(48);
}
test('falha fechado sem configuração',async()=>{
  delete process.env.SCHOOL_API_BASE_URL;
  delete process.env.SCHOOL_ALEXA_SKILL_KEY;
  await assert.rejects(api(id,'alexa/skill/summary'),/CONFIGURATION_REQUIRED/);
});
test('bloqueia rotas administrativas e IDs falsos',async()=>{
  configure();
  await assert.rejects(api(id,'alexa/admin/links'),/INVALID_ROUTE/);
  await assert.rejects(api('arbitrary-id','alexa/skill/summary'),/INVALID_ALEXA_USER/);
});
test('recusa HTTP sem TLS',async()=>{
  configure();process.env.SCHOOL_API_BASE_URL='http://family.example/api/v1';
  await assert.rejects(api(id,'alexa/skill/summary'),/CONFIGURATION_REQUIRED/);
});
test('envia somente token da skill e userId, nunca chave administrativa',async()=>{
  configure();let called=false;
  global.fetch=async (url,options)=>{
    called=true;
    assert.equal(url.href,'https://family.example/api/v1/alexa/skill/summary');
    assert.equal(options.method,'GET');
    assert.equal(options.headers.Authorization,'Bearer '+'a'.repeat(48));
    assert.equal(options.headers['X-Alexa-User-Id'],id);
    assert.equal(options.headers['X-Tenant-Id'],undefined);
    return {ok:true,status:200,json:async()=>({recentCommunications:0,pendingAssignments:0})};
  };
  const r=await api(id,'alexa/skill/summary');
  assert.equal(called,true);assert.equal(r.recentCommunications,0);
});
test('nega consulta sem vínculo',async()=>{
  configure();global.fetch=async()=>({ok:false,status:403});
  await assert.rejects(api(id,'alexa/skill/messages'),/UNLINKED/);
});
test('POST de vínculo não inclui dados de crianças',async()=>{
  configure();
  global.fetch=async (_url,options)=>{
    assert.equal(options.method,'POST');
    assert.deepEqual(JSON.parse(options.body),{code:'12345678'});
    return {ok:true,status:200,json:async()=>({status:'LINKED'})};
  };
  assert.deepEqual(await api(id,'alexa/skill/link',{code:'12345678'}),{status:'LINKED'});
});
