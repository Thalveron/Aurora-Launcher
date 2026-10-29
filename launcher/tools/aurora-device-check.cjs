'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const root=path.resolve(__dirname,'../app/src/main/assets/aurora'),storage=new Map();
const context={window:{},fetch:async url=>({json:async()=>JSON.parse(fs.readFileSync(path.join(root,url.replace(/^\//,''))))}),localStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v)},URLSearchParams,crypto:require('crypto')};
vm.createContext(context);vm.runInContext(fs.readFileSync(path.join(root,'core/mock.js'),'utf8'),context);
const request=async(p,b)=>{const r=await context.window.AuroraMock.response(p,b);assert(r.ok,JSON.stringify(r));return r.data;};
(async()=>{
 const before=await request('apps/manage');assert(before.apps.length>=8);const app=before.apps[0],pkg=app.package;
 for(const field of ['name','originalName','launchable','visible','enabled','system','canToggle','toggleReason'])assert(field in app,field);
 await request('apps/manage',{package:pkg,name:'临时名称'});assert.equal((await request('apps')).find(a=>a.package===pkg).name,'临时名称');assert.equal((await request('bootstrap')).apps.find(a=>a.package===pkg).name,'临时名称');
 let result=await request('apps/manage',{package:pkg,visible:false});assert.equal(result.apps.find(a=>a.package===pkg).visible,false);assert(result.apps.some(a=>a.system&&!a.canToggle&&a.toggleReason));
 const protectedApp=result.apps.find(a=>a.system&&!a.canToggle);assert.equal((await context.window.AuroraMock.response('apps/manage',{package:protectedApp.package,enabled:false,confirm:true})).ok,false);
 const system=result.apps.find(a=>a.system&&a.canToggle);assert.equal((await context.window.AuroraMock.response('apps/manage',{package:system.package,enabled:false})).ok,false);result=await request('apps/manage',{package:system.package,enabled:false,confirm:true});assert.equal(result.apps.find(a=>a.package===system.package).enabled,false);
 const caps=await request('system/capabilities');for(const k of ['key','launch','screenshot','standby'])assert.strictEqual(caps[k].available,false,'mock must never send real device controls');
 console.log('PASS application wire fields / rename visible in bootstrap / hidden state / protected system app / explicit confirmation / mock controls unavailable');
})().catch(e=>{console.error(e);process.exitCode=1;});
