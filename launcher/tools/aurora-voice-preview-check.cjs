'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync(__dirname+'/../app/src/main/assets/aurora/console/settings.js','utf8');
const start=source.indexOf("  ['english','chinese'].forEach"),end=source.indexOf("  el('voice-stop')",start);
const buttons={},calls=[],voice={config:{volume:2.5,rate:1.2,pitch:.9,enabled:false}};
vm.runInNewContext(source.slice(start,end),{el:id=>buttons[id]||(buttons[id]={}),run:fn=>fn,voice,api:{request:async(path,body)=>{calls.push({path,body});return{accepted:true};}}});
(async()=>{for(const language of ['english','chinese'])await buttons['voice-'+language].onclick();assert.equal(calls.length,2);for(const call of calls){assert.equal(call.path,'voice/test');assert.equal(call.body.parameters.volume,2.5);assert.equal(call.body.parameters.rate,1.2);assert.equal(call.body.parameters.pitch,.9);}assert.equal(calls[0].body.parameters.language,'en-US');assert.equal(calls[1].body.parameters.language,'zh-CN');assert.equal(voice.config.enabled,false);console.log('PASS audition uses unsaved voice parameters without persisting or restoring settings');})().catch(e=>{console.error(e);process.exitCode=1;});
