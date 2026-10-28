'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync(__dirname+'/../app/src/main/assets/aurora/console/updates.js','utf8');
async function scenario(accept,fail=false,file={name:'Aurora-Launcher-v0.2.6.apk',size:20000000}){
 const nodes={},calls=[],uploads=[];let confirmations=0;
 const node=id=>nodes[id]||(nodes[id]={hidden:false,value:'',addEventListener(){},click(){}});
 const state=()=>({phase:'idle',currentVersion:'0.2.6',busy:false,message:'',routes:[]});
 class Xhr{
  constructor(){this.upload={};}
  open(method,url){this.method=method;this.url=url;}
  setRequestHeader(name,value){this.header=[name,value];}
  send(body){uploads.push(this);this.body=body;this.status=fail?400:200;this.responseText=JSON.stringify(fail?{ok:false,error:{message:'签名不匹配'}}:{ok:true,data:{version:'0.2.6',bytes:file.size,sha256:'digest',uploadToken:'single-use'}});this.upload.onprogress({lengthComputable:true,loaded:file.size,total:file.size});this.onload();}
 }
 const context={window:{AuroraAPI:{request:async(route,body)=>{calls.push({route,body});return state();}}},document:{hidden:false,getElementById:node,querySelector:()=>node('panel'),addEventListener(){}},location:{hash:'',hostname:'tv'},XMLHttpRequest:Xhr,confirm(){confirmations++;return accept;},setTimeout(){},setInterval:()=>1,clearInterval(){}};
 vm.runInNewContext(source,context);node('update-apk-file').files=[file];await node('update-apk-file').onchange();
 const valid=/\.apk$/i.test(file.name)&&file.size>=1024&&file.size<=512*1048576;
 assert.equal(uploads.length,valid?1:0);
 if(!valid||fail){assert.equal(confirmations,0);assert(!calls.some(c=>c.route.includes('install')));return;}
 assert.equal(uploads[0].url,'/api/v1/updates/upload');assert.equal(uploads[0].body,file);assert.equal(uploads[0].withCredentials,true);assert.equal(confirmations,1);
 if(accept){const install=calls.find(c=>c.route==='updates/install-upload');assert(install);assert.equal(install.body.confirmed,true);assert.equal(install.body.uploadToken,'single-use');}else assert(calls.some(c=>c.route==='updates/cancel'));
 assert(!calls.some(c=>c.route.includes('clean')),'manual update must never clear user data');
}
(async()=>{await scenario(true);await scenario(false);await scenario(true,true);await scenario(true,false,{name:'wrong.txt',size:10000});await scenario(true,false,{name:'large.apk',size:513*1048576});console.log('PASS manual APK upload / explicit confirmation / cancellation / validation errors / no clean-install route');})().catch(e=>{console.error(e);process.exitCode=1;});
