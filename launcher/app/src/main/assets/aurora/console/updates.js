'use strict';
(function(){
 var api=window.AuroraAPI,UI=window.AuroraConsoleUI,timer=null,last=null,draft=null,resetPending=false,readyPrompted=false,uploading=false,saving=false,policyRevision=0;
 function el(id){return document.getElementById(id);}
 function note(text){el('update-status').textContent=text;}
 function render(s){
  last=s;if(s.phase!=='ready')readyPrompted=false;if(s.phase==='ready'&&!readyPrompted){readyPrompted=true;setTimeout(function(){if(confirm('新版已下载并通过校验，现在安装吗？'))request('install');else request('cancel');},0);}el('update-current').textContent='Aurora Launcher v'+s.currentVersion+(s.latest?' · 最新 v'+s.latest.version:'');
  var settings={autoCheck:!!s.autoCheck,automatic:!!s.automatic};
  if(UI&&!draft)draft=UI.draft('updates-policy',{label:'桌面更新',initial:settings,render:renderSettings,save:async function(value){saving=true;policyRevision++;try{return await api.request('updates/config',value);}finally{saving=false;policyRevision++;}},saved:function(accepted){draft.replace({autoCheck:!!accepted.autoCheck,automatic:!!accepted.automatic});note('更新设置已保存并回读确认');}});
  if(draft){if(!saving)draft.replace(settings);renderSettings(draft.value);}else renderSettings(settings);
  el('update-automatic').disabled=saving||!s.maintenanceAvailable;el('update-auto-check').disabled=saving;
  el('update-automatic').title=s.maintenanceAvailable?'桌面空闲时自动安装':s.maintenanceReason;
  el('update-capability').textContent=s.maintenanceReason;
  el('update-previous').textContent=s.previous?'上一版本：v'+s.previous.version:'尚未保留上一版本，完成一次受管更新后可回退。';
  el('update-rollback').disabled=s.busy||!s.rollbackAvailable;el('update-clean').disabled=s.busy||!s.maintenanceAvailable;
  el('update-check').disabled=s.busy||uploading;el('update-install').disabled=s.busy||uploading||!(s.newVersion||s.phase==='ready'||s.phase==='install_permission'||s.hasPendingInstaller);el('update-install').textContent=s.hasPendingInstaller?'继续安装':s.phase==='ready'?'安装已下载新版':'下载新版';el('update-upload').disabled=s.busy||uploading;
  el('update-progress').hidden=s.phase!=='downloading';el('update-progress').value=s.progress||0;
  note(s.message+(s.phase==='downloading'?' '+s.progress+'%':''));
  el('update-routes').textContent='更新来源（自动测速，失败切换备用）：\n'+(s.sources||[]).join('\n')+'\n'+(s.routes||[]).map(function(r){return r.name+'：'+(r.ok?r.ms+' ms / '+r.sampleBytes+' B':r.reason);}).join('\n');
 }
 function renderSettings(value){el('update-auto-check').checked=value.autoCheck;el('update-automatic').checked=value.automatic;}
 function stage(key,value){var next=draft?Object.assign({},draft.value):{autoCheck:!!last.autoCheck,automatic:!!last.automatic};next[key]=value;if(key==='autoCheck'&&!value)next.automatic=false;if(key==='automatic'&&value)next.autoCheck=true;if(draft)draft.set(next);else request('config',next);}
 async function refresh(){var revision=policyRevision;try{var next=await api.request('updates');if(revision===policyRevision&&!saving)render(next);}catch(e){if(resetPending&&e.code==='PATH_REJECTED'){location.replace('http://'+location.hostname+':8899/');return;}note(e.message);}}
 async function request(action,body){if(!timer)timer=setInterval(function(){if(!document.hidden&&!document.querySelector('[data-panel="updates"]').hidden)refresh();},2000);try{render(await api.request('updates/'+action,body||{}));}catch(e){note(e.message);}}
 el('update-check').onclick=function(){request('check');};
 el('update-install').onclick=function(){if(last&&last.phase==='ready'){if(confirm('安装已下载的新版本？'))request('install');else request('cancel');}else if(last&&(last.phase==='install_permission'||last.hasPendingInstaller))request('install');else request('download');};
 el('update-upload').onclick=function(){el('update-apk-file').click();};
 function uploadApk(file){return new Promise(function(resolve,reject){
  var xhr=new XMLHttpRequest();xhr.open('POST','/api/v1/updates/upload');xhr.withCredentials=true;xhr.timeout=1800000;xhr.setRequestHeader('Content-Type','application/vnd.android.package-archive');
  xhr.upload.onprogress=function(event){if(event.lengthComputable){var percent=Math.round(event.loaded/event.total*100);el('update-upload-progress').value=percent;el('update-upload-status').textContent='上传 '+percent+'% · 上传完成后校验包名和签名';}};
  xhr.onload=function(){try{var result=JSON.parse(xhr.responseText);if(xhr.status<200||xhr.status>=300||!result.ok)throw Error(result.error&&result.error.message||'上传失败');resolve(result.data);}catch(e){reject(e);}};
  xhr.onerror=function(){reject(Error('上传连接中断，请检查网络后重新上传'));};xhr.ontimeout=function(){reject(Error('上传超时，请重新上传'));};xhr.onabort=function(){reject(Error('上传已取消'));};xhr.send(file);
 });}
 el('update-apk-file').onchange=async function(){
  var file=this.files&&this.files[0];if(!file)return;
  if(!/\.apk$/i.test(file.name)||file.size<1024||file.size>512*1048576){el('update-upload-status').textContent='请选择 1KB～512MB 的 APK 文件';this.value='';return;}
  uploading=true;el('update-upload').disabled=true;el('update-upload-progress').hidden=false;el('update-upload-progress').value=0;el('update-upload-status').textContent='正在上传并校验安装包…';
  try{var uploaded=await uploadApk(file);el('update-upload-status').textContent='已校验 Aurora Launcher v'+uploaded.version+' · '+(uploaded.bytes/1048576).toFixed(1)+' MB · SHA-256 '+uploaded.sha256;
   if(confirm('安装 Aurora Launcher v'+uploaded.version+'？保留当前账号、设置、背景和音乐，不清空数据。'))await request('install-upload',{uploadToken:uploaded.uploadToken,confirmed:true});
   else{await request('cancel');el('update-upload-status').textContent='已取消安装，当前桌面不变';}
  }catch(e){el('update-upload-status').textContent=e.message;}finally{uploading=false;this.value='';el('update-upload-progress').hidden=true;await refresh();}
 };
 el('update-rollback').onclick=function(){if(confirm('将恢复上一版本及更新前的设置，更新后修改的设置会被覆盖。是否确认回退？'))request('rollback',{confirmed:true});};
 el('update-auto-check').onchange=function(){stage('autoCheck',this.checked);};
 el('update-automatic').onchange=function(){stage('automatic',this.checked);};
 el('update-clean').onclick=async function(){
  if(!confirm('严重警告：这会清空桌面账号密码、路径、设置、壁纸、屏保音乐及本应用数据并重新安装。U盘和其他应用不受影响。确认继续？'))return;
  try{var ticket=await api.request('updates/clean/confirm',{});resetPending=true;await request('clean',{confirmationToken:ticket.confirmationToken,confirmationText:''});}catch(e){note(e.message);}
 };
 document.querySelector('[data-page="updates"]').addEventListener('click',function(){refresh();if(timer)clearInterval(timer);timer=setInterval(function(){if(!document.hidden&&!document.querySelector('[data-panel="updates"]').hidden)refresh();},2000);});
 document.addEventListener('visibilitychange',function(){if(!document.hidden&&location.hash==='#updates')refresh();});
 refresh();if(!timer)timer=setInterval(function(){if(!document.hidden&&!document.querySelector('[data-panel="updates"]').hidden&&!saving)refresh();},2000);
})();
