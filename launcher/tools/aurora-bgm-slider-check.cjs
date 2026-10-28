const fs=require('fs'),vm=require('vm'),assert=require('assert');
const nodes={},pending=[],calls=[];let saved=12;
function node(id){return nodes[id]||(nodes[id]={value:'',checked:false,textContent:'',append(){},replaceChildren(){}});}
const state=()=>({volume:saved,volumeMax:100,enabled:true,autoplay:false,fadeMs:1500,reason:'playing',selected:''});
const api={request:async(path,body)=>{if(path==='bgm/library')return [];if(path==='bgm')return state();if(path==='bgm/volume'){calls.push(body.volume);return new Promise(resolve=>pending.push(()=>{saved=body.volume;resolve(state());}));}return state();}};
const sandbox={window:{AuroraAPI:api,addEventListener(){}},document:{hidden:false,getElementById:node,createElement:()=>({})},setInterval:()=>0,clearInterval(){},setTimeout:()=>0,clearTimeout(){},fetch(){}};
vm.createContext(sandbox);vm.runInContext(fs.readFileSync(__dirname+'/../app/src/main/assets/aurora/console/bgm.js','utf8'),sandbox);
(async()=>{for(let i=0;i<8;i++)await Promise.resolve();
for(const level of [2,6,0]){node('bgm-volume').value=String(level);node('bgm-volume').onchange();}
assert.deepEqual(calls,[2]);pending.shift()();for(let i=0;i<8;i++)await Promise.resolve();
assert.deepEqual(calls,[2,0]);assert.equal(node('bgm-volume').value,'0');pending.shift()();for(let i=0;i<8;i++)await Promise.resolve();
assert.equal(saved,0);assert.equal(node('bgm-volume').value,'0');
assert.equal(node('bgm-volume-value').textContent,'0%');assert.equal(String(node('bgm-volume-number').value),'0');
for(const bad of ['-1','101','','oops']){node('bgm-volume-number').value=bad;node('bgm-volume-number').onchange();assert.equal(calls.length,2);}
node('bgm-volume-number').value='53';node('bgm-volume-number').onchange();assert.equal(calls[2],53);pending.shift()();for(let i=0;i<8;i++)await Promise.resolve();assert.equal(saved,53);assert.equal(node('bgm-volume-value').textContent,'53%');
console.log('PASS numeric percentage validation, percentage units and rapid slider changes serialize to latest target; stale response does not move slider back');
})().catch(e=>{console.error(e);process.exitCode=1;});
