'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync(__dirname+'/../app/src/main/assets/aurora/core/api.js','utf8');
function setup(actual){
 const calls=[];
 const context={Element:class {},URLSearchParams,location:{search:'',pathname:'/console/app.html',host:'tv:8899'},document:{addEventListener(){}},window:{addEventListener(){}},setTimeout,clearTimeout,AbortController,
  fetch:async(url,options)=>{calls.push([url,options.method||'GET']);return {ok:true,status:200,json:async()=>({ok:true,data:options.method==='POST'?{volume:35}:actual})};}};
 context.Element.prototype.replaceChildren=function(){};vm.runInNewContext(source,context);return {api:context.window.AuroraAPI,calls};
}
(async()=>{
 let sample=setup({volume:35});await sample.api.request('bgm/volume',{volume:35});assert.deepEqual(sample.calls,[['/api/v1/bgm/volume','POST'],['/api/v1/bgm','GET']]);
 sample=setup({volume:5});await assert.rejects(()=>sample.api.request('bgm/volume',{volume:35}),error=>error.code==='READBACK_MISMATCH');
 sample=setup({volume:35});await sample.api.request('bgm');assert.equal(sample.calls.length,1,'ordinary reads are not doubled');
 console.log('PASS successful write readback and mismatch failure without fake sync success');
})().catch(error=>{console.error(error);process.exitCode=1;});
