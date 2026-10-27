'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const script=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/aurora/console/login.js'),'utf8');
async function scenario(initialized,match=true){
 const message={},button={},title={},confirm={};let target='',requests=[];
 const form={elements:{username:{value:'owner'},password:{value:'password123'},passwordConfirm:{value:match?'password123':'different'}},querySelector:s=>s==='button'?button:title};
 const context={document:{getElementById:id=>id==='login'?form:id==='message'?message:confirm},URL,URLSearchParams,location:{origin:'http://tv:8899',search:'',replace:v=>target=v},fetch:async(url,opts)=>{requests.push([url,opts]);return {ok:true,json:async()=>url.endsWith('status')?{ok:true,data:{initialized}}:{ok:true,data:{needsPath:!initialized}}};}};
 vm.runInNewContext(script,context);await new Promise(r=>setImmediate(r));
 assert.equal(confirm.hidden,initialized);
 await form.onsubmit({preventDefault(){},currentTarget:form});
 if(!match){assert.equal(requests.length,1);assert.equal(target,'');return;}
 assert.equal(requests[1][0],'/api/v1/auth/'+(initialized?'login':'setup'));
 assert.equal(target,initialized?'/console/':'/console/settings.html?setup=1#security');
}
(async()=>{await scenario(false);await scenario(false,false);await scenario(true);console.log('PASS first setup / password confirmation / existing login');})().catch(e=>{console.error(e);process.exitCode=1;});
