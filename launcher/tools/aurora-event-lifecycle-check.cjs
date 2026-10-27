const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/aurora/core/api.js'),'utf8');
function setup(mock=true){
 const intervals=new Map(),events={},sockets=[];let count=0,id=0,resolveFirst;
 const first=new Promise(resolve=>resolveFirst=resolve);
 const context={URLSearchParams,Element:function(){},location:{search:mock?'?mock=1':'',host:'tv:8899',pathname:'/themes/cards/index.html'},document:{hidden:false,addEventListener(n,f){events[n]=f;}},setInterval(f){intervals.set(++id,f);return id;},clearInterval(n){intervals.delete(n);},setTimeout,clearTimeout};
 context.window={AuroraMock:{response(){count++;return count===1?first:Promise.resolve({ok:true,data:{theme:'cards'}});}},addEventListener(n,f){events[n]=f;}};
 context.Element.prototype.replaceChildren=function(){};
 context.WebSocket=function(){sockets.push(this);this.close=()=>{if(this.onclose)this.onclose();};};
 vm.runInNewContext(source,context);
 const received=[];context.window.AuroraAPI.events(v=>received.push(v));
 return {context,events,intervals,sockets,received,count:()=>count,resolve:()=>resolveFirst({ok:true,data:{}})};
}
(async()=>{
 let s=setup(),tick=[...s.intervals.values()][0],pending=tick();await tick();assert.equal(s.count(),1,'slow fallback must not overlap');
 s.resolve();await pending;assert.equal(s.count(),6);assert.equal(s.received.length,1);
 s=setup();tick=[...s.intervals.values()][0];pending=tick();s.events.pagehide();s.resolve();await pending;
 assert.equal(s.count(),1,'hidden page must not keep requesting the rest of an obsolete snapshot');assert.equal(s.received.length,0);assert.equal(s.intervals.size,0);
 s=setup(false);const old=s.sockets[0],close=old.onclose;s.events['aurora-hidden']();close();old.onerror();assert.equal(s.intervals.size,0);
 console.log('PASS single-flight fallback, obsolete response suppression and closed WebSocket lifecycle');
})().catch(e=>{console.error(e);process.exitCode=1;});
