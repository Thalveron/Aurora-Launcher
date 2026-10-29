'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const root=path.join(__dirname,'../../');
const inventory=JSON.parse(fs.readFileSync(path.join(root,'docs/console-feature-map.json'),'utf8'));
const nodes=new Map();
class Node{
 constructor(tag){this.tagName=tag;this.children=[];this.hidden=false;this.value='';}
 set id(value){this._id=value;nodes.set(value,this);}get id(){return this._id;}
 append(...items){this.children.push(...items);}
 replaceChildren(...items){this.children=items;}
 removeAttribute(){} checkValidity(){return true;}
}
for(const id of ['native-category','native-settings-anchor','native-category-title']){const n=new Node('div');n.id=id;}
const fields=inventory.fields.map(f=>({id:f.id,title:f.name,section:f.section,type:f.id==='launcher:columns'?'number':'boolean',value:f.id==='launcher:columns'?5:true,min:0,max:100}));
let draft, writes=[];
const UI={row:(title,desc,control)=>control,card:(title,desc,rows)=>({title,rows}),draft:(id,options)=>{draft={baseline:options.initial,value:options.initial,patch(p){this.value=Object.assign({},this.value,p);options.render(this.value);},options};return draft;}};
const api={request:async(p,b)=>{if(b){writes.push([p,b]);return {fields};}return p==='config'?{theme:'legacy'}:{fields};}};
const context={window:{AuroraAPI:api,AuroraConsoleUI:UI},document:{createElement:t=>new Node(t),getElementById:id=>nodes.get(id),querySelectorAll:()=>[]}};
vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/aurora/console/native-settings.js'),'utf8'),context);
(async()=>{
 await context.window.AuroraNativeSettings.show('layout');
 const expected=fields.filter(f=>['appearance','layout','music','advanced','system'].includes(f.section)&&!f.id.startsWith('location:'));
 for(const f of expected)assert(nodes.has('native-field-'+f.id),f.id+' has a control');
 assert.equal(nodes.get('native-field-launcher:columns').value,5);
 nodes.get('native-field-launcher:columns').value='6';nodes.get('native-field-launcher:columns').onchange();
 await draft.options.save(draft.value);assert.equal(writes[0][0],'config/native');assert.equal(writes[0][1].changes['launcher:columns'],6);
 assert.equal(Object.keys(writes[0][1].changes).length,1,'only changed settings are written');
 assert.equal(writes.length,1);
 console.log('PASS native settings schema coverage, typed values and changed-only writes');
})().catch(e=>{console.error(e);process.exitCode=1;});
