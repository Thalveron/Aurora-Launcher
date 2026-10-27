/* Same v1 wire envelopes and actions as Android. No native object access. */
'use strict';
(function(){
 var fixture,defaults,registry,routes;
 function clone(v){return JSON.parse(JSON.stringify(v));}
 function stored(k,fallback){try{return JSON.parse(localStorage.getItem(k))||clone(fallback);}catch(e){return clone(fallback);}}
 function save(k,v){localStorage.setItem(k,JSON.stringify(v));return clone(v);}
 function canonical(p){if(p==='appearance'||p.indexOf('appearance/')===0)return 'config/'+p;if(p==='custom-css')return 'themes/custom-css';if(p==='library')return 'assets';if(p.indexOf('library/')===0)return 'assets/'+p.slice(8);if(p.indexOf('apps/icon/')===0)return 'icons/'+p.slice(10);return p;}
 var ready=Promise.all([fetch('/core/mock-data.json').then(function(r){return r.json();}),fetch('/core/appearance-defaults.json').then(function(r){return r.json();}),fetch('/core/contract.json').then(function(r){return r.json();})]).then(function(v){fixture=v[0];defaults=v[1];registry=v[2].actions;routes=v[2].routes;});
 function fail(code,message){var e=Error(message);e.code=code;throw e;}
 function only(p,keys){if(!p||typeof p!=='object'||Array.isArray(p))fail('INVALID_ARGUMENT','Object required');Object.keys(p).forEach(function(k){if(keys.indexOf(k)<0)fail('INVALID_ARGUMENT','Unknown parameter: '+k);});}
 async function execute(action,params){var spec=registry.filter(function(v){return v.action===action;})[0];if(!spec)fail('INVALID_ARGUMENT','Unknown action');only(params,Object.keys(spec.parameters));Object.keys(spec.parameters).forEach(function(k){var value=params[k],t=spec.parameters[k];if(t==='integer'?!Number.isInteger(value):t==='object'?(!value||typeof value!=='object'||Array.isArray(value)):typeof value!==t)fail('INVALID_ARGUMENT','Invalid parameter: '+k);});
  if(action==='open_settings'){if(['wifi','bluetooth','display'].indexOf(params.kind)<0)fail('INVALID_ARGUMENT','Unknown settings');return {opened:params.kind};}
  if(action==='launch_app'){if(!fixture.apps.some(function(v){return v.package===params.package;}))fail('INVALID_ARGUMENT','Application not installed');return {};}
  if(action==='switch_theme'){if(!(await data("themes")).some(function(v){return v.id===params.theme&&v.available;}))fail('NOT_FOUND','Theme not installed');var c=await data('config');c.theme=params.theme;return save('aurora-mock-config',c);}
  if(action==='set_appearance')return data('config/appearance',params.patch);
  if(action.indexOf('media_')===0)return data('media/'+action.slice(6),params);
  if(action==='enter_saver'||action==='enter_saver_auto')return data('saver/enter',{});
  if(action==='exit_saver')return data('saver/exit',{});
 }
 async function data(path,body){path=canonical(path);
 if(path.indexOf('network/traffic')===0){
  var monitor=stored('aurora-network-monitor',{enabled:false,intervalSeconds:5,historyEnabled:true});
  if(path==='network/traffic/config'){only(body,['enabled','intervalSeconds','historyEnabled','retentionDays','resolveDomains']);Object.keys(body).forEach(function(k){monitor[k]=body[k];});save('aurora-network-monitor',monitor);}
  if(path.indexOf('network/traffic/history')===0){var query=new URLSearchParams(path.split('?')[1]||'');return {uid:Number(query.get('uid')||-1),hours:Number(query.get('hours')||24),bucketSeconds:300,points:[],note:'电脑预览模拟数据，不采集真实流量'};}
  if(path==='network/traffic/clear'){only(body,['confirm']);if(body.confirm!==true)fail('INVALID_ARGUMENT','Confirmation required');return {clearing:true};}
  return Object.assign(monitor,{available:true,error:'',sampleAgeMs:-1,hasSample:false,retentionDays:monitor.retentionDays||7,resolveDomains:!!monitor.resolveDomains,lastAutoCleanupAt:0,failures:0,apps:[],connections:[],interfaces:[],series:[],source:'mock'});
 }

 if(path==='inputs/shortcut'){var shortcut=stored('aurora-input-shortcut',{id:'EN_TCL_HDMI1',remoteHint:true});if(body){only(body,['id','remoteHint']);if(body.id&&!/^EN_TCL_(HDMI[1-4]|AV[12])$/.test(body.id))fail('INVALID_ARGUMENT','Invalid input');if(body.remoteHint!==undefined&&typeof body.remoteHint!=='boolean')fail('INVALID_ARGUMENT','Invalid remoteHint');shortcut=save('aurora-input-shortcut',Object.assign(shortcut,body));}return shortcut;}
 if(path==='system/idle-standby'){var idle=stored('aurora-idle-standby',{enabled:false});if(body){only(body,['enabled']);if(typeof body.enabled!=='boolean')fail('INVALID_ARGUMENT','enabled must be boolean');idle=save('aurora-idle-standby',{enabled:body.enabled});}return {enabled:idle.enabled,minutes:15,available:true,idleSeconds:0,remainingSeconds:900,reason:idle.enabled?'计时中':'已关闭',wake:'power_key',scope:'desktop_screensaver',paused:false};}
  if(path==='actions')return clone(registry);
  if(path==='themes/switch')return execute('switch_theme',{theme:body.theme});
  if(path==='config'){var cfg=stored('aurora-mock-config',fixture.config);if(body){only(body,['theme','locale','consoleLocale','icon_fallback','system_backends']);if((body.locale&&['zh-CN','en-US'].indexOf(body.locale)<0)||(body.consoleLocale&&['zh-CN','en-US'].indexOf(body.consoleLocale)<0))fail('INVALID_ARGUMENT','Invalid locale');if(body.theme)await execute('switch_theme',{theme:body.theme});cfg=Object.assign(cfg,body);save('aurora-mock-config',cfg);}return cfg;}
  if(path==='config/appearance'){var theme=(await data('config')).theme,description=(await data('themes')).filter(function(t){return t.id===theme;})[0],seed=clone(defaults);if(description&&description.defaultLayout)seed.layout=clone(description.defaultLayout);var app=stored('aurora-mock-style-'+theme,seed);Object.assign(app,stored('aurora-mock-shared-saver',{}));if(body){only(body,Object.keys(defaults));Object.keys(body).forEach(function(k){var v=body[k],ranges={glassBlur:[0,40],cardOpacity:[.05,.6],backgroundDim:[0,.8],fontScale:[.6,1.8],greetingSize:[20,100],fontSize:[16,64],focusWidth:[1,12],focusRadius:[0,64],focusGlow:[0,100],motionMs:[0,1000]};if(ranges[k]&&(typeof v!=='number'||!isFinite(v)||v<ranges[k][0]||v>ranges[k][1]))fail('INVALID_ARGUMENT','Out of range: '+k);if(/^(text|accent|card|lyricPast|lyricActive|lyricFuture|focusColor|ambientColor)$/.test(k)&&!/^#[0-9a-f]{6}([0-9a-f]{2})?$/i.test(v))fail('INVALID_ARGUMENT','Invalid color');});var shared=stored('aurora-mock-shared-saver',{});Object.keys(body).forEach(function(k){if(/^(saver|greeting)/.test(k))shared[k]=body[k];});save('aurora-mock-shared-saver',shared);app=save('aurora-mock-style-'+theme,Object.assign(app,body,shared));}return app;}
  if(path==='config/appearance/preview')return Object.assign(await data('config/appearance'),body);
  if(path==='config/appearance/presets')return {presets:body?save('aurora-mock-presets-'+(await data('config')).theme,body.presets):stored('aurora-mock-presets-'+(await data('config')).theme,[])};
  if(path==='themes')return clone(fixture.themes).concat(stored('aurora-mock-custom-themes',[]));
  if(path==='themes/custom'){only(body,['name','base','css']);var base=(await data('themes')).find(function(t){return t.id===body.base&&t.available&&!t.custom&&t.id!=='legacy';});if(!base||!body.name||body.name.length>40)fail('INVALID_ARGUMENT','Invalid template or name');var item=Object.assign({},base,{id:'user_'+crypto.randomUUID().replace(/-/g,''),name:body.name,base:body.base,custom:true,readOnly:false});var items=stored('aurora-mock-custom-themes',[]);items.push(item);save('aurora-mock-custom-themes',items);await data('themes/css/'+item.id,{css:body.css||''});return item;}
  if(path==='themes/custom/delete'){only(body,['id','confirm']);if(!body.confirm||(await data('config')).theme===body.id)fail('INVALID_ARGUMENT','Switch theme and confirm first');save('aurora-mock-custom-themes',stored('aurora-mock-custom-themes',[]).filter(function(t){return t.id!==body.id;}));localStorage.removeItem('aurora-mock-css-'+body.id);return {};}
  if(path==='themes/custom-css')return data('themes/css/'+(await data('config')).theme,body);
  if(path.indexOf('themes/css/')===0){var id=path.slice(11);if(!(await data('themes')).some(function(t){return t.id===id;}))fail('INVALID_ARGUMENT','Unknown theme');if(body){only(body,['css']);if(typeof body.css!=='string'||body.css.length>60000||/@import|expression\s*\(|javascript:|<\/style/i.test(body.css))fail('INVALID_ARGUMENT','Invalid CSS');localStorage.setItem('aurora-mock-css-'+id,body.css);}return {theme:id,css:localStorage.getItem('aurora-mock-css-'+id)||'',applicable:id!=='legacy'};}
  if(path==='bootstrap'){var state={apiVersion:1,stage:2,legacyConsole:'/console/'};['weather','media','apps','status','themes'].forEach(function(k){state[k]=clone(fixture[k]);});state.themes=await data('themes');state.time=await data('time');state.config=await data('config');state.appearance=await data('config/appearance');return state;}
  if(path==='time'){var now=new Date();return {epochMs:+now,time:now.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false,timeZone:'Asia/Shanghai'}),date:now.toLocaleDateString('sv-SE',{timeZone:'Asia/Shanghai'}),weekday:now.toLocaleDateString('zh-CN',{weekday:'long',timeZone:'Asia/Shanghai'}),lunar:'农历九月十五',festival:'',timezone:'Asia/Shanghai'};}
  if(path==='voice'){if(body)fixture.voice.config=Object.assign(fixture.voice.config,body);return clone(fixture.voice);}
  if(path==='voice/test'||path==='voice/speak')return {accepted:false,reason:'Preview does not speak on the TV'};
  if(path==='voice/stop')return {stopped:true};
  if(path==='backgrounds')return fixture.assets.filter(function(v){return v.kind==='background';});
  if(path==='themes/upload'||path==='ai/chat'||path==='icons')fail('NOT_IMPLEMENTED','Reserved for a later stage');
  if(path==='media/queue')return [];
  if(path.indexOf('media/')===0){var op=path.slice(6);if(['play','pause','prev','next','seek','mode'].indexOf(op)<0)fail('NOT_FOUND','Unknown route');if(op==='play'||op==='pause')fixture.media.playing=op==='play';if(op==='seek'){if(!body||body.positionMs<0||body.positionMs>fixture.media.durationMs)fail('INVALID_ARGUMENT','Invalid seek');fixture.media.positionMs=body.positionMs;}if(op==='mode'){if(!body||!Number.isInteger(body.mode)||body.mode<0||body.mode>2)fail('INVALID_ARGUMENT','Invalid mode');fixture.media.mode=body.mode;}return clone(fixture.media);}
  if(path==='saver/enter'||path==='saver/exit'){fixture.saver.active=path==='saver/enter';return clone(fixture.saver);}
  if(path==='apps/launch')return execute('launch_app',body);
  if(path==='bgm')return {volume:12,volumeMax:100,systemVolume:20,desktopVolume:-1,volumeMode:'tv_index',enabled:false,autoplay:false,fadeMs:1500,reason:'stopped',selected:'',lyrics:''};if(path==='bgm/volume')return Object.assign(await data('bgm'),{volume:body.volume});if(path==='bgm/library'||path==='apps/jobs')return [];
  if(path.indexOf('services/cities?')===0)return [{name:'深圳',region:'广东省',country:'中国',lat:22.54554,lon:114.0683}];
  if(path==='services'){if(body){var value=clone(body),built=clone(fixture.services.config);if(!value.weather.urls.length)['provider','urls','headers','mapping','key'].forEach(function(k){value.weather[k]=built.weather[k];});['ntp','http'].forEach(function(k){if(!value.time[k].length)value.time[k]=built.time[k];});fixture.services.config=value;}return clone(fixture.services);}
  if(path==='music/local/settings')return clone(fixture.localMusic);
  if(Object.prototype.hasOwnProperty.call(fixture,path)&&body===undefined)return clone(fixture[path]);
  var known=routes.some(function(r){var pattern=r.path.replace(/[.*+?^$()|[\]\\]/g,'\\$&').replace(/\{[^}]+\}/g,'[^/]+');return new RegExp('^'+pattern+'$').test(path);});fail(known?'UNSUPPORTED':'NOT_FOUND',known?'This operation is unavailable in computer preview':'Unknown API route');
 }
 async function response(path,body){try{await ready;if(path==='actions'&&body!==undefined){only(body,['action','params']);return {ok:true,data:await execute(body.action,body.params)};}return {ok:true,data:await data(path,body)};}catch(e){return {ok:false,error:{code:e.code||'INVALID_ARGUMENT',message:e.message}};}}
 window.AuroraMock={response:response};
}());
