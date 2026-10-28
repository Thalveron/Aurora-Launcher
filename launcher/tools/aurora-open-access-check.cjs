'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync(__dirname+'/../app/src/main/assets/aurora/console/login.js','utf8');
async function check(openAccess){
 const nodes={},node=id=>nodes[id]||(nodes[id]={value:'',querySelector:()=>({}),elements:{passwordConfirm:{},password:{}}});let destination='',count=0;
 vm.runInNewContext(source,{document:{getElementById:node},location:{replace:to=>destination=to},fetch:async()=>{count++;return{json:async()=>({ok:true,data:{initialized:true,openAccess}})};}});
 await new Promise(setImmediate);assert.equal(count,1);assert.equal(destination,openAccess?'/console/':'');
}
(async()=>{await check(true);await check(false);console.log('PASS optional password-free entry; protected mode still waits for login');})().catch(e=>{console.error(e);process.exitCode=1;});
