'use strict';
const {test,afterEach}=require('node:test');
const assert=require('node:assert/strict');
const originalFetch=global.fetch;
const originalEnv={...process.env};
const id='amzn1.ask.account.TEST12345';
const {api}=require('../api');
afterEach(()=>{
  global.fetch=originalFetch;
  for (const key of ['SCHOOL_API_BASE_URL','SCHOOL_ALEXA_SKILL_KEY']) {
    if(originalEnv[key]===undefined)delete process.env[key];
    else process.env[key]=originalEnv[key];
  }
});
test('falha fechada quando ambiente não configurado',async()=>{
  await assert.rejects(api(id,'alexa/skill/summary'),/CONFIGURATION_REQUIRED/);
});
test('não permite caminhos administrativos',async()=>{
  await assert.rejects(api(id,'alexa/admin/links'),/INVALID_ROUTE|CONFIGURATION_REQUIRED/);
});
