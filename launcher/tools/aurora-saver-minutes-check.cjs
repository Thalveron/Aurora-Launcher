'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync(__dirname+'/../app/src/main/assets/aurora/console/settings.js','utf8');
const start=source.indexOf('  function field('),end=source.indexOf('  var formDrafts=',start);
const node=()=>({dataset:{},children:[],append(...children){this.children.push(...children);},setAttribute(){}});
const ctx={document:{createElement:node},A:null,sample(){},Number};
vm.createContext(ctx);vm.runInContext(source.slice(start,end)+';this.field=field;',ctx);
for(const original of [5,'5']){const value={saverMinutes:original},box=node();box.id='saver-fields';const select=ctx.field(box,value,'saverMinutes','分钟','select',{'3':'3分钟','5':'5分钟','8':'8分钟'});select.value='8';select.oninput();assert.strictEqual(value.saverMinutes,8);assert.equal(JSON.parse(JSON.stringify(value)).saverMinutes,8);}
const value={saverMode:'idle'},box=node();box.id='saver-fields';const mode=ctx.field(box,value,'saverMode','播放方式','select',{idle:'闲置',continuous:'持续'});mode.value='continuous';mode.oninput();assert.strictEqual(value.saverMode,'continuous');
console.log('PASS saver minute selections submit numbers, including legacy drafts; enum selects remain strings');
