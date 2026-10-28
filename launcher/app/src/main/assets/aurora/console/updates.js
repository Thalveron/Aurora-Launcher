'use strict';
(function(){
 var api=window.AuroraAPI,timer=null,last=null,resetPending=false,readyPrompted=false,uploading=false;
 function el(id){return document.getElementById(id);}
 function note(text){el('update-status').textContent=text;}
 function render(s){
  last=s;if(s.phase!=='ready')readyPrompted=false;if(s.phase==='ready'&&!readyPrompted){readyPrompted=true;setTimeout(function(){if(confirm('新版已下载并通过校验，现在安装吗？'))request('install');else request('cancel');},0);}el('update-current').textContent='Aurora Launcher v'+s.currentVersion+(s.latest?' · 最新 v'+s.latest.version:'');
  el('update-auto-check').checked=s.autoCheck;el('update-automatic').checked=s.automatic;
  el('update-automatic').disabled=s.busy||!s.maintenanceAvailable;el('update-auto-check').disabled=s.busy;
  el('update-capability').textContent=s.maintenanceReason;
  el('update-previous').textContent=s.previous?'上一版本：v'+s.previous.version:'尚未保留上一版本，完成一次受管更新后可回退。';
  el('update-rollback').disabled=s.busy||!s.rollbackAvailable;el('update-clean').disabled=s.busy||!s.maintenanceAvailable;
  el('update-check').disabled=s.busy||uploading;el('update-install').disabled=s.busy||uploading;el('update-upload').disabled=s.busy||uploading;
  el('update-progress').hidden=s.phase!=='downloading';el('update-progress').value=s.progress||0;
  note(s.message+(s.phase==='downloading'?' '+s.progress+'%':''));
  el('update-routes').textContent=(s.routes||[]).map(function(r){return r.name+'：'+(r.ok?r.ms+' ms / '+r.sampleBytes+' B':r.reason);}).join('\n');
 }
 async function refresh(){try{render(await api.request('updates'));}catch(e){if(resetPending&&e.code==='PATH_REJECTED'){location.replace('http://'+location.hostname+':8899/');return;}note(e.message);}}
 async function request(action,body){if(!timer)timer=setInterval(function(){if(!document.hidden&&!document.querySelector('[data-panel="updates"]').hidden)refresh();},2000);try{render(await api.request('updates/'+action,body||{}));}catch(e){note(e.message);}}
 el('update-check').onclick=function(){request('download');};
 el('update-install').onclick=function(){if(last&&last.phase==='ready'){if(confirm('安装已下载的新版本？'))request('install');else request('cancel');}else request('download');};
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
 el('update-auto-check').onchange=function(){request('config',{autoCheck:this.checked});};
 el('update-automatic').onchange=function(){request('config',{automatic:this.checked});};
 el('update-clean').onclick=async function(){
  if(!confirm('严重警告：这会清空桌面账号密码、路径、设置、壁纸、屏保音乐及本应用数据并重新安装。U盘和其他应用不受影响。确认继续？'))return;
  try{var ticket=await api.request('updates/clean/confirm',{});resetPending=true;await request('clean',{confirmationToken:ticket.confirmationToken,confirmationText:''});}catch(e){note(e.message);}
 };
 document.querySelector('[data-page="updates"]').addEventListener('click',function(){refresh();if(timer)clearInterval(timer);timer=setInterval(function(){if(!document.hidden&&!document.querySelector('[data-panel="updates"]').hidden)refresh();},2000);});
 document.addEventListener('visibilitychange',function(){if(!document.hidden&&location.hash==='#updates')refresh();});
 if(location.hash==='#updates')refresh();
})();
