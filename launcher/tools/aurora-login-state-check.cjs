const fs=require("fs"),vm=require("vm"),assert=require("assert");
const source=fs.readFileSync(__dirname+"/../app/src/main/assets/aurora/console/login.js","utf8");
async function check(initialized,fail=false){
 const nodes={};const node=id=>nodes[id]||(nodes[id]={hidden:true,textContent:"",value:""});
 const form=node("login");form.elements={password:{},passwordConfirm:{}};form.querySelector=name=>node(name);
 let resolve;const context={document:{getElementById:node},fetch:()=>new Promise(r=>resolve=r),location:{replace(){}}};
 vm.runInNewContext(source,context);assert(form.hidden,"form stays hidden until auth status is known");
 resolve({json:async()=>fail?{ok:false}:{ok:true,data:{initialized,openAccess:false}}});await new Promise(setImmediate);
 if(fail){assert(form.hidden);assert(node("login-loading").textContent.includes("不会重置账号"));return;}
 assert(!form.hidden);assert.equal(node("confirm-row").hidden,initialized);assert.equal(node("h2").textContent,initialized?"登录控制中心":"首次设置你的控制中心");
}
(async()=>{await check(true);await check(false);await check(false,true);console.log("PASS auth status loads before login/setup rendering; failed status never offers account reset");})().catch(e=>{console.error(e);process.exitCode=1;});
