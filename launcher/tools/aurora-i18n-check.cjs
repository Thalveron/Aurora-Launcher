'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const root=path.join(__dirname,'../app/src/main/assets/aurora');
const dictionary=JSON.parse(fs.readFileSync(root+'/core/strings-en.json','utf8'));
const callbacks={},nodes=[],attrs=[],storage={};
let title='Aurora Launcher',walk;
function text(value,protectedContent){const n={nodeValue:value,parentElement:{closest:()=>!!protectedContent}};nodes.push(n);return n;}
const menu=text('当前主题设置'),dynamic=text('正在检查 GitHub 更新'),song=text('音乐卡片',true);
const attribute={closest:()=>false,hasAttribute:k=>k==='placeholder',getAttribute:()=>title,setAttribute:(k,v)=>{title=v;}};attrs.push(attribute);
title='搜索名称、包名或 UID';
const document={title:'桌面更新',body:{},documentElement:{},createTreeWalker:()=>{walk=0;return {nextNode:()=>nodes[walk++]||null};},querySelectorAll:()=>attrs,addEventListener:(event,fn)=>{callbacks[event]=fn;}};
const context={document,location:{pathname:'/console/app.html'},localStorage:{getItem:k=>storage[k],setItem:(k,v)=>{storage[k]=v;}},NodeFilter:{SHOW_TEXT:4},MutationObserver:class{observe(){}disconnect(){}},fetch:async()=>({ok:true,json:async()=>dictionary}),console,setInterval:()=>0,requestAnimationFrame:fn=>fn()};
context.window={confirm:()=>true,prompt:()=>'',addEventListener:()=>{}};
vm.createContext(context);vm.runInContext(fs.readFileSync(root+'/core/i18n.js','utf8'),context);
(async()=>{
 await new Promise(resolve=>setImmediate(resolve));callbacks.DOMContentLoaded();
 const i18n=context.window.AuroraI18n;i18n.set('en-US');
 assert.equal(menu.nodeValue,'Current theme settings');assert.equal(dynamic.nodeValue,'Checking GitHub for updates');
 assert.equal(song.nodeValue,'音乐卡片','user music titles must remain unchanged');
 assert.equal(title,'Search by name, package or UID');
 assert.equal(i18n.t('U 盘歌曲'),'USB music');
 assert.equal(i18n.t('全局设置 · 所有主题共用'),'Global settings · Shared by all themes');
 assert.equal(i18n.t('与桌面显示语言独立设置'),'Independent of the desktop display language');
 assert.equal(i18n.t('仅修改电视桌面的显示语言'),'Changes the TV desktop display language only');
 assert.equal(i18n.t('☀ 天气'),'☀ Weather','unit suffixes must not corrupt weather labels');
 assert.equal(i18n.t('保留 7 天'),'保留 7 days');
 dynamic.nodeValue='发现新版本 v1.4.5';i18n.set('en-US');assert.equal(dynamic.nodeValue,'New version available: v1.4.5');
 assert.equal(i18n.t('下载完成：https://example.org/app.apk'),'Download complete: https://example.org/app.apk');
 const labels=['外观','内容','设备','系统','主题管理','当前主题设置','背景库','屏保','天气与时间','U 盘歌曲','语言与朗读','应用管理','连接与输入源','遥控与设备','桌面预览','应用流量监控','桌面更新','安全设置','操作日志','音乐频谱','歌曲上传与音量','屏保问候语','屏保音乐','语音参数','高级维护','尚未检查更新','正在检查维护权限','可独立安装、保留设置及启动失败回退','当前已是最新版本','上一版本','暂无','自动安装与回退可用'];
 for(const label of labels)assert(!/[\u4e00-\u9fff]/.test(i18n.t(label)),'Missing translation: '+label);
 i18n.set('zh-CN');assert.equal(menu.nodeValue,'当前主题设置');assert.equal(dynamic.nodeValue,'发现新版本 v1.4.5');assert.equal(title,'搜索名称、包名或 UID');
 for(let i=0;i<10;i++){i18n.set('en-US');assert.equal(menu.nodeValue,'Current theme settings');i18n.set('zh-CN');assert.equal(menu.nodeValue,'当前主题设置');}
 assert.equal(i18n.t('用户填写的自定义名称'),'用户填写的自定义名称');
 console.log('Passed: menu coverage, dynamic messages, protected data, attributes and repeated language switching');
})().catch(error=>{console.error(error);process.exitCode=1;});
