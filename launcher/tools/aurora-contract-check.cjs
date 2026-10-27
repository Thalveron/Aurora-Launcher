#!/usr/bin/env node
'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const root=path.resolve(__dirname,'../app/src/main/assets/aurora'),contract=JSON.parse(fs.readFileSync(path.join(root,'core/contract.json')));
const storage=new Map(),context={crypto:require('crypto'),Element:class Element{},window:{},fetch:async url=>({json:async()=>JSON.parse(fs.readFileSync(path.join(root,url.replace(/^\//,''))))}),localStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},URLSearchParams,location:{search:'?mock=1'},document:{hidden:false,addEventListener(){}},setInterval,clearInterval,setTimeout,clearTimeout,AbortController,console};context.window.addEventListener=()=>{};
vm.createContext(context);vm.runInContext(fs.readFileSync(path.join(root,'core/mock.js'),'utf8'),context);vm.runInContext(fs.readFileSync(path.join(root,'core/api.js'),'utf8'),context);
function validate(schema,value,label){
 const type=schema.type;assert(type==='array'?Array.isArray(value):type==='object'?value!==null&&typeof value==='object'&&!Array.isArray(value):type==='integer'?Number.isInteger(value):typeof value===type,label+': expected '+type);
 if(type==='object'){for(const key of schema.required||[])assert(Object.prototype.hasOwnProperty.call(value,key),label+': missing '+key);for(const[key,s]of Object.entries(schema.properties||{}))if(value[key]!==undefined)validate(s,value[key],label+'.'+key);}
 if(type==='array'&&schema.items)value.forEach((v,i)=>validate(schema.items,v,label+'['+i+']'));
}
function envelope(r){assert(typeof r.ok==='boolean');if(r.ok)assert(Object.prototype.hasOwnProperty.call(r,'data'));else{assert(typeof r.error.code==='string');assert(typeof r.error.message==='string');}}
(async()=>{
 for(const[route,schema]of Object.entries(contract.schemas)){const value=await context.window.AuroraAPI.mockResponse(route);envelope(value);assert(value.ok,route+': '+JSON.stringify(value));validate(schema,value.data,'mock/'+route);console.log('PASS mock schema',route);}
 const beforeLanguage=await context.window.AuroraAPI.request('config');
 await context.window.AuroraAPI.request('config',{consoleLocale:'en-US'});
 assert.equal((await context.window.AuroraAPI.request('config')).locale,beforeLanguage.locale);
 await context.window.AuroraAPI.request('config',{locale:'en-US',consoleLocale:'zh-CN'});
 const splitLanguage=await context.window.AuroraAPI.request('config');assert.equal(splitLanguage.locale,'en-US');assert.equal(splitLanguage.consoleLocale,'zh-CN');
 const invalidLanguage=await context.window.AuroraAPI.mockResponse('config',{consoleLocale:'bad'});assert(!invalidLanguage.ok);
 console.log('PASS independent desktop and console languages');
 const actions=await context.window.AuroraAPI.request('actions');assert.deepStrictEqual(JSON.parse(JSON.stringify(actions)),contract.actions);
 for(const action of [{action:'unknown',params:{}},{action:'launch_app',params:{package:12}},{action:'launch_app',params:{package:'demo.music',extra:true}},{action:'set_appearance',params:{patch:{glassBlur:999}}},{action:'media_mode',params:{mode:6}}]){const r=await context.window.AuroraAPI.mockResponse('actions',action);envelope(r);assert(!r.ok);assert.equal(r.error.code,'INVALID_ARGUMENT');}
 for(const route of ['ai/chat','themes/upload','icons']){const r=await context.window.AuroraAPI.mockResponse(route,{});envelope(r);assert.equal(r.error.code,'NOT_IMPLEMENTED');}
 let r=await context.window.AuroraAPI.mockResponse('actions',{action:'set_appearance',params:{patch:{glassBlur:21}}});assert(r.ok);assert.equal((await context.window.AuroraAPI.request('appearance')).glassBlur,21);
 await context.window.AuroraAPI.request('actions',{action:'switch_theme',params:{theme:'foundation'}});
 await context.window.AuroraAPI.request('actions',{action:'launch_app',params:{package:'demo.music'}});
 for(const[alias,canonical]of Object.entries(contract.aliases)){if(alias.includes('*'))continue;assert.deepStrictEqual(JSON.parse(JSON.stringify(await context.window.AuroraAPI.request(alias))),JSON.parse(JSON.stringify(await context.window.AuroraAPI.request(canonical))),alias);}
 if(process.argv[2]){const live=JSON.parse(fs.readFileSync(process.argv[2]));for(const[route,schema]of Object.entries(contract.schemas)){envelope(live[route]);assert(live[route].ok,route);validate(schema,live[route].data,'device/'+route);}assert.deepStrictEqual(live.actions.data,contract.actions);console.log('PASS real device and mock share the contract and action registry');}
 const api=context.window.AuroraAPI;
 const customOne=await api.request('themes/custom',{name:'One',base:'foundation',css:':root { --accent: #123456; }'});
 const customTwo=await api.request('themes/custom',{name:'Two',base:'foundation',css:':root { --accent: #654321; }'});
 assert.notEqual(customOne.id,customTwo.id);
 assert.notEqual((await api.request('themes/css/'+customOne.id)).css,(await api.request('themes/css/'+customTwo.id)).css);
 assert(!(await api.request('themes')).some(t=>t.id==='sidebar'));
 await api.request('actions',{action:'switch_theme',params:{theme:customOne.id}});
 assert.equal((await api.request('config')).theme,customOne.id);
 await api.request('actions',{action:'switch_theme',params:{theme:'foundation'}});
 await api.request('themes/custom/delete',{id:customOne.id,confirm:true});
 assert(!(await api.request('themes')).some(t=>t.id===customOne.id));
 console.log('PASS independent CSS, multiple custom themes and switching');
 console.log('PASS v1 compatibility, action validation and error envelopes');
})().catch(e=>{console.error(e);process.exitCode=1;});
