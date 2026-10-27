'use strict';
(function(){
 var api=window.AuroraAPI,timer=null,last=null,resetPending=false;
 function el(id){return document.getElementById(id);}
 function note(text){el('update-status').textContent=text;}
 function render(s){
  last=s;el('update-current').textContent='Aurora Launcher v'+s.currentVersion+(s.latest?' · 最新 v'+s.latest.version:'');
  el('update-auto-check').checked=s.autoCheck;el('update-automatic').checked=s.automatic;
  el('update-automatic').disabled=s.busy||!s.maintenanceAvailable;el('update-auto-check').disabled=s.busy;
  el('update-capability').textContent=s.maintenanceReason;
  el('update-previous').textContent=s.previous?'上一版本：v'+s.previous.version:'尚未保留上一版本，完成一次受管更新后可回退。';
  el('update-rollback').disabled=s.busy||!s.rollbackAvailable;el('update-clean').disabled=s.busy||!s.maintenanceAvailable;
  el('update-version-install').disabled=s.busy;el('update-upload').disabled=s.busy;el('update-check').disabled=s.busy;el('update-install').disabled=s.busy;
  el('update-progress').hidden=s.phase!=='downloading';el('update-progress').value=s.progress||0;
  note(s.message+(s.phase==='downloading'?' '+s.progress+'%':''));
  el('update-routes').textContent=(s.routes||[]).map(function(r){return r.name+'：'+(r.ok?r.ms+' ms / '+r.sampleBytes+' B':r.reason);}).join('\n');
 }
 async function refresh(){try{render(await api.request('updates'));}catch(e){if(resetPending&&e.code==='PATH_REJECTED'){location.replace('http://'+location.hostname+':8899/');return;}note(e.message);}}
 async function request(action,body){try{render(await api.request('updates/'+action,body||{}));}catch(e){note(e.message);}}
 el('update-version-install').onclick=function(){var version=el('update-version').value.trim();if(!version){note('请填写版本号');return;}if(confirm('下载指定版本 v'+version+'？新版/同版交给系统确认安装；低版本仅在维护权限可用时执行，并保留恢复包。'))request('version',{version:version,confirmed:true});};
 el('update-check').onclick=function(){request('check');};
 el('update-install').onclick=function(){if(confirm('更新将重新打开桌面，并保留现有设置。是否继续？'))request('online');};
 el('update-upload').onclick=function(){el('update-apk-file').click();};
 el('update-apk-file').onchange=async function(){
  var file=this.files[0];if(!file)return;
  el('update-upload').disabled=true;note('正在上传并校验安装包…');
  try{
   if(!/\.apk$/i.test(file.name))throw Error('请选择 APK 文件');
   var response=await fetch('/api/v1/updates/upload',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/vnd.android.package-archive'},body:file}),result=await response.json();
   if(!response.ok||!result.ok)throw Error(result.error&&result.error.message||'上传失败');
   var uploaded=result.data;
   if(confirm('已校验 Aurora Launcher v'+uploaded.version+'（'+(uploaded.bytes/1048576).toFixed(1)+' MB）。安装后保留设置，是否继续？'))await request('install-upload',{uploadToken:uploaded.uploadToken,confirmed:true});
   else note('已取消安装；电视版本未改变。');
  }catch(e){note(e.message);}finally{this.value='';el('update-upload').disabled=false;}
 };
 el('update-rollback').onclick=function(){if(confirm('将恢复上一版本及更新前的设置，更新后修改的设置会被覆盖。是否确认回退？'))request('rollback',{confirmed:true});};
 el('update-auto-check').onchange=function(){request('config',{autoCheck:this.checked});};
 el('update-automatic').onchange=function(){request('config',{automatic:this.checked});};
 el('update-clean').onclick=async function(){
  if(!confirm('严重警告：这会清空桌面账号密码、路径、设置、壁纸、屏保音乐及本应用数据，重新进入首次设置。不是普通更新。是否继续到第二次确认？'))return;
  try{var ticket=await api.request('updates/clean/confirm',{});var text=prompt('第二次确认：请输入“清空设置”。完成后需用电视当前 IP:8899 重新创建账号。U盘和其他应用不会删除。');if(text!=='清空设置'){note('已取消全新安装');return;}resetPending=true;await request('clean',{confirmationToken:ticket.confirmationToken,confirmationText:text});}catch(e){note(e.message);}
 };
 document.querySelector('[data-page="updates"]').addEventListener('click',function(){refresh();if(timer)clearInterval(timer);timer=setInterval(function(){if(!document.hidden&&!document.querySelector('[data-panel="updates"]').hidden)refresh();},2000);});
 document.addEventListener('visibilitychange',function(){if(!document.hidden&&location.hash==='#updates')refresh();});
 if(location.hash==='#updates')refresh();
})();
