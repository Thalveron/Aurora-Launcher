'use strict';
(function(){
 var api=window.AuroraAPI,M=window.AuroraTrafficModel,Charts=window.AuroraLineChart,data={},selected=null,timer=null,request=null,configBusy=false,historyData=null,historySequence=0,historyUpdated=0,epoch=0,loaded=false,sortDirection='desc',rowCache=new Map();
 function el(id){return document.getElementById(id);}if(!el('traffic-panel'))return;
 var settings=window.AuroraDrawer.bind(el('traffic-settings')),details=window.AuroraDrawer.bind(el('traffic-detail'));
 function node(tag,text,cls){var n=document.createElement(tag);if(text!=null)n.textContent=text;if(cls)n.className=cls;return n;}
 function set(target,text){if(target.textContent!==String(text))target.textContent=text;}
 function date(n){return n?new Date(n).toLocaleString('zh-CN'):'尚无记录';}
 function active(){return location.hash==='#traffic'&&!document.hidden;}
 function status(text){set(el('traffic-status'),text);}
 function download(value){var blob=new Blob([JSON.stringify(value,null,2)],{type:'application/json'}),url=URL.createObjectURL(blob),a=node('a');a.href=url;a.download='Aurora-流量监控-'+Date.now()+'.json';a.click();setTimeout(function(){URL.revokeObjectURL(url);},1000);}
 function setBusy(value){configBusy=value;['traffic-toggle','traffic-empty-start','traffic-interval','traffic-store','traffic-retention','traffic-dns','traffic-clear'].forEach(function(id){el(id).disabled=value;});}
 function renderControls(){
  set(el('traffic-toggle'),data.enabled?'暂停':'开启监控');el('traffic-toggle').disabled=configBusy||!loaded;el('traffic-empty-start').disabled=configBusy||!loaded||!!data.enabled;
  [['traffic-interval',String(data.intervalSeconds||5)],['traffic-retention',String(data.retentionDays||7)]].forEach(function(v){if(document.activeElement!==el(v[0]))el(v[0]).value=v[1];});el('traffic-store').checked=data.historyEnabled!==false;el('traffic-dns').checked=!!data.resolveDomains;
  set(el('traffic-source'),api.mock?'模拟数据':'系统计数');var badge=el('traffic-badge');badge.className='ui-badge state-badge '+(data.error?'error':data.enabled?'connected':'disconnected');set(badge,data.error?'采集异常':data.enabled?'监控中':'已暂停');
  status(data.error?'采集暂不可用：'+data.error+(data.hasSample?'；保留上次有效数据。':''):data.hasSample?(data.enabled?'最后更新 ':'已暂停，最后记录 ')+new Date(data.sampleAt).toLocaleTimeString('zh-CN')+(data.enabled&&data.sampleAgeMs>15000?' · 数据可能已过期':'')+(data.rateReady?'':' · 等待下一次采样计算速度'):data.enabled?'正在等待有效采样，请稍候':'开启监控后查看每个应用的网络用量');
  set(el('traffic-settings-status'),'最后采集：'+date(data.sampleAt)+'\n采集耗时：'+(data.sampleDurationMs==null?'—':data.sampleDurationMs+' 毫秒')+'\n历史保留：'+(data.retentionDays||7)+' 天\n上次定时清理：'+date(data.lastAutoCleanupAt)+'\n'+(api.mock?'此页为模拟数据，不会读取或改变电视流量。':data.compatibilityNote||'关闭网页后，监控仍按当前开关状态运行。'));
 }
 function totals(){
  var box=el('traffic-totals'),total=data.hasSample?data.total||{}:{};
  if(!box.children.length)['实时下载','实时上传','开机累计下载','开机累计上传'].forEach(function(label){var card=node('div',null,'traffic-stat');card.append(node('small',label),node('strong','—','traffic-missing'));box.append(card);});
  [M.rate(total.rxRate),M.rate(total.txRate),M.bytes(total.rxBytes),M.bytes(total.txBytes)].forEach(function(value,i){var number=box.children[i].querySelector('strong');set(number,value);number.classList.toggle('traffic-missing',value==='—');});
  var nets=el('traffic-interfaces');nets.replaceChildren();(data.interfaces||[]).forEach(function(net){nets.append(node('div',net.name+' · 下载 '+M.rate(net.rxRate)+' · 上传 '+M.rate(net.txRate),'traffic-interface'));});
  var points=M.points(data.series,false).filter(function(p){return M.valid(p.rx)||M.valid(p.tx);});el('traffic-chart-wrap').hidden=!points.length;el('traffic-chart-empty').hidden=!!points.length;set(el('traffic-chart-empty-note'),data.enabled?'正在等待采样，至少需要两次有效记录才能计算速度':'开启监控后即可查看网络趋势');set(el('traffic-empty-start'),data.enabled?'正在采样…':'开启监控');
  if(points.length)Charts.render(el('traffic-chart'),points,Math.max(90000,(data.intervalSeconds||5)*4000));
 }
 function valuePair(cell,rx,tx,speed){
  if(!cell.children.length)cell.append(node('span',null,'traffic-download-value'),node('span',null,'traffic-upload-value'));
  var down=speed?M.rate(rx):M.bytes(rx),up=speed?M.rate(tx):M.bytes(tx);set(cell.children[0],'↓ '+down);set(cell.children[1],'↑ '+up);cell.children[0].classList.toggle('traffic-missing',down==='—');cell.children[1].classList.toggle('traffic-missing',up==='—');cell.setAttribute('aria-label','下载 '+down+'，上传 '+up);
 }
 function makeRow(uid){
  var row=node('tr');row.dataset.uid=String(uid);row.tabIndex=0;row.setAttribute('aria-haspopup','dialog');row.setAttribute('aria-controls','traffic-detail');
  var app=node('td'),identity=node('div',null,'traffic-app-identity'),avatar=node('span',null,'traffic-app-avatar'),image=node('img');image.alt='';image.loading='lazy';image.hidden=true;var text=node('div'),name=node('strong',null,'traffic-app-name'),info=node('small');text.append(name,info);identity.append(avatar,image,text);app.append(identity);row.append(app,node('td',null,'traffic-number-pair'),node('td',null,'traffic-number-pair'),node('td',null,'traffic-number-pair'),node('td',null,'traffic-suspicion'));
  row.onclick=function(){openDetail(uid);};row.onkeydown=function(e){if(e.key==='Enter'||e.key===' '){e.preventDefault();openDetail(uid);}};
  var item={row:row,name:name,info:info,image:image,avatar:avatar,icon:''};image.onerror=function(){image.hidden=true;avatar.hidden=false;};return item;
 }
 function applications(){
  var sort=el('traffic-sort').value,items=M.rows(data.apps,el('traffic-filter').value,el('traffic-active').checked,sort,sortDirection),body=el('traffic-apps'),focus=document.activeElement&&document.activeElement.closest('[data-uid]'),focusedUid=focus&&focus.dataset.uid;
  set(el('traffic-result-count'),items.length+' 个应用');document.querySelectorAll('[data-traffic-sort]').forEach(function(b){var chosen=b.dataset.trafficSort===sort;b.classList.toggle('is-sorted',chosen);b.setAttribute('aria-pressed',String(chosen));b.title=chosen?(sortDirection==='desc'?'从高到低，点击切换':'从低到高，点击切换'):'点击排序';var label=b.dataset.baseLabel||b.textContent;b.dataset.baseLabel=label;set(b,label+(chosen?(sortDirection==='desc'?' ↓':' ↑'):''));});document.querySelectorAll('[data-sort-group]').forEach(function(th){th.setAttribute('aria-sort',th.querySelector('.is-sorted')?(sortDirection==='desc'?'descending':'ascending'):'none');});
  var keep=new Set(items.map(function(a){return String(a.uid);}));Array.from(body.children).forEach(function(row){if(!keep.has(row.dataset.uid))row.remove();});var cursor=body.firstChild;
  items.forEach(function(app){var cached=rowCache.get(app.uid);if(!cached){cached=makeRow(app.uid);rowCache.set(app.uid,cached);}var row=cached.row;set(cached.name,app.name||'未识别应用');set(cached.info,'UID '+app.uid+(app.sharedUid?' · 共享':'')+(data.connectionsAvailable===false?'':' · '+(app.connections||0)+' 个连接'));row.setAttribute('aria-label',(app.name||'应用')+'，UID '+app.uid+'，查看流量详情');
   var pkg=(app.packages||[])[0],icon=app.icon||(!api.mock&&pkg&&/^[A-Za-z0-9_.]+$/.test(pkg.package)?'/api/v1/icons/'+pkg.package:'');set(cached.avatar,(app.name||'?').slice(0,1));if(icon!==cached.icon){cached.icon=icon;cached.image.hidden=!icon;cached.avatar.hidden=!!icon;if(icon)cached.image.src=icon;else cached.image.removeAttribute('src');}
   valuePair(row.children[1],app.rxRate,app.txRate,true);valuePair(row.children[2],app.sessionRx,app.sessionTx,false);valuePair(row.children[3],app.rxBytes,app.txBytes,false);
   var suspicion=row.children[4],hint=app.uploadHint||'';if(hint){var tag=suspicion.firstElementChild;if(!tag){tag=node('span','可疑','traffic-suspicious-badge');tag.tabIndex=0;suspicion.replaceChildren(tag);}tag.title=hint+'。仅为核对线索，不能据此认定 PCDN。';tag.setAttribute('aria-label','可疑：'+tag.title);}else set(suspicion,'—');
   if(row!==cursor)body.insertBefore(row,cursor);cursor=row.nextSibling;
  });
  if(!items.length){var row=node('tr'),cell=node('td',data.hasSample?'没有符合筛选条件的应用':'暂无应用记录，开启监控后等待采样','traffic-table-empty');cell.colSpan=5;row.append(cell);body.replaceChildren(row);}
  if(focusedUid&&document.activeElement!==focus){var restored=Array.from(body.children).find(function(row){return row.dataset.uid===focusedUid;});if(restored)restored.focus({preventScroll:true});}
 }
 function renderDetail(){
  if(selected===null||!details.isOpen())return;var app=(data.apps||[]).find(function(a){return a.uid===selected;});set(el('traffic-detail-title'),selected===-1?'网卡总历史':app?app.name:'应用流量详情');set(el('traffic-detail-caption'),selected===-1?'所有可见连接与网卡历史':'UID '+selected+' · '+(api.mock?'模拟数据':'系统计数'));
  var packages=el('traffic-packages'),packageKey=JSON.stringify([selected,app&&app.packages]);if(packages.dataset.snapshot!==packageKey){packages.dataset.snapshot=packageKey;packages.replaceChildren();if(app)(app.packages||[]).forEach(function(pkg){var item=node('div',null,'traffic-package');item.append(node('strong',pkg.name||pkg.package),node('code',pkg.package),node('small',(pkg.system?'系统应用':'用户应用')+(pkg.enabled===false?' · 已停用':'')));packages.append(item);});if(!app)packages.append(node('p',selected===-1?'当前展示网卡汇总，不对应单个应用包。':'应用暂未出现在本次采样中。'));}
  set(el('traffic-detail-stats'),app?'峰值下载 '+M.rate(app.peakRx)+' · 峰值上传 '+M.rate(app.peakTx)+'\n最近活动：'+date(app.lastActiveAt)+'\n连续上传达到 32 KB/s：'+(app.sustainedUploadSeconds||0)+' 秒'+(app.sharedUid?'\n这些组件共用 UID，只能合计统计。':''):'连接快照仅反映采样时刻，不是完整网络日志。');
  var links=(data.connections||[]).filter(function(c){return selected===-1||c.uid===selected;}).sort(function(a,b){return (a.scope==='public'?-1:0)-(b.scope==='public'?-1:0)||String(a.remote).localeCompare(String(b.remote));}),publicIps=new Set(links.filter(function(c){return c.scope==='public';}).map(function(c){return c.remote;}));set(el('traffic-connection-count'),links.length+' 个连接 · '+publicIps.size+' 个公网 IP');
  var body=el('traffic-connections');body.replaceChildren();var scopes={public:'公网',lan:'局域网',loopback:'本机回环',any:'监听 / 未指定',multicast:'组播',unknown:'未知'};
  links.forEach(function(c){var row=node('tr'),address=function(ip,port){return (String(ip).indexOf(':')>=0?'['+ip+']':ip)+':'+port;};row.append(node('td',String(c.protocol).toUpperCase()+' / '+c.state+(selected===-1?' · UID '+c.uid:'')),node('td',address(c.local,c.localPort)),node('td',address(c.remote,c.remotePort)),node('td',(scopes[c.scope]||'未知')+' · '+(c.reverseName?'反向解析：'+c.reverseName:data.resolveDomains?'暂无解析结果':'反向解析未开启')+'\n首次 '+date(c.firstSeenAt)+'\n持续观测 '+(c.observedSeconds||0)+' 秒'),node('td',M.bytes(c.txQueueBytes)+' / '+M.bytes(c.rxQueueBytes)));body.append(row);});
  if(!links.length){var row=node('tr'),cell=node('td',data.connectionsAvailable===false?'系统未开放连接列表，请检查设备权限。':'当前没有可见连接，短连接可能发生在两次采样之间。','traffic-table-empty');cell.colSpan=5;row.append(cell);body.append(row);}
 }
 function openDetail(uid){selected=uid;historyData=null;details.open();renderDetail();loadHistory();}
 async function loadHistory(){
  if(selected===null||!details.isOpen())return;var sequence=++historySequence,uid=selected,hours=Number(el('traffic-hours').value);historyUpdated=Date.now();set(el('traffic-history-note'),'正在读取历史…');Charts.render(el('traffic-history-chart'),[],90000);
  try{var result=await api.request('network/traffic/history?uid='+uid+'&hours='+hours);if(sequence!==historySequence||uid!==selected||!details.isOpen())return;historyData=result;historyUpdated=Date.now();Charts.render(el('traffic-history-chart'),M.points(result.points,true),Math.max(90000,(result.bucketSeconds||60)*2500));set(el('traffic-history-note'),(result.points||[]).length?(result.note||'历史从开启记录后开始。')+' 图中显示每段时间的平均速度。':'这个时间范围暂无历史记录。');}catch(e){if(sequence===historySequence&&details.isOpen())set(el('traffic-history-note'),'读取失败：'+e.message);}
 }
 function render(){renderControls();totals();applications();renderDetail();if(details.isOpen()&&Date.now()-historyUpdated>=60000)loadHistory();}
 function refresh(){
  if(request||configBusy)return request||Promise.resolve();var revision=epoch;el('traffic-refresh').disabled=true;el('traffic-refresh').classList.add('is-refreshing');
  request=(async function(){try{var result=await api.request('network/traffic');if(revision!==epoch)return;data=result;loaded=true;render();}catch(e){if(revision===epoch){set(el('traffic-badge'),'采集异常');el('traffic-badge').className='ui-badge state-badge error';status('暂时无法读取：'+e.message);}}finally{request=null;el('traffic-refresh').disabled=false;el('traffic-refresh').classList.remove('is-refreshing');}})();return request;
 }
 async function config(patch){
  if(configBusy)return;setBusy(true);set(el('traffic-settings-feedback'),'正在同步…');try{if(request)await request;data=await api.request('network/traffic/config',patch);loaded=true;render();set(el('traffic-settings-feedback'),api.mock?'已更新模拟设置':'已同步到电视');}catch(e){status('设置未保存：'+e.message);set(el('traffic-settings-feedback'),'设置未保存：'+e.message);renderControls();}finally{setBusy(false);renderControls();}
 }
 el('traffic-settings-open').onclick=function(){settings.open();};el('traffic-toggle').onclick=function(){config({enabled:!data.enabled});};el('traffic-empty-start').onclick=function(){config({enabled:true});};el('traffic-refresh').onclick=refresh;
 el('traffic-interval').onchange=function(){config({intervalSeconds:Number(this.value)});};el('traffic-store').onchange=function(){config({historyEnabled:this.checked});};el('traffic-retention').onchange=function(){config({retentionDays:Number(this.value)});};el('traffic-dns').onchange=function(){config({resolveDomains:this.checked});};
 el('traffic-sort').onchange=function(){sortDirection='desc';applications();};el('traffic-active').onchange=applications;el('traffic-filter').oninput=applications;
 document.querySelectorAll('[data-traffic-sort]').forEach(function(b){b.onclick=function(){var key=b.dataset.trafficSort;sortDirection=el('traffic-sort').value===key&&sortDirection==='desc'?'asc':'desc';el('traffic-sort').value=key;applications();};});
 el('traffic-hours').onchange=loadHistory;el('traffic-all-history').onclick=function(){selected=-1;renderDetail();loadHistory();};
 el('traffic-export').onclick=async function(){var b=this;b.disabled=true;try{var snapshot=await api.request('network/traffic/export',{});download({exportedAt:new Date().toISOString(),snapshot:snapshot,history:historyData});set(el('traffic-settings-feedback'),'导出文件已生成');}catch(e){set(el('traffic-settings-feedback'),'导出失败：'+e.message);}finally{b.disabled=false;}};
 el('traffic-clear').onclick=async function(){if(configBusy)return;var confirmed=await window.AuroraDrawer.confirm({title:'清空监控历史？',message:'将删除历史记录、趋势曲线和本次采集统计。此操作不能撤销；系统开机累计不会重置。',confirmLabel:'确认清空'});if(!confirmed||configBusy)return;setBusy(true);try{if(request)await request;await api.request('network/traffic/clear',{confirm:true});historyData=null;historySequence++;historyUpdated=0;set(el('traffic-settings-feedback'),'清空已提交，下一次采样后更新');}catch(e){set(el('traffic-settings-feedback'),'清空失败：'+e.message);}finally{setBusy(false);refresh();}};
 el('traffic-detail').addEventListener('close',function(){historySequence++;});
 function closeDrawers(){settings.close();details.close();historySequence++;}
 function schedule(){clearTimeout(timer);if(active())refresh();timer=setTimeout(schedule,2000);}
 document.querySelectorAll('[data-page]').forEach(function(b){b.addEventListener('click',function(){if(b.dataset.page==='traffic')refresh();else closeDrawers();});});
 window.addEventListener('hashchange',function(){if(active())refresh();else closeDrawers();});document.addEventListener('visibilitychange',function(){if(active())refresh();});window.addEventListener('resize',function(){if(active()){totals();if(historyData&&details.isOpen())Charts.render(el('traffic-history-chart'),M.points(historyData.points,true),Math.max(90000,(historyData.bucketSeconds||60)*2500));}});new MutationObserver(function(){if(active()){totals();if(historyData&&details.isOpen())Charts.render(el('traffic-history-chart'),M.points(historyData.points,true),Math.max(90000,(historyData.bucketSeconds||60)*2500));}}).observe(document.documentElement,{attributes:true,attributeFilter:['data-console-theme']});
 window.addEventListener('pagehide',function(){clearTimeout(timer);timer=null;epoch++;closeDrawers();});window.addEventListener('pageshow',function(){if(timer===null)schedule();});el('traffic-toggle').disabled=el('traffic-empty-start').disabled=true;totals();applications();schedule();
}());
