'use strict';
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const elements=new Map();
function element(id){if(!elements.has(id))elements.set(id,{hidden:false,files:[],value:'',textContent:'',addEventListener(){},click(){}});return elements.get(id);}
let calls=[],actions=[];
const context={window:{AuroraAPI:{request:async(...args)=>{actions.push(args);return {};}}},document:{hidden:false,getElementById:element,querySelector:()=>({hidden:false,addEventListener(){}}),addEventListener(){}},location:{hash:''},setInterval(){},clearInterval(){},confirm:()=>false,prompt:()=>null,fetch:async(url,options)=>{calls.push({url,options});return {ok:true,json:async()=>({ok:true,data:{version:'0.1.2',bytes:1024,uploadToken:'test'}})};}};
vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/aurora/console/updates.js'),'utf8'),context);
(async()=>{
 const input=element('update-apk-file');input.files=[{name:'Aurora Launcher.APK'}];
 await input.onchange.call(input);
 assert.equal(calls.length,1);assert.equal(calls[0].url,'/api/v1/updates/upload');assert.equal(calls[0].options.credentials,'same-origin');
 assert.equal(actions.length,0,'Cancelling upload confirmation must never install');
 assert.match(element('update-status').textContent,/已取消安装/);
 input.files=[{name:'bad.txt'}];await input.onchange.call(input);
 assert.equal(calls.length,1,'Reject other extensions before upload');assert.match(element('update-status').textContent,/APK/);
 console.log('PASS APK upload accepts valid filename, rejects other types and cancel never installs');
})().catch(e=>{console.error(e);process.exitCode=1;});
