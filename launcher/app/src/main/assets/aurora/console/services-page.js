'use strict';
(function(){
 var host=document.querySelector('[data-panel="services"]');if(!host)return;
 var api=window.AuroraAPI,UI=window.AuroraConsoleUI,M=window.AuroraServiceModel;
 var service=null,defaults=null,draft=null,controls={},searchSequence=0,searching=false,searchResultsQuery='',testing=false,loading=false,committing=false,locked=[];
 function node(tag,cls,text){var e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e;}
 function text(element,value){if(element.textContent!==value)element.textContent=value;}
 function button(id,text,action){var b=node('button','secondary',text);b.id=id;b.type='button';b.onclick=action;return b;}
 function message(text,error){feedback.textContent=text;feedback.classList.toggle('ui-error',!!error);feedback.hidden=!text;}
 function lock(value){committing=value;host.setAttribute('aria-busy',String(value));if(value){locked=Array.from(host.querySelectorAll('input,select,textarea,button')).map(function(c){var item=[c,c.disabled];c.disabled=true;return item;});}else{locked.forEach(function(item){item[0].disabled=item[1];});locked=[];if(draft)render(draft.value);}}
 function allowed(){return !!(service&&service.time.canSyncSystem);}
 function textInput(id,type){var input=node('input');input.id=id;input.type=type||'text';return input;}
 function cluster(){return node('div','ui-control-cluster');}
 function select(id,options){var input=node('select');input.id=id;Object.keys(options).forEach(function(key){var option=node('option','',options[key]);option.value=key;input.append(option);});return input;}
 function field(section,key,id,type){var c=textInput(id,type);controls[section+'.'+key]=c;if(type==='checkbox'){c.className='ui-switch';c.setAttribute('role','switch');}c[type==='checkbox'?'onchange':'oninput']=function(){if(!draft)return;if(section==='weather'&&key==='city'){searchSequence++;searching=false;resultsRow.hidden=true;cityNote.textContent='';}draft.set(M.change(draft.value,section,key,type==='checkbox'?c.checked:c.value));};return c;}
 function area(key,id,rows){var c=node('textarea');c.id=id;c.rows=rows||3;c.spellcheck=false;controls['editors.'+key]=c;c.oninput=function(){if(draft)draft.set(M.editor(draft.value,key,c.value));};return c;}
 function editorRow(title,description,control){var row=UI.row(title,description,control);row.classList.add('ui-editor-row');return row;}
 function fold(title,rows){var d=node('details','ui-fold');d.append(node('summary','',title));rows.forEach(function(r){d.append(r);});return d;}
 function range(input,min,max,step){input.min=min;input.max=max;input.step=step||1;return input;}
 function formatTime(epoch){if(!epoch)return '尚未更新';try{return new Date(epoch).toLocaleString('zh-CN',{timeZone:service.config.time.timezone,hour12:false});}catch(e){return new Date(epoch).toLocaleString();}}
 host.replaceChildren();host.classList.add('ui-service-page');
 var heading=node('div','ui-page-heading'),title=node('div');title.append(node('h2','','天气与时间服务'),node('p','','统一管理所有主题与屏保使用的城市、天气和当地时间。'));var test=button('test-services','测试已保存设置',testSaved);heading.append(title,test);host.append(heading);
 var previewNotice=node('div','ui-help','电脑模拟预览：仅保存在本浏览器，不修改电视。城市搜索提供部分示例，连接电视后使用完整城市库。');previewNotice.hidden=!api.mock;host.append(previewNotice);
 var feedback=node('div','ui-help');feedback.id='services-feedback';feedback.setAttribute('role','status');feedback.hidden=true;host.append(feedback);
 var status=node('div','ui-status-grid');status.id='service-status';status.setAttribute('role','status');
 function statusBox(title){var box=node('section','ui-card ui-status-card'),top=node('div','ui-status-heading'),state=node('span','ui-badge','读取中'),value=node('strong','ui-status-value','—'),meta=node('small','ui-status-meta','尚未更新');top.append(node('span','',title),state);box.append(top,value,meta);status.append(box);return {state:state,value:value,meta:meta};}
 var weatherStatus=statusBox('天气服务'),timeStatus=statusBox('时间服务');host.append(status);
 var statusHint=node('p','ui-section-caption','运行状态来自已保存的配置，未保存的修改不参与测试。');host.append(statusHint);
 var cityInput=field('weather','city','service-city','text');cityInput.maxLength=80;cityInput.autocomplete='off';cityInput.placeholder='输入城市中文或英文名';var search=button('services-city-search','搜索',searchCity),cityGroup=cluster();cityGroup.append(cityInput,search);
 var results=select('services-city-results',{}),resultsRow=UI.row('搜索结果','请核对地区和国家后再选择',results);resultsRow.hidden=true;var cityNote=node('span','ui-inline-note');cityNote.id='services-city-note';resultsRow.querySelector('.setting-row-copy').append(cityNote);
 var zone=textInput('services-timezone');zone.readOnly=true;zone.setAttribute('aria-label','城市时区（自动匹配）');
 var lat=range(field('weather','lat','service-lat','number'),-90,90,.00001),lon=range(field('weather','lon','service-lon','number'),-180,180,.00001);
 var locationCard=UI.card('城市与时区','天气、日期与显示时间跟随同一个城市，并自动处理夏令时。',[UI.row('天气城市','搜索后选择结果，避免同名城市混淆',cityGroup),resultsRow,UI.row('当地时区','无需开启系统校时权限',zone)]);
 var locationAdvanced=fold('手动填写经纬度',[UI.row('纬度','范围 −90～90',lat),UI.row('经度','范围 −180～180；保存后自动匹配时区',lon)]);locationCard.append(locationAdvanced);host.append(locationCard);
 var provider=select('service-provider',{openmeteo:'Open-Meteo',qweather:'和风天气',custom:'自定义 JSON'});controls['weather.provider']=provider;provider.onchange=function(){if(!draft)return;draft.set(M.change(draft.value,'weather','provider',provider.value));if(provider.value==='custom')weatherAdvanced.open=true;};
 var template=button('services-apply-template','套用模板',applyTemplate),providerGroup=cluster();providerGroup.append(provider,template);
 var weatherInterval=range(field('weather','intervalMinutes','service-weather-interval','number'),5,1440),key=field('weather','key','service-key','password');key.autocomplete='off';key.placeholder='没有密钥可留空';var keyGroup=cluster(),reveal=button('services-key-reveal','显示',function(){key.type=key.type==='password'?'text':'password';reveal.textContent=key.type==='password'?'显示':'隐藏';reveal.setAttribute('aria-pressed',String(key.type==='text'));});reveal.setAttribute('aria-pressed','false');keyGroup.append(key,reveal);
 var urls=area('urls','weather-urls',4);urls.placeholder='留空使用默认 Open-Meteo 服务';var headers=area('headers','weather-headers',4),mapping=area('mapping','weather-mapping',7);
 var weatherCard=UI.card('天气服务','默认使用 Open-Meteo；可换成和风天气或自定义 JSON 服务。',[UI.row('天气适配器','选择解析方式；套用模板不会改变城市',providerGroup),UI.row('刷新间隔','5～1440 分钟，按保存值自动刷新',weatherInterval),UI.row('服务密钥','按服务商要求填写，也可通过请求头认证',keyGroup)]);
 var weatherAdvanced=fold('天气 API 地址与字段映射',[editorRow('天气 API 地址','每行一个，最多 8 个；按顺序尝试。留空恢复默认接口、密钥与解析配置。',urls),editorRow('请求头（JSON）','JSON 对象，字段值使用字符串',headers),editorRow('JSONPath 字段映射','支持 $.字段 和 [序号]；和风天气使用内置解析',mapping)]);
 var placeholders=node('p','ui-fold-note','地址占位符：{lat}、{lon}、{city}、{key}、{timezone}。仅支持 HTTPS；请勿在网址中填写用户名和密码。');weatherAdvanced.append(placeholders);weatherCard.append(weatherAdvanced);host.append(weatherCard);
 var timeInterval=range(field('time','intervalMinutes','service-time-interval','number'),5,10080),sync=field('time','syncSystem','sync-system','checkbox'),permission=node('span','setting-row-description');permission.id='time-permission';
 var syncRow=UI.row('同步到系统时间','只影响电视系统时钟；不开启也能显示城市当地时间',sync);syncRow.querySelector('.setting-row-copy').append(permission);
 var ntp=area('ntp','time-ntp',3);ntp.placeholder='留空使用内置 NTP 服务器';var http=area('http','time-http',3);http.placeholder='留空使用内置备用校时地址';
 var hour24=field('time','hour24','service-hour24','checkbox');
 var timeCard=UI.card('时间服务','优先使用 NTP，失败时尝试备用 HTTPS 网站的 Date 时间。',[UI.row('24 小时制','控制所有主题的时间显示格式，不需要系统校时权限',hour24),UI.row('校时间隔','5～10080 分钟；显示时区由城市决定',timeInterval),syncRow]);
 var timeAdvanced=fold('自定义时间服务器',[editorRow('NTP 服务器','每行一个，最多 8 个；填写域名或 IPv4，不带协议和端口',ntp),editorRow('备用 HTTPS Date 地址','每行一个完整 HTTPS 网址；清空后使用默认',http)]);timeCard.append(timeAdvanced);host.append(timeCard);
 var footer=node('div','ui-page-tools'),reset=button('reset-services','恢复全部默认…',resetDefaults);footer.append(node('span','','恢复上海城市、默认服务地址与关闭系统校时；保存后生效。'),reset);host.append(footer);
 Object.keys(controls).forEach(function(k){controls[k].disabled=true;});provider.disabled=search.disabled=test.disabled=template.disabled=reset.disabled=true;
 function render(value){Object.keys(controls).forEach(function(path){var keys=path.split('.'),c=controls[path],v=keys[0]==='editors'?value.editors[keys[1]]:value.config[keys[0]][keys[1]];if(c.type==='checkbox')c.checked=(path==='time.syncSystem'?allowed():true)&&(path==='time.hour24'?v!==false:!!v);else if(c.value!==String(v))c.value=v;});
  if(!value.selection&&searchResultsQuery!==String(value.config.weather.city).trim())resultsRow.hidden=true;
  var pendingZone=M.manualLocation(value,draft.baseline)||(value.selection&&!value.selection.timezone);var z=pendingZone?'保存后按经纬度自动匹配':value.config.time.timezone;zone.value=z;zone.title=z;sync.disabled=committing||!allowed();text(permission,allowed()?'已有系统校时能力，需手动开启。':(service.time.permissionReason||'当前设备没有系统校时权限，不能开启。'));
  template.hidden=value.config.weather.provider==='custom';template.disabled=committing||value.config.weather.provider==='openmeteo'&&!defaults;search.disabled=committing||testing||searching;reset.disabled=committing||!defaults;
 }
 function showStatus(next){service=next;var w=next.weather,t=next.time;text(weatherStatus.state,w.updating?'更新中':w.error?'连接失败':w.success?'正常':'等待更新');weatherStatus.state.classList.toggle('ui-badge-error',!!w.error);text(weatherStatus.value,next.config.weather.city+' · '+({openmeteo:'Open-Meteo',qweather:'和风天气',custom:'自定义 JSON'}[next.config.weather.provider]||next.config.weather.provider));text(weatherStatus.meta,(w.error?w.error+' · ':'')+(w.updatedAt?'上次更新 '+formatTime(w.updatedAt):'尚未更新'));
  text(timeStatus.state,t.updating?'校时中':t.error?'连接失败':t.updatedAt?'正常':'等待校时');timeStatus.state.classList.toggle('ui-badge-error',!!t.error);text(timeStatus.value,next.config.time.timezone);text(timeStatus.meta,(t.error?t.error+' · ':t.source?t.source+' · ':'')+(t.updatedAt?'上次校时 '+formatTime(t.updatedAt):'尚未校时'));
  if(draft){if(!allowed()&&draft.value.config.time.syncSystem)draft.set(M.change(draft.value,'time','syncSystem',false));else render(draft.value);}
 }
 async function load(){if(loading)return;loading=true;message('正在读取服务设置…');try{
  var data=await api.request('services');try{var response=await fetch('/console/service-defaults.json',{cache:'no-store'});if(!response.ok)throw Error();defaults=await response.json();}catch(e){defaults=null;}
  service=data;var initial=M.state(data.config,allowed());draft=UI.draft('global-services',{label:'天气与时间服务',initial:initial,render:render,validate:function(value){try{M.payload(value,draft.baseline,allowed());}catch(e){if(/NTP|备用|校时间隔/.test(e.message))timeAdvanced.open=true;else if(/纬度|经度/.test(e.message))locationAdvanced.open=true;else if(/JSON|映射|API|请求头/.test(e.message))weatherAdvanced.open=true;message(e.message,true);throw e;}},save:async function(value){lock(true);try{var accepted=await api.request('services',M.payload(value,draft.baseline,allowed()));service=accepted;return accepted;}finally{lock(false);}},discard:function(){message('');resultsRow.hidden=true;searchSequence++;searching=false;},saved:function(accepted){draft.replace(M.state(accepted.config,allowed()));showStatus(accepted);message(api.mock?'已保存模拟配置，未操作电视。':'已同步到电视。');}});
  Object.keys(controls).forEach(function(k){controls[k].disabled=false;});search.disabled=test.disabled=provider.disabled=false;test.textContent='测试已保存设置';showStatus(data);render(draft.value);message(defaults?'':'默认模板暂时未能读取，现有设置仍可编辑与保存。',!defaults);
 }catch(e){message('无法读取服务设置：'+e.message,true);test.disabled=false;test.textContent='重新连接';}finally{loading=false;}}
 async function searchCity(){if(!draft)return;var query=cityInput.value.trim();if(!query){message('请先输入城市名称。',true);return;}var sequence=++searchSequence;searching=true;search.disabled=true;cityNote.textContent='正在搜索…';try{
  var list=await api.request('services/cities?name='+encodeURIComponent(query));if(sequence!==searchSequence||cityInput.value.trim()!==query)return;results.replaceChildren();var prompt=node('option','',list.length?'请选择城市和地区':'没有找到城市，可修改名称或手动填写坐标');prompt.value='';results.append(prompt);list.forEach(function(city,index){var option=node('option','',[city.name,city.region,city.country].filter(Boolean).join(' · '));option.value=String(index);results.append(option);});searchResultsQuery=query;resultsRow.hidden=false;cityNote.textContent=list.length?'搜索结果尚未保存':'';results.onchange=function(){if(this.value==='')return;draft.set(M.city(draft.value,list[Number(this.value)]));cityNote.textContent='已选中，保存后全局生效';message('');};
 }catch(e){message('搜索失败：'+e.message,true);}finally{if(sequence===searchSequence){searching=false;search.disabled=testing;}}}
 async function applyTemplate(){if(!draft)return;var kind=provider.value;if(kind==='custom')return;if(!confirm('替换当前天气接口模板和请求头？城市不变，保存后才生效。'))return;draft.set(M.preset(draft.value,kind,defaults));weatherAdvanced.open=true;message(kind==='qweather'?'请填写和风 API Host 和认证信息，然后保存。':'已套用 Open-Meteo 默认接口，保存后生效。');}
 function resetDefaults(){if(!defaults||!draft)return;if(!confirm('把天气和时间恢复为内置默认配置？城市将恢复上海，系统校时关闭；保存前仍可撤销。'))return;draft.set(M.city(M.state(defaults,allowed()),{name:defaults.weather.city,lat:defaults.weather.lat,lon:defaults.weather.lon,timezone:defaults.time.timezone}));resultsRow.hidden=true;message('默认配置已放入草稿，保存后生效。');}
 async function testSaved(){if(!draft){load();return;}if(testing)return;if(JSON.stringify(draft.value)!==JSON.stringify(draft.baseline)&&!confirm('当前有未保存的修改。本次仅测试电视已保存的配置，是否继续？'))return;testing=true;test.disabled=true;test.textContent='测试中…';try{showStatus(await api.request('services/test',{}));message(api.mock?'模拟测试完成，未向外部服务发送请求。':'已发起天气与校时测试，结果会自动更新。');}catch(e){message('测试未完成：'+e.message,true);}finally{testing=false;test.disabled=false;test.textContent='测试已保存设置';if(draft)render(draft.value);}}
 var refresh=setInterval(async function(){if(document.hidden||host.hidden||!draft||testing||committing)return;try{showStatus(await api.request('services'));}catch(e){message('暂时无法读取状态，未保存的修改仍保留在页面中。',true);}},5000);
 window.addEventListener('pagehide',function(){clearInterval(refresh);});load();
}());
