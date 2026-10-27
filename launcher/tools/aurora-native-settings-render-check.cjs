'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const assets=path.resolve(__dirname,'../app/src/main/assets/web');
const html=fs.readFileSync(path.join(assets,'index.html'),'utf8'),source=fs.readFileSync(path.join(assets,'app.js'),'utf8');
class Element{
 constructor(tag){this.tagName=tag;this.children=[];this.style={};}
 append(...nodes){for(const n of nodes){this.children.push(n);if(n&&typeof n==='object')n.parentElement=this;}}
 replaceChildren(...nodes){this.children=[];this.append(...nodes);}
 setAttribute(k,v){this[k]=v;}
}
const nodes=new Map([...html.matchAll(/id="([^"]+)"/g)].map(m=>[m[1],new Element('div')]));
const fields=JSON.parse(fs.readFileSync(0,'utf8'));
const state={fields:[{id:'future:unsupported',section:'future-section',type:'text',title:'Future field',value:''},...fields]};
const context={document:{createElement:tag=>new Element(tag)},state,$:id=>nodes.get(id)||null,openColor(){},saveField(){},TextDecoder};
vm.createContext(context);
const start=source.indexOf('function renderFields(){'),end=source.indexOf('async function saveField(',start);
vm.runInContext(source.slice(start,end),context);
for(let round=0;round<2;round++){
 context.renderFields();
 for(const section of ['layout','appearance','music','ambient','system','advanced']){
  const expected=fields.filter(f=>f.section===section).length;
  assert(expected>0,'fixture must exercise '+section);
  assert.equal(nodes.get('fields-'+section).children.length,expected,section+' rendered all controls');
  console.log('PASS complete settings section',section,expected);
 }
 const descendants=[];function visit(n){descendants.push(n);for(const c of n.children||[])if(c&&typeof c==='object')visit(c);}
 visit(nodes.get('fields-music'));
 const styles=descendants.find(n=>n.id==='field-appearance:card_style');
 assert(styles&&styles.children.length===5&&typeof styles.onchange==='function','all five native spectrum choices and save handler');
 assert(descendants.some(n=>n.id==='field-launcher:music_card_position'),'existing position setting remains');
}
console.log('PASS unknown/compat groups cannot abort rendering, repeated rendering has no duplicates');
