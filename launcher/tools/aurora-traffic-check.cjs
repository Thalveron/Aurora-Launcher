'use strict';
const fs=require('fs'),vm=require('vm'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'../app/src/main/assets/aurora'),storage=new Map();let now=Date.parse('2026-10-29T04:00:00Z');
class Clock extends Date{constructor(...args){super(...(args.length?args:[now]));}static now(){return now;}}
const context={window:{},Date:Clock,URLSearchParams,crypto:require('crypto'),fetch:async url=>({json:async()=>JSON.parse(fs.readFileSync(path.join(root,url.slice(1))))}),localStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v)}};
vm.createContext(context);vm.runInContext(fs.readFileSync(path.join(root,'core/mock.js'),'utf8'),context);vm.runInContext(fs.readFileSync(path.join(root,'console/traffic-model.js'),'utf8'),context);
async function request(route,body){const envelope=await context.window.AuroraMock.response(route,body);assert(envelope.ok,JSON.stringify(envelope));return envelope.data;}
async function invalid(route,body){const result=await context.window.AuroraMock.response(route,body);assert.equal(result.ok,false);assert(result.error.code);}
function charts(){
 const M=context.window.AuroraTrafficModel,events={},paths=[],tooltip={hidden:true,style:{}},canvas={clientWidth:600,parentElement:{querySelector:()=>tooltip},addEventListener:(type,fn)=>events[type]=fn,getBoundingClientRect:()=>({left:0,width:600})};let points=[];
 const ctx=new Proxy({beginPath(){points=[];},moveTo(x,y){points.push(['move',x,y]);},lineTo(x,y){points.push(['line',x,y]);},stroke(){paths.push({color:ctx.strokeStyle,points:points.slice()});}},{get:(target,k)=>k in target?target[k]:()=>{}});canvas.getContext=()=>ctx;
 const scope={window:{AuroraTrafficModel:M,devicePixelRatio:1},document:{documentElement:{}},getComputedStyle:()=>({getPropertyValue:()=>''}),WeakMap};vm.createContext(scope);vm.runInContext(fs.readFileSync(path.join(root,'console/line-chart.js'),'utf8'),scope);
 const values=[{time:now-200000,rx:10,tx:5},{time:now-199000,rx:20,tx:8},{time:now,rx:30,tx:9}];scope.window.AuroraLineChart.render(canvas,values,90000);
 const rx=paths.find(v=>v.color==='#16aeb8');assert.equal(rx.points.filter(p=>p[0]==='move').length,2,'do not bridge a real sampling gap');assert.equal(rx.points.filter(p=>p[0]==='line').length,1);
 events.pointermove({clientX:300});assert.equal(tooltip.hidden,false);assert(tooltip.textContent.includes('下载'));events.pointerleave();assert.equal(tooltip.hidden,true);
 let prevented=false;events.keydown({key:'ArrowLeft',preventDefault(){prevented=true;}});assert(prevented);assert.equal(tooltip.hidden,false);events.keydown({key:'Escape',preventDefault(){}});assert.equal(tooltip.hidden,true);
 scope.window.AuroraLineChart.render(canvas,[],90000);assert.equal(tooltip.hidden,true);
}
(async()=>{
 const M=context.window.AuroraTrafficModel;
 assert.equal(M.bytes(null),'—');assert.equal(M.rate(undefined),'—');assert.equal(M.bytes(0),'0 B');assert.equal(M.rate(1024),'1.0 KB/s');assert.equal(M.bytes(-1),'—');
 const sample=[{uid:1,name:'甲',rxRate:0,txRate:0,packages:[{package:'org.example.alpha'}]},{uid:2,name:'乙',rxRate:2048,txRate:10,packages:[]},{uid:3,name:'丙',rxRate:null,txRate:null,packages:[]}];
 assert.deepEqual(Array.from(M.rows(sample,'alpha',false,'rxRate','desc'),a=>a.uid),[1]);assert.deepEqual(Array.from(M.rows(sample,'',true,'rxRate','desc'),a=>a.uid),[2]);assert.deepEqual(Array.from(M.rows(sample,'',false,'rxRate','asc'),a=>a.uid),[1,2,3],'unknown stays last in either direction');assert.deepEqual(Array.from(M.rows(sample,'',false,'rxRate','desc'),a=>a.uid),[2,1,3]);assert.equal(sample[0].uid,1,'sorting must not mutate source data');
 let state=await request('network/traffic');assert.equal(state.resolveDomains,false);assert.equal(state.apps.length,9);assert.equal(new Set(state.apps.map(a=>a.uid)).size,9);assert(state.series.length>=100);assert(state.series.every((p,i,a)=>!i||p.time-a[i-1].time===5000));assert(state.apps.some(a=>a.uploadHint));assert(state.apps.some(a=>a.sharedUid));assert(state.connections.some(c=>c.remote.includes(':')));assert(state.connections.every(c=>!c.reverseName));assert.equal(state.total.rxRate,state.apps.reduce((sum,a)=>sum+a.rxRate,0));
 const paused=state.sampleAt;now+=20000;assert.equal((await request('network/traffic')).sampleAt,paused,'paused snapshots do not keep moving');
 state=await request('network/traffic/config',{enabled:true});now+=20000;state=await request('network/traffic');assert(state.sampleAt>paused);assert(state.enabled);const uid=state.apps[0].uid;
 let history=await request('network/traffic/history?uid='+uid+'&hours=24');assert(history.points.length>0);assert.equal(history.uid,uid);assert(history.points.every(p=>p.seconds===history.bucketSeconds));assert(M.points(history.points,true).every(p=>M.valid(p.rx)));
 await request('network/traffic/config',{resolveDomains:true,retentionDays:1});state=await request('network/traffic');assert(state.connections.some(c=>c.reverseName));history=await request('network/traffic/history?uid=-1&hours=720');assert(history.points.every(p=>p.time>=now-86400000));
 await invalid('network/traffic/config',{intervalSeconds:0});await invalid('network/traffic/config',{resolveDomains:'true'});await invalid('network/traffic/clear',{confirm:false});await invalid('network/traffic/history?uid=9&hours=24');
 const totalBefore=state.total.rxBytes;await request('network/traffic/clear',{confirm:true});state=await request('network/traffic');assert.equal(state.hasSample,false);assert.equal(state.series.length,0);assert.equal((await request('network/traffic/history?uid='+uid+'&hours=24')).points.length,0);now+=20000;state=await request('network/traffic');assert(state.hasSample);assert(state.total.rxBytes>=totalBefore,'clear must not reset boot counters');assert(state.apps[0].sessionRx<100000000,'session totals restart after clearing');
 const exported=await request('network/traffic/export',{});assert.equal(exported.apps.length,9);assert(!('_pausedAt' in exported));assert(!('_clearedAt' in exported));charts();
 console.log('PASS units and unknown values / immutable sorting and search / 9-app wire data / continuous curves / pause and resume / history and retention / DNS off by default / confirmation and validation / clear preserves boot counters / chart gaps and keyboard-pointer tooltips');
})().catch(error=>{console.error(error);process.exitCode=1;});
