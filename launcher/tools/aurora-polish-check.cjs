'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const base=__dirname+'/../app/src/main/assets/aurora/console/';
const polish=fs.readFileSync(base+'polish.js','utf8'),settings=fs.readFileSync(base+'settings.js','utf8');
function extract(source,start,end){return source.slice(source.indexOf(start),source.indexOf(end,source.indexOf(start)));}
const libraryCalls=[];
const state={desktop:['a','b'],saver:[]},ctx={Set,JSON,Array,selected:new Set(['a','b']),activeBackground:'desktop',orders:{desktop:['a','b','c'],saver:['c','b','a']},
 view:()=>({state:()=>state,choose:(scope,ids)=>libraryCalls.push([scope,Array.from(ids)]),deleteAssets:()=>{throw Error('must not delete');}}),backgrounds:()=>{},confirm:()=>false};
vm.createContext(ctx);
vm.runInContext(extract(polish,'function selectedOrder()','async function deleteSelected()')+extract(polish,'async function deleteSelected()','function backgrounds(scope)')+';this.actions={selectedOrder,applyBackground,deleteSelected,moveOrder};',ctx);
(async()=>{
 ctx.actions.moveOrder('desktop','b','a');assert.deepEqual(Array.from(ctx.orders.desktop),['b','a','c']);assert.deepEqual(libraryCalls.pop(),['desktop',['b','a']]);
 ctx.orders.desktop=['a','b','c'];ctx.actions.moveOrder('desktop','c','b');assert.equal(libraryCalls.length,0,'reordering unused media must not change the applied set');
 ctx.orders.desktop=['c','b','a'];assert.deepEqual(Array.from(ctx.actions.selectedOrder()),['b','a']);ctx.actions.applyBackground('saver');assert.deepEqual(libraryCalls.pop(),['saver',['b','a']]);
 await ctx.actions.deleteSelected();assert.equal(libraryCalls.length,0,'cancelling deletion must not write');
 const uploadSource=extract(settings,'async function uploadAssets(','  el(\'asset-upload\').onchange');
 async function upload(failAt){let seen=[],headers=[],requests=0;const meter={hidden:true,value:0},status={textContent:''},input={id:'background-upload',files:[],value:'selected',disabled:false};
  class XHR{open(method,path){assert.equal(method,'POST');assert.equal(path,'/api/v1/assets/upload');}setRequestHeader(k,v){headers.push([k,v]);}send(file){seen.push(file.name);const n=++requests;Promise.resolve().then(()=>{this.upload.onprogress({lengthComputable:true,loaded:5,total:10});if(n===failAt)this.onerror();else{this.responseText='{"ok":true,"data":{}}';this.onload();}});}upload={};}
  const u={XMLHttpRequest:XHR,Promise,Array,Error,JSON,encodeURIComponent,el:()=>meter,api:{request:async path=>{assert.equal(path,'assets');return[];}},renderAssets:()=>{},style:()=>{},backgrounds:()=>{}};vm.createContext(u);vm.runInContext(uploadSource+';this.go=uploadAssets;',u);
  const files=[{name:'测试 A.png'},{name:'测试 B.mp4'}];
  if(failAt)await assert.rejects(u.go(input,status,files),/连接中断/);else await u.go(input,status,files);
  assert(!input.disabled);assert.equal(input.value,'');assert(meter.hidden);assert.deepEqual(seen,files.map(f=>f.name));assert(headers.some(([k,v])=>k==='X-File-Name'&&v===encodeURIComponent(files[0].name)));
  if(!failAt)assert(status.textContent.includes('手动选择'));
  await assert.rejects(u.go(input,status,[{name:'bad.apk'}]),/请选择/);
 }
 await upload(0);await upload(2);
 const colors=fs.readFileSync(base+'colors.js','utf8'),colorContext={Math,Number,Error,parseInt,document:{createElement:()=>({style:{},children:[],append(...nodes){this.children.push(...nodes);}})}};vm.createContext(colorContext);
 vm.runInContext(extract(colors,'function rgb(','function stored(')+extract(colors,'function button(','window.AuroraColors=')+';this.colorButton=button;',colorContext);
 const colorButton=colorContext.colorButton('#12345680',()=>{});assert.equal(colorButton.children[1].textContent,'#123456');assert(colorButton.title.includes('50%'));colorButton.update('#ffffff00');assert.equal(colorButton.children[1].textContent,'#FFFFFF');assert(colorButton.title.includes('0%'));
 const hexInput={},error={textContent:''};Object.assign(colorContext,{a:[],h:[],alpha:128,render:()=>{},overlay:{querySelector:selector=>selector==='.hex'?hexInput:error}});
 vm.runInContext(extract(colors,"overlay.querySelector('.hex').onchange=","overlay.querySelector('.alpha').oninput="),colorContext);
 hexInput.onchange({target:{value:'#ABCDEF'}});assert.equal(colorContext.alpha,128,'editing RGB must preserve independent alpha');assert.equal(error.textContent,'');hexInput.onchange({target:{value:'#ABCDEF80'}});assert(error.textContent.includes('六位'));
 const presentation=fs.readFileSync(base+'../core/presentation.js','utf8'),listenerLine=presentation.split('\n').find(line=>line.includes("window.addEventListener('message'"));
 function previewContext(search){let listener,calls=[];const parent={},c={URLSearchParams,consolePreviewAppearance:null,parent,location:{origin:'http://local',search},window:{addEventListener:(name,fn)=>listener=fn},apply:v=>calls.push(v)};vm.createContext(c);vm.runInContext(listenerLine,c);return {c,calls,send:(origin,source,data)=>listener({origin,source,data})};}
 const preview=previewContext('?preview=1'),draft={glassBlur:31};preview.send('http://other',preview.c.parent,{type:'aurora-preview',appearance:draft});assert.equal(preview.calls.length,0);preview.send('http://local',{}, {type:'aurora-preview',appearance:draft});assert.equal(preview.calls.length,0);preview.send('http://local',preview.c.parent,{type:'aurora-preview',appearance:draft});assert.equal(preview.c.consolePreviewAppearance,draft);
 const normal=previewContext('');normal.send('http://local',normal.c.parent,{type:'aurora-preview',appearance:draft});assert.equal(normal.c.consolePreviewAppearance,null,'normal desktop must never pin console preview data');
 console.log('PASS library selection/order / cancel-delete / sequential upload+failure recovery / manual apply / RGB-alpha separation / preview origin+source gates / preview-only draft pinning');
})().catch(e=>{console.error(e);process.exitCode=1;});
