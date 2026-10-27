'use strict';
(function(){
 var api=window.AuroraAPI,data={},selected=null,timer,busy=false,lastSample=0,historyData=null;
 function el(id){return document.getElementById(id);}if(!el('traffic-panel'))return;
 function bytes(n){if(n==null||!isFinite(n))return '—';var u=['B','KB','MB','GB','TB'],i=0;while(n>=1024&&i<u.length-1){n/=1024;i++;}return n.toFixed(i?1:0)+' '+u[i];}
 function rate(n){return n==null?'等待采样':bytes(n)+'/s';}
 function date(n){return n?new Date(n).toLocaleString():'暂无';}
 function node(tag,text,cls){var n=document.createElement(tag);if(text!=null)n.textContent=text;if(cls)n.className=cls;return n;}
 function status(text){el('traffic-status').textContent=text;}
 function download(value){var blob=new Blob([JSON.stringify(value,null,2)],{type:'application/json'}),url=URL.createObjectURL(blob),a=node('a');a.href=url;a.download='Aurora-流量监控-'+Date.now()+'.json';a.click();setTimeout(function(){URL.revokeObjectURL(url);},1000);}
 function chart(canvas,points,historical){
  var width=Math.max(300,canvas.clientWidth),height=180,dpr=Math.min(2,window.devicePixelRatio||1);canvas.width=width*dpr;canvas.height=height*dpr;var ctx=canvas.getContext('2d');ctx.scale(dpr,dpr);ctx.clearRect(0,0,width,height);ctx.font='12px sans-serif';ctx.fillStyle='#849cae';
  if(!points.length){ctx.fillText('还没有采样记录',20,45);return;}
  var a=points.map(function(p){return historical?{x:p.time,r:p.rxBytes/(p.seconds||60),t:p.txBytes/(p.seconds||60)}:{x:p.time,r:p.rxRate,t:p.txRate};});
  var max=1024;a.forEach(function(p){max=Math.max(max,p.r,p.t);});var start=a[0].x,end=a[a.length-1].x;if(end===start)end=start+1000;
  ctx.strokeStyle='rgba(128,156,174,.2)';for(var i=0;i<=3;i++){var y=20+i*42;ctx.beginPath();ctx.moveTo(70,y);ctx.lineTo(width-12,y);ctx.stroke();ctx.fillText(rate(max*(3-i)/3),3,y+4);}
  [['r','#21c9ca'],['t','#a18bff']].forEach(function(v){ctx.strokeStyle=v[1];ctx.lineWidth=2;ctx.beginPath();a.forEach(function(p,i){var x=70+(p.x-start)/(end-start)*(width-85),y=146-(p[v[0]]/max)*126;if(i)ctx.lineTo(x,y);else ctx.moveTo(x,y);});ctx.stroke();});
  ctx.fillText(new Date(start).toLocaleTimeString(),70,174);ctx.textAlign='right';ctx.fillText(new Date(end).toLocaleTimeString(),width-12,174);ctx.textAlign='left';
 }
 function totals(){
  var box=el('traffic-totals');box.replaceChildren();var total=data.total||{};
  [['实时下载',rate(total.rxRate)],['实时上传',rate(total.txRate)],['网卡累计下载',bytes(total.rxBytes)],['网卡累计上传',bytes(total.txBytes)]].forEach(function(v){var card=node('div',null,'traffic-stat');card.append(node('small',v[0]),node('strong',v[1]));box.append(card);});
  var nets=el('traffic-interfaces');nets.replaceChildren();(data.interfaces||[]).forEach(function(n){nets.append(node('span',n.name+'　↓ '+rate(n.rxRate)+'　↑ '+rate(n.txRate),'traffic-interface'));});chart(el('traffic-chart'),data.series||[],false);
 }
 function applications(){
  var filter=el('traffic-filter').value.trim().toLowerCase(),only=el('traffic-active').checked,sort=el('traffic-sort').value,body=el('traffic-apps');body.replaceChildren();
  var items=(data.apps||[]).filter(function(a){return (!only||a.rxRate>0||a.txRate>0||a.connections>0)&&(!filter||(a.name+' '+a.uid+' '+(a.packages||[]).map(function(p){return p.package+' '+p.name;}).join(' ')).toLowerCase().indexOf(filter)>=0);}).sort(function(a,b){return (b[sort]||0)-(a[sort]||0)||b.txBytes-a.txBytes||a.uid-b.uid;});
  items.forEach(function(a){var row=node('tr'),name=node('td'),button=node('button',a.name,'traffic-app-link');button.onclick=function(){selected=a.uid;detail();loadHistory();};name.append(button,node('small','UID '+a.uid+(a.sharedUid?' · 共享 UID':'')));row.append(name,node('td',rate(a.rxRate)),node('td',rate(a.txRate)),node('td',bytes(a.sessionRx)+' / '+bytes(a.sessionTx)),node('td',bytes(a.rxBytes)+' / '+bytes(a.txBytes)),node('td',String(a.connections)+' / 公网 '+a.publicConnections+' 条 · '+(a.publicIpCount||0)+' 个 IP'),node('td',a.uploadHint||'—',a.uploadHint?'traffic-hint':''));body.append(row);});
  if(!items.length){var row=node('tr'),cell=node('td',data.hasSample?'没有符合筛选条件的应用':'开启监控并等待两次采样后显示真实速度');cell.colSpan=7;row.append(cell);body.append(row);}
 }
 function detail(){
  if(selected===null)return;el('traffic-detail').hidden=false;var app=(data.apps||[]).filter(function(a){return a.uid===selected;})[0];
  el('traffic-detail-title').textContent=selected===-1?'网卡总历史与全部连接':app?app.name+' · UID '+selected:'UID '+selected;
  var packages=el('traffic-packages');packages.replaceChildren();if(app)(app.packages||[]).forEach(function(p){packages.append(node('div',p.name+'　'+p.package+'　'+(p.system?'系统应用':'用户应用')+(p.enabled?'':' · 已停用'),'traffic-package'));});
  el('traffic-detail-stats').textContent=app?'峰值下载 '+rate(app.peakRx)+' · 峰值上传 '+rate(app.peakTx)+' · 最近流量活动 '+date(app.lastActiveAt)+' · 连续上传 ≥32 KB/s：'+app.sustainedUploadSeconds+' 秒'+(app.sharedUid?'。这些组件共用 UID，只能合计统计。':''):'连接快照只反映采样时刻，不是抓包日志。';
  el('traffic-detail-stats').textContent+=' 公网连接优先显示；PTR 是反向解析名称，不等于应用原始请求域名。连接持续时间从本次连续观测计算，不是实际建立时间；共享 UID 无法准确拆分到单独进程。';var body=el('traffic-connections');body.replaceChildren();var links=(data.connections||[]).filter(function(c){return selected===-1||c.uid===selected;}).sort(function(a,b){return (a.scope==='public'?-1:0)-(b.scope==='public'?-1:0)||a.remote.localeCompare(b.remote);});
  var scopes={public:'公网',lan:'局域网',loopback:'本机回环',any:'监听/未指定',multicast:'组播',unknown:'未知'};
  links.forEach(function(c){var row=node('tr');row.append(node('td',c.protocol.toUpperCase()+' / '+c.state+(selected===-1?' · UID '+c.uid:'')),node('td',(c.local.indexOf(':')>=0?'['+c.local+']':c.local)+':'+c.localPort),node('td',(c.remote.indexOf(':')>=0?'['+c.remote+']':c.remote)+':'+c.remotePort),node('td',(scopes[c.scope]||c.scope)+' · '+(c.reverseName?'PTR：'+c.reverseName:data.resolveDomains?'无可用 PTR / 查询中':'域名解析未开启')+' · 首次 '+date(c.firstSeenAt)+' · 持续观测 '+(c.observedSeconds||0)+' 秒'),node('td',bytes(c.txQueueBytes)+' / '+bytes(c.rxQueueBytes)));body.append(row);});
  if(!links.length){var row=node('tr'),td=node('td','采样时没有可见连接；短连接可能发生在两次采样之间。');td.colSpan=5;row.append(td);body.append(row);}
 }
 async function loadHistory(){if(selected===null)return;try{historyData=await api.request('network/traffic/history?uid='+selected+'&hours='+el('traffic-hours').value);chart(el('traffic-history-chart'),historyData.points||[],true);el('traffic-history-note').textContent=(historyData.note||'')+' 分钟记录约每60秒落盘，图中为时间桶平均速度。';}catch(e){el('traffic-history-note').textContent=e.message;}}
 async function refresh(){
  if(busy)return;busy=true;
  try{
   data=await api.request('network/traffic');el('traffic-toggle').textContent=data.enabled?'暂停采集':'开启监控';el('traffic-interval').value=String(data.intervalSeconds||5);el('traffic-store').checked=data.historyEnabled!==false;el('traffic-retention').value=String(data.retentionDays||7);el('traffic-dns').checked=!!data.resolveDomains;
   el('traffic-badge').textContent=data.error?'采集异常':data.enabled?'正在采集':'已暂停';el('traffic-badge').className='badge'+(data.error?' off':'');
   status((data.error?data.error+'；上次有效数据仍保留。':data.hasSample?'最后采样 '+date(data.sampleAt)+' · 采样耗时 '+data.sampleDurationMs+' ms · '+(data.rateReady?'速度已就绪':'等待下一次有效采样'):'开启后需要两次有效采样才能计算速度。')+(data.sampleAgeMs>15000?' · 数据可能已过期':'')+' 历史保留 '+(data.retentionDays||7)+' 天；每小时自动清理，上次 '+date(data.lastAutoCleanupAt)+'。关闭网页后按开关状态继续采集。');
   totals();applications();detail();if(data.sampleAt!==lastSample){lastSample=data.sampleAt;if(selected!==null&&Date.now()%60000<6000)loadHistory();}
  }catch(e){status(e.message);}finally{busy=false;}
 }
 async function config(patch){try{await api.request('network/traffic/config',patch);await refresh();}catch(e){status(e.message);}}
 el('traffic-toggle').onclick=function(){config({enabled:!data.enabled});};
 el('traffic-interval').onchange=function(){config({intervalSeconds:Number(this.value)});};
 el('traffic-store').onchange=function(){config({historyEnabled:this.checked});};
 el('traffic-retention').onchange=function(){config({retentionDays:Number(this.value)});};el('traffic-dns').onchange=function(){config({resolveDomains:this.checked});};el('traffic-refresh').onclick=refresh;el('traffic-sort').onchange=applications;el('traffic-active').onchange=applications;el('traffic-filter').oninput=applications;
 el('traffic-hours').onchange=loadHistory;el('traffic-all-history').onclick=function(){selected=-1;detail();loadHistory();};
 el('traffic-export').onclick=async function(){try{var snapshot=await api.request('network/traffic/export',{});download({exportedAt:new Date().toISOString(),snapshot:snapshot,history:historyData});}catch(e){status(e.message);}};
 el('traffic-clear').onclick=async function(){if(!confirm('清空采集历史、曲线和采集期间统计？不会重置系统网络计数。'))return;try{await api.request('network/traffic/clear',{confirm:true});status('已提交清空，请等待下一次采样');setTimeout(refresh,1000);}catch(e){status(e.message);}};
 function active(){return location.hash==='#traffic'&&!document.hidden;}
 function schedule(){clearTimeout(timer);if(active())refresh();timer=setTimeout(schedule,2000);}
 window.addEventListener('hashchange',function(){if(active())refresh();});window.addEventListener('resize',function(){if(active()){chart(el('traffic-chart'),data.series||[],false);if(historyData)chart(el('traffic-history-chart'),historyData.points||[],true);}});window.addEventListener('pagehide',function(){clearTimeout(timer);});schedule();
}());
