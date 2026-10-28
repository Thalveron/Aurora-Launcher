'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/aurora/console/updates.js'),'utf8');
async function scenario(install){
 const nodes={},calls=[],tasks=[];let phase='idle',prompts=0;
 const node=id=>nodes[id]||(nodes[id]={addEventListener(){},hidden:false});
 const state=()=>({phase,currentVersion:'0.2.1',busy:false,message:'test',routes:[]});
 const api={request:async(route,body)=>{calls.push({route,body});if(route==='updates/download')phase='ready';else if(route==='updates/install')phase='installing';else if(route==='updates/cancel')phase='idle';if(route==='updates/clean/confirm')return{confirmationToken:'one-use-ticket'};return state();}};
 const context={window:{AuroraAPI:api},document:{getElementById:node,querySelector:()=>node('panel'),addEventListener(){}},location:{hash:'',hostname:'tv',replace(){}},confirm(){prompts++;return install;},setTimeout:f=>tasks.push(f),setInterval:()=>0,clearInterval(){}};
 vm.runInNewContext(source,context);
 node('update-check').onclick();await new Promise(setImmediate);
 assert.deepEqual(calls.map(x=>x.route),['updates/download']);
 assert.equal(prompts,0,'download cannot install without an explicit decision');
 tasks.shift()();await new Promise(setImmediate);
 assert.equal(calls[1].route,install?'updates/install':'updates/cancel');assert.equal(prompts,1);
 if(install){calls.length=0;prompts=0;await node('update-clean').onclick();assert.equal(prompts,1);assert.deepEqual(calls.map(x=>x.route),['updates/clean/confirm','updates/clean']);assert.equal(calls[1].body.confirmationToken,'one-use-ticket');}
}
(async()=>{await scenario(true);await scenario(false);console.log('PASS download requires install/cancel decision; clean uses one warning and server ticket');})().catch(e=>{console.error(e);process.exitCode=1;});
