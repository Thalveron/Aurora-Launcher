'use strict';
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/aurora/console/login.js'),'utf8');
async function run(status,accept){
 const nodes=new Map();function el(id){if(!nodes.has(id))nodes.set(id,{hidden:false,textContent:'',value:'',required:false,disabled:false});return nodes.get(id);}
 const form=el('login');form.elements={username:el('username'),password:el('password'),passwordConfirm:el('passwordConfirm')};form.querySelector=s=>el(s);
 const requests=[],redirects=[];const context={document:{getElementById:el},location:{search:'',origin:'http://localhost',replace:u=>redirects.push(u)},URL,URLSearchParams,confirm:()=>accept,fetch:async(url,options)=>{requests.push({url,options});return{ok:true,json:async()=>({ok:true,data:url.endsWith('/status')?status:{requireAuth:false}})};}};
 vm.runInNewContext(source,context);for(let i=0;i<5;i++)await new Promise(setImmediate);
 return{nodes,requests,redirects};
}
(async()=>{
 let state=await run({initialized:false,requireAuth:false},false);assert.deepEqual(state.redirects,['/console/']);assert.equal(state.requests.length,1);
 state=await run({initialized:false,requireAuth:true},false);assert.equal(state.nodes.get('setup-open').hidden,false);await state.nodes.get('setup-open').onclick();assert.equal(state.requests.length,1,'Cancelling open mode must not change protection');
 state=await run({initialized:false,requireAuth:true},true);await state.nodes.get('setup-open').onclick();assert.equal(state.requests[1].url,'/api/v1/auth/setup-open');assert.equal(JSON.parse(state.requests[1].options.body).confirmed,true);
 state=await run({initialized:true,requireAuth:true},true);assert.equal(state.nodes.get('setup-open').hidden,true);assert.equal(state.redirects.length,0);
 console.log('PASS protected login, open-mode redirect and explicit first-setup confirmation');
})().catch(e=>{console.error(e);process.exitCode=1;});
