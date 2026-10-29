'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync(__dirname+'/../app/src/main/assets/aurora/console/login.js','utf8');
async function scenario(initialized,confirm,fail=false){
 const nodes={},requests=[];let target='';
 const node=id=>nodes[id]||(nodes[id]={elements:{password:{},passwordConfirm:{}},querySelector:()=>node('button'),getAttribute:()=>String(initialized),setAttribute(){}});
 vm.runInNewContext(source,{document:{getElementById:node},location:{replace:x=>target=x},confirm:()=>confirm,fetch:async(url,options)=>{requests.push({url,body:JSON.parse(options.body)});return{ok:!fail,json:async()=>fail?{ok:false,error:{message:'访问方式已设置'}}:{ok:true,data:{openAccess:true}}};}});
 assert.equal(node('open-setup').hidden,initialized);
 await node('open-setup').onclick();
 assert.equal(requests.length,!initialized&&confirm?1:0);
 if(requests.length){assert.equal(requests[0].url,'/api/v1/auth/open-setup');assert.equal(requests[0].body.confirmed,true);assert.equal(target,fail?'':'/console/app.html?setup=1#security');}
}
(async()=>{await scenario(false,true);await scenario(false,false);await scenario(true,true);await scenario(false,true,true);console.log('PASS fresh-install password-free choice / confirmation / existing account hidden / explicit failure');})().catch(e=>{console.error(e);process.exitCode=1;});
