/* Same v1 wire envelopes and actions as Android. No native object access. */
'use strict';
(function(){
 var fixture,defaults,registry,routes,nativeFixture;
 function clone(v){return JSON.parse(JSON.stringify(v));}
 function stored(k,fallback){try{return JSON.parse(localStorage.getItem(k))||clone(fallback);}catch(e){return clone(fallback);}}
 function save(k,v){localStorage.setItem(k,JSON.stringify(v));return clone(v);}
 function canonical(p){if(p==='appearance'||p.indexOf('appearance/')===0)return 'config/'+p;if(p==='custom-css')return 'themes/custom-css';if(p==='library')return 'assets';if(p.indexOf('library/')===0)return 'assets/'+p.slice(8);if(p.indexOf('apps/icon/')===0)return 'icons/'+p.slice(10);return p;}
 var ready=Promise.all([fetch('/core/mock-data.json').then(function(r){return r.json();}),fetch('/core/appearance-defaults.json').then(function(r){return r.json();}),fetch('/core/contract.json').then(function(r){return r.json();}),fetch('/core/mock-native-settings.json').then(function(r){return r.json();})]).then(function(v){fixture=v[0];defaults=v[1];registry=v[2].actions;routes=v[2].routes;nativeFixture=v[3];});
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
 function traffic(path,body){
  var now=Date.now(),monitor=stored('aurora-network-monitor',{enabled:false,intervalSeconds:5,historyEnabled:true,retentionDays:7,resolveDomains:false}),key='aurora-network-monitor';
  if(!monitor._pausedAt&&!monitor.enabled){monitor._pausedAt=now;save(key,monitor);}
  if(path==='network/traffic/config'){
   only(body,['enabled','intervalSeconds','historyEnabled','retentionDays','resolveDomains']);
   ['enabled','historyEnabled','resolveDomains'].forEach(function(k){if(body[k]!==undefined&&typeof body[k]!=='boolean')fail('INVALID_ARGUMENT','开关参数必须是布尔值');});
   if(body.intervalSeconds!==undefined&&[2,5,10,30].indexOf(body.intervalSeconds)<0)fail('INVALID_ARGUMENT','采样间隔无效');
   if(body.retentionDays!==undefined&&[1,3,7,30].indexOf(body.retentionDays)<0)fail('INVALID_ARGUMENT','保留天数无效');
   if(body.enabled!==undefined&&body.enabled!==monitor.enabled){if(body.enabled){monitor._resumedAt=now;delete monitor._pausedAt;}else monitor._pausedAt=now;}
   if(body.historyEnabled===false)monitor._historyStoppedAt=now;
   if(body.historyEnabled===true)delete monitor._historyStoppedAt;
   Object.keys(body).forEach(function(k){monitor[k]=body[k];});save(key,monitor);
  }else if(path==='network/traffic/clear'){
   only(body,['confirm']);if(body.confirm!==true)fail('INVALID_ARGUMENT','请先确认清空历史');
   monitor._clearedAt=now;monitor._resumedAt=monitor.enabled?now:0;monitor._pausedAt=monitor.enabled?0:now;save(key,monitor);return {clearing:true};
  }else if(path==='network/traffic/export'){only(body,[]);}
  else if(path!=='network/traffic'&&path.indexOf('network/traffic/history?')!==0)fail('NOT_FOUND','监控接口不存在');
  var sampled=Math.floor((monitor.enabled?now:monitor._pausedAt||now)/((monitor.intervalSeconds||5)*1000))*((monitor.intervalSeconds||5)*1000);
  var hasSample=!monitor._clearedAt||(monitor._resumedAt&&sampled-monitor._resumedAt>=(monitor.intervalSeconds||5)*2000);
  var definitions=[
   ['Aurora TV','org.example.aurora.tv',10084,2900000,42000,'#6879cc'],
   ['Moonlight','org.example.moonlight',10097,4600000,480000,'#437ca9'],
   ['Aurora 音乐','org.example.aurora.music',10073,96000,1300,'#9e6ec0'],
   ['系统服务','android',1000,4400,3000,'#677b89'],
   ['搜狗输入法','org.example.keyboard',10042,900,3100,'#d28a40'],
   ['媒体中心','org.example.media',10055,150000,140000,'#3b9290'],
   ['当贝市场','org.example.market',10063,0,0,'#4b88ca'],
   ['YouTube','org.example.video',10102,2100000,25000,'#b96b75'],
   ['Aurora Launcher','org.example.launcher',10081,4200,60000,'#4b9b80']
  ];
  function sampleRates(t,index){var d=definitions[index],phase=t/1000;return {rx:Math.round(d[3]*(.83+.2*Math.sin(phase/19+index)+.12*Math.cos(phase/8+index))),tx:Math.round(d[4]*(.92+.18*Math.sin(phase/27+index)+.08*Math.cos(phase/11+index)))};}
  function sumRates(t,index){if(index>=0)return sampleRates(t,index);var result={rx:0,tx:0};definitions.forEach(function(d,i){var v=sampleRates(t,i);result.rx+=v.rx;result.tx+=v.tx;});return result;}
  var series=[];if(hasSample){var start=monitor._clearedAt?Math.max(monitor._clearedAt,sampled-600000):sampled-600000;for(var t=Math.ceil(start/5000)*5000;t<=sampled;t+=5000){var r=sumRates(t,-1);series.push({time:t,rxRate:r.rx,txRate:r.tx});}}
  var apps=[],connections=[];
  definitions.forEach(function(d,i){
   var r=sampleRates(sampled,i),elapsed=monitor._clearedAt?Math.max(0,(sampled-monitor._clearedAt)/1000):2520,boot=Math.floor(sampled/86400000)*86400000,bootElapsed=(sampled-boot)/1000;
   var icon='<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64"><rect width="64" height="64" rx="14" fill="'+d[5]+'"/><text x="32" y="43" text-anchor="middle" fill="white" font-family="sans-serif" font-size="34">'+['▶','☾','♫','⚙','S','▣','▦','▶','A'][i]+'</text></svg>';
   var packages=[{name:d[0],package:d[1],system:i===3,enabled:true}];if(i===3)packages.push({name:'网络连接服务',package:'org.example.system.network',system:true,enabled:true});
   apps.push({uid:d[2],name:d[0],icon:'data:image/svg+xml;charset=utf-8,'+encodeURIComponent(icon),packages:packages,sharedUid:i===3,rxBytes:Math.round(536870912*(i+1)+bootElapsed*d[3]*.3),txBytes:Math.round(104857600*(i+1)+bootElapsed*d[4]*.3),rxRate:r.rx,txRate:r.tx,sessionRx:Math.round(elapsed*d[3]),sessionTx:Math.round(elapsed*d[4]),peakRx:Math.round(d[3]*1.3),peakTx:Math.round(d[4]*1.25),lastActiveAt:d[3]?sampled:sampled-1800000,sustainedUploadSeconds:i===5?246:0,connections:i===6?0:i===1?3:i===5?3:2,publicConnections:i===1||i===6||i===8?0:i===5?3:2,publicIpCount:i===1||i===6||i===8?0:i===5?3:2,uploadHint:i===5?'持续上传超过一分钟，请结合应用用途核对':''});
   if(i===6)return;for(var j=0;j<(i===1||i===5?3:2);j++){var scope=i===1||i===8?'lan':'public',remote=scope==='lan'?'10.12.0.'+(20+j):j===2?'2001:db8::'+(i+10):'203.0.113.'+(i*4+j+10);connections.push({uid:d[2],protocol:j===1?'udp':'tcp',state:j===1?'UNCONN':'ESTABLISHED',local:'192.0.2.2',localPort:35000+i*50+j,remote:remote,remotePort:j===1?443:scope==='lan'?47989:443,scope:scope,reverseName:monitor.resolveDomains&&scope==='public'?'cdn-'+i+'.example':'',firstSeenAt:sampled-(180+i*30)*1000,observedSeconds:180+i*30,txQueueBytes:j?0:1024*(i+1),rxQueueBytes:0});}
  });
  if(path.indexOf('network/traffic/history?')===0){
   var query=new URLSearchParams(path.split('?')[1]),uid=Number(query.get('uid')),hours=Number(query.get('hours')),index=definitions.findIndex(function(d){return d[2]===uid;});if([1,24,168,720].indexOf(hours)<0||(uid!==-1&&index<0))fail('INVALID_ARGUMENT','历史范围或应用无效');
   var bucket=hours===1?60:hours===24?300:1800,end=Math.floor(Math.min(sampled,monitor._historyStoppedAt||sampled)/(bucket*1000))*(bucket*1000),points=[];
   if(hasSample)for(var n=Math.min(144,hours*3600/bucket)-1;n>=0;n--){var at=end-n*bucket*1000;if((monitor._clearedAt&&at<monitor._clearedAt)||at<now-(monitor.retentionDays||7)*86400000)continue;var r=sumRates(at,index);points.push({time:at,rxBytes:r.rx*bucket,txBytes:r.tx*bucket,seconds:bucket});}
   return {uid:uid,hours:hours,bucketSeconds:bucket,points:points,note:'模拟数据，仅用于预览排版。'};
  }
  var total={rxBytes:0,txBytes:0,rxRate:0,txRate:0};apps.forEach(function(a){total.rxBytes+=a.rxBytes;total.txBytes+=a.txBytes;total.rxRate+=a.rxRate;total.txRate+=a.txRate;});
  return {enabled:!!monitor.enabled,intervalSeconds:monitor.intervalSeconds||5,historyEnabled:monitor.historyEnabled!==false,retentionDays:monitor.retentionDays||7,resolveDomains:!!monitor.resolveDomains,available:true,error:'',hasSample:!!hasSample,rateReady:!!hasSample,sampleAt:hasSample?sampled:0,sampleAgeMs:hasSample?now-sampled:-1,sampleDurationMs:38,sampleSeconds:monitor.intervalSeconds||5,lastAutoCleanupAt:sampled-1800000,failures:0,uidAvailable:true,connectionsAvailable:true,total:hasSample?total:{},apps:hasSample?apps:[],connections:hasSample?connections:[],interfaces:hasSample?[Object.assign({name:'以太网'},total)]:[],series:series,source:'mock',compatibilityNote:'模拟数据，不采集真实网络信息。'};
 }
 async function data(path,body){path=canonical(path);
  if(path==='config/native'){
   var native=stored('aurora-native-fields',nativeFixture);
   if(body!==undefined){only(body,['changes']);Object.keys(body.changes).forEach(function(id){var field=native.fields.filter(function(f){return f.id===id;})[0];if(!field||id.indexOf('ambient:')===0||id.indexOf('location:')===0)fail('INVALID_ARGUMENT','请使用统一屏保或服务设置');var value=body.changes[id];if(field.type==='boolean'&&typeof value!=='boolean')fail('INVALID_ARGUMENT','开关参数无效');if(['number','select','minutes'].indexOf(field.type)>=0&&(!Number.isInteger(value)||value<field.min||value>field.max))fail('INVALID_ARGUMENT','数值超出范围');field.value=value;});save('aurora-native-fields',native);native.verified=true;}
   return native;
  }
  if(path==='music/usb/volumes')return [];
  if(path==='logs')return [{time:Date.now(),ip:'192.0.2.10',action:'模拟日志',detail:'仅供电脑预览，不是电视操作记录',status:200}];
  if(path==='logs/clear'){only(body,['confirm']);if(body.confirm!==true)fail('INVALID_ARGUMENT','请先确认');return {cleared:true};}
  if(path==='apps/cards')return [];
  if(path==='apps/layout')return fixture.apps.map(function(app,index){return {package:app.package,key:app.package,label:app.name,visible:true,pinned:index<4,rank:index};});
  if(path==='bgm/builtin'||path==='bgm/delete'||path==='config/native/reset-color'||path==='inputs/name')fail('NOT_IMPLEMENTED','模拟预览不执行此设备操作');

 if(path.indexOf('network/traffic')===0)return traffic(path,body);

 if(path==='inputs/shortcut'){var shortcut=stored('aurora-input-shortcut',{id:'EN_TCL_HDMI1',remoteHint:true});if(body){only(body,['id','remoteHint']);if(body.id&&!/^EN_TCL_(HDMI[1-4]|AV[12])$/.test(body.id))fail('INVALID_ARGUMENT','Invalid input');if(body.remoteHint!==undefined&&typeof body.remoteHint!=='boolean')fail('INVALID_ARGUMENT','Invalid remoteHint');shortcut=save('aurora-input-shortcut',Object.assign(shortcut,body));}return shortcut;}
 if(path==='system/idle-standby'){var idle=stored('aurora-idle-standby',{enabled:false});if(body){only(body,['enabled']);if(typeof body.enabled!=='boolean')fail('INVALID_ARGUMENT','enabled must be boolean');idle=save('aurora-idle-standby',{enabled:body.enabled});}return {enabled:idle.enabled,minutes:15,available:true,idleSeconds:0,remainingSeconds:900,reason:idle.enabled?'计时中':'已关闭',wake:'power_key',scope:'desktop_screensaver',paused:false};}
  if(path==='system/capabilities'){var unavailable={available:false,backend:'',preferred:'auto',candidates:[]};return {launch:clone(unavailable),key:clone(unavailable),screenshot:clone(unavailable),standby:clone(unavailable),adb:{connected:false,pending:false,message:'电脑模拟未连接电视；真实控制需在电视控制台使用'}};}
  if(path==='apps'){var overrides=stored('aurora-mock-app-manager',[]);return clone(fixture.apps).map(function(a){var v=overrides.find(function(b){return b.package===a.package;});return v?Object.assign(a,{name:v.name,icon:v.icon||a.icon}):a;});}
  if(path==='apps/manage'){
   var seed=fixture.apps.map(function(a,i){return Object.assign({},a,{originalName:a.name,version:'1.0',launchable:true,visible:true,rank:i,system:false,enabled:true,removable:true,canToggle:false,toggleReason:'非系统应用不支持停用'});});
   seed.push({package:'com.demo.weather',name:'天气服务（模拟）',originalName:'天气服务（模拟）',version:'2.0',launchable:true,visible:false,rank:seed.length,system:true,enabled:true,removable:false,canToggle:true,toggleReason:''},{package:'com.android.settings',name:'系统设置（模拟）',originalName:'系统设置（模拟）',version:'9',launchable:true,visible:true,rank:seed.length+1,system:true,enabled:true,removable:false,canToggle:false,toggleReason:'系统设置受到保护'});
   var items=stored('aurora-mock-app-manager',seed);if(body){only(body,['package','name','icon','resetIcon','visible','direction','enabled','confirm']);var operations=['name','icon','resetIcon','visible','direction','enabled'].filter(function(k){return body[k]!==undefined;});if(operations.length!==1)fail('INVALID_ARGUMENT','每次只修改一个应用属性');['visible','enabled','resetIcon','confirm'].forEach(function(k){if(body[k]!==undefined&&typeof body[k]!=='boolean')fail('INVALID_ARGUMENT','开关参数必须是布尔值');});if(body.direction!==undefined&&body.direction!==1&&body.direction!==-1)fail('INVALID_ARGUMENT','排序方向无效');if(body.name!==undefined&&(typeof body.name!=='string'||Array.from(body.name.trim()).length>60||/[\x00-\x1f\x7f\u202a-\u202e\u2066-\u2069]/.test(body.name)))fail('INVALID_ARGUMENT','应用名称无效');var item=items.find(function(a){return a.package===body.package;});if(!item)fail('INVALID_ARGUMENT','应用不存在');if(body.name!==undefined)item.name=body.name.trim()||item.originalName;if(body.icon){item.icon='data:image/png;base64,'+body.icon;item.customIcon=true;}if(body.resetIcon){delete item.icon;item.customIcon=false;}if(body.visible!==undefined)item.visible=body.visible;if(body.enabled!==undefined){if(!item.canToggle||!body.confirm)fail('INVALID_ARGUMENT','此操作需要确认且组件不能受保护');item.enabled=body.enabled;}if(body.direction){var at=items.indexOf(item),next=at+body.direction;if(next>=0&&next<items.length){items[at]=items[next];items[next]=item;}}items.forEach(function(a,i){a.rank=i;});save('aurora-mock-app-manager',items);}return {apps:items,theme:(await data('config')).theme,mock:true};
  }
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
  if(path==='bootstrap'){var state={apiVersion:1,stage:2,console:'/console/app.html',legacyConsole:'/console/app.html'};['weather','media','apps','status','themes'].forEach(function(k){state[k]=clone(fixture[k]);});state.apps=await data('apps');state.themes=await data('themes');state.time=await data('time');state.config=await data('config');state.appearance=await data('config/appearance');return state;}
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
  if(path.indexOf('services/cities?')===0){var query=(new URLSearchParams(path.split('?')[1]||'').get('name')||'').trim().toLowerCase();return [
   {name:'上海',region:'上海市',country:'中国',countryCode:'CN',lat:31.2304,lon:121.4737,timezone:'Asia/Shanghai',aliases:['上海','shanghai']},
   {name:'洛杉矶',region:'加利福尼亚州',country:'美国',countryCode:'US',lat:34.05223,lon:-118.24368,timezone:'America/Los_Angeles',aliases:['洛杉矶','los angeles']},
   {name:'深圳',region:'广东省',country:'中国',countryCode:'CN',lat:22.54554,lon:114.0683,timezone:'Asia/Shanghai',aliases:['深圳','shenzhen']},
   {name:'耒阳',region:'湖南省',country:'中国',countryCode:'CN',lat:26.4167,lon:112.85,timezone:'Asia/Shanghai',aliases:['耒阳','耒阳市','leiyang']},
   {name:'伦敦',region:'英格兰',country:'英国',countryCode:'GB',lat:51.5085,lon:-.1257,timezone:'Europe/London',aliases:['伦敦','london']}
  ].filter(function(v){return !!query&&v.aliases.some(function(a){return a.indexOf(query)>=0;});}).map(function(v){var c=clone(v);delete c.aliases;return c;});}
  if(path==='logs/settings'){var audit=stored('aurora-mock-log-retention',{retentionDays:0});if(body){only(body,['retentionDays']);if([0,1,3,7,15,30].indexOf(body.retentionDays)<0)fail('INVALID_ARGUMENT','保存期限无效');audit=save('aurora-mock-log-retention',body);}return audit;}
  if(path==='updates'||path==='updates/config'){
   var policy=stored('aurora-mock-update-policy',{autoCheck:true,automatic:false});
   if(body){only(body,['autoCheck','automatic']);Object.keys(body).forEach(function(k){if(typeof body[k]!=='boolean')fail('INVALID_ARGUMENT','开关须为布尔值');});Object.assign(policy,body);if(body.autoCheck===false)policy.automatic=false;if(body.automatic===true)policy.autoCheck=true;save('aurora-mock-update-policy',policy);}
   return Object.assign({currentVersion:'1.0.0',phase:'idle',busy:false,maintenanceAvailable:true,maintenanceReason:'模拟数据：不执行真实安装',message:'模拟数据：设置仅保存在此浏览器，不会更新电视',routes:[],sources:['https://raw.githubusercontent.com/Thalveron/Aurora-Launcher/main/updates/latest.json','https://gh-proxy.com/https://raw.githubusercontent.com/Thalveron/Aurora-Launcher/main/updates/latest.json'],latest:null,previous:null,newVersion:false,rollbackAvailable:false},policy);
  }
  if(path.indexOf('updates/')===0)fail('NOT_IMPLEMENTED','模拟预览不能下载、安装或清空电视数据');
  if(path==='services/test'){var tested=await data('services');tested.weather={success:true,updating:false,updatedAt:Date.now(),error:''};tested.time=Object.assign(tested.time,{updatedAt:Date.now(),updating:false,error:'',source:'NTP（模拟测试）',offsetMs:0});return save('aurora-mock-services',tested);}
  if(path==='services/reset')return data('services',clone(fixture.services.config));
  if(path==='services'){var current=stored('aurora-mock-services',fixture.services);if(body){var value=clone(body),built=clone(fixture.services.config);if(!value.weather.urls.length)['provider','urls','headers','mapping','key'].forEach(function(k){value.weather[k]=built.weather[k];});['ntp','http'].forEach(function(k){if(!value.time[k].length)value.time[k]=built.time[k];});current.config=value;current=save('aurora-mock-services',current);}return clone(current);}
  if(path==='music/local/settings')return clone(fixture.localMusic);
  if(Object.prototype.hasOwnProperty.call(fixture,path)&&body===undefined)return clone(fixture[path]);
  var known=routes.some(function(r){var pattern=r.path.replace(/[.*+?^$()|[\]\\]/g,'\\$&').replace(/\{[^}]+\}/g,'[^/]+');return new RegExp('^'+pattern+'$').test(path);});fail(known?'UNSUPPORTED':'NOT_FOUND',known?'This operation is unavailable in computer preview':'Unknown API route');
 }
 async function response(path,body){try{await ready;if(path==='actions'&&body!==undefined){only(body,['action','params']);return {ok:true,data:await execute(body.action,body.params)};}return {ok:true,data:await data(path,body)};}catch(e){return {ok:false,error:{code:e.code||'INVALID_ARGUMENT',message:e.message}};}}
 window.AuroraMock={response:response};
}());
