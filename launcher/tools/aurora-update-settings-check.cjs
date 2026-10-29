'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync(__dirname+'/../app/src/main/assets/aurora/console/updates.js','utf8');
async function scenario(fail){
 const nodes={},calls=[],intervals=[];let draft,stored={autoCheck:true,automatic:false};
 const copy=value=>JSON.parse(JSON.stringify(value));
 const node=id=>nodes[id]||(nodes[id]={hidden:false,addEventListener(){}});
 const state=()=>Object.assign({phase:'idle',currentVersion:'1.0.0',maintenanceAvailable:true,busy:false,message:'',sources:[],routes:[]},stored);
 const UI={draft(id,options){draft={value:copy(options.initial),baseline:copy(options.initial),options};draft.set=v=>{draft.value=copy(v);options.render(v);};draft.replace=v=>{if(JSON.stringify(draft.value)!==JSON.stringify(draft.baseline))return false;draft.value=draft.baseline=copy(v);options.render(v);return true;};return draft;}};
 const api={request:async(route,body)=>{calls.push(route);if(route==='updates/config'){if(fail)throw Error('磁盘保存失败');stored=copy(body);}return state();}};
 vm.runInNewContext(source,{window:{AuroraAPI:api,AuroraConsoleUI:UI},document:{getElementById:node,querySelector:()=>node('panel'),addEventListener(){}},location:{hash:'#updates'},setInterval:f=>(intervals.push(f),1),clearInterval(){},setTimeout(){}});
 await new Promise(setImmediate);assert(draft);
 node('update-auto-check').checked=false;node('update-auto-check').onchange();
 assert.equal(draft.value.autoCheck,false);assert(!calls.includes('updates/config'),'toggle stages until Save');
 intervals.forEach(f=>f());await new Promise(setImmediate);
 assert.equal(node('update-auto-check').checked,false,'polling cannot overwrite unsaved toggle');
 if(fail){await assert.rejects(()=>draft.options.save(draft.value),/磁盘/);assert.notDeepEqual(draft.value,draft.baseline);return;}
 const result=await draft.options.save(draft.value);draft.baseline=copy(draft.value);draft.options.saved(result);
 intervals.forEach(f=>f());await new Promise(setImmediate);assert.equal(node('update-auto-check').checked,false);
 node('update-automatic').checked=true;node('update-automatic').onchange();assert.equal(draft.value.autoCheck,true);assert.equal(draft.value.automatic,true);
}
(async()=>{await scenario(false);await scenario(true);console.log('PASS update policy draft / polling / save / coupled switches / failed save keeps edits');})().catch(e=>{console.error(e);process.exitCode=1;});
