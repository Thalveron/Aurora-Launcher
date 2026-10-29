'use strict';
(function () {
  var api = window.AuroraAPI, statusTimer, toastTimer, watching = false, systemBackends = {};
  function el(id) {return document.getElementById(id);}
  function notice(message) {el('notice').textContent = message; el('notice').hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(function () {el('notice').hidden = true;}, 5000);}
  function stop() {el('screen').removeAttribute('src'); el('screen').hidden = true; el('screen-empty').hidden = false; watching = false;el('watch').textContent='开始观看';el('watch').setAttribute('aria-pressed','false');}
  function capabilities(data) {
    var box = el('capabilities'); box.replaceChildren();
    [['launch', '应用启动'], ['key', '遥控按键'], ['screenshot', '实时画面'], ['standby', '待机']].forEach(function (entry) {
      var capability = data[entry[0]], row = document.createElement('div'), description = document.createElement('div'), label = document.createElement('strong'), detail = document.createElement('small'), badge = document.createElement('span');
      row.className = 'capability'; label.textContent = entry[1];
      detail.textContent = capability.available ? backendName(capability.backend) : (capability.candidates||[]).map(function (backend) {return backend.reason;}).filter(Boolean).join('；');
      description.append(label, detail); badge.className = 'ui-badge state-badge ' + (capability.available ? 'connected' : 'disconnected'); badge.textContent = capability.available ? '已连接' : '未连接';
      var choose = document.createElement('select'), automatic = document.createElement('option'); automatic.value = 'auto'; automatic.textContent = '自动选择'; choose.append(automatic); choose.setAttribute('aria-label', entry[1] + '后端');
      (capability.candidates||[]).forEach(function (backend) {var option = document.createElement('option'); option.value = backend.backend; option.textContent = backendName(backend.backend) + (backend.available ? '' : '（不可用）'); option.disabled = !backend.available; choose.append(option);});
      choose.disabled=api.mock;choose.title=api.mock?'电脑模拟无法更改电视控制方式':'';choose.value = capability.preferred; systemBackends[entry[0]] = capability.preferred;
      choose.onchange = async function () {try {systemBackends[entry[0]] = choose.value; await api.request('config', {system_backends: systemBackends}); refreshCapabilities();} catch (error) {notice(error.message);}};
      var actions = document.createElement('div'); actions.className = 'backend-choice'; actions.append(badge, choose); row.append(description, actions); box.append(row);
    });
    document.querySelectorAll('[data-key]').forEach(function (button) {button.disabled = !data.key.available; button.title = data.key.available ? '' : '请先连接本机ADB或启用辅助功能';});
    ['watch','screen-save','screen-fullscreen'].forEach(function(id){el(id).disabled=!data.screenshot.available;el(id).title=data.screenshot.available?'':(api.mock?'电脑模拟无法读取真实画面':'请先连接支持截图的电视控制方式');});el('standby').disabled=!data.standby.available;el('standby').title=data.standby.available?'':'当前没有可用的待机控制方式';
    el('adb-status').textContent = data.adb.message;var connectionBadge=el('device-connect-badge');if(connectionBadge){connectionBadge.className='ui-badge state-badge '+(data.adb.connected?'connected':'disconnected');connectionBadge.textContent=data.adb.connected?'已连接':data.adb.pending?'等待授权':'未连接';} if(!data.screenshot.available&&watching)stop();el('fps').disabled=api.mock||!data.screenshot.available;el('fps').title=el('fps').disabled?'当前无法读取电视画面':'';el('adb-connect').disabled = api.mock||data.adb.pending;el('adb-connect').title=api.mock?'电脑模拟不连接电视':data.adb.pending?'请先完成电视端授权':'';el('open-theme').disabled=api.mock;el('open-theme').title=api.mock?'电脑模拟不能操作电视':'';
    if (data.adb.pending) {clearTimeout(statusTimer); statusTimer = setTimeout(refreshCapabilities, 1000);}
  }
  async function idleStandby(){var control=el('idle-standby-enabled');if(!control)return;try{var state=await api.request('system/idle-standby');control.checked=state.enabled;control.disabled=api.mock||(!state.available&&!state.enabled);el('idle-standby-minutes').disabled=api.mock||!state.available;el('idle-standby-minutes').value=String(state.minutes);el('idle-standby-status').textContent=state.enabled?state.reason+' · 无操作约 '+Math.floor(state.idleSeconds/60)+' 分钟':!api.mock&&state.available?'已关闭，开启后重新计时 '+state.minutes+' 分钟':'请先连接电视调试并启用主页辅助服务';}catch(e){el('idle-standby-status').textContent=e.message;}}
  async function saveIdle(){var control=el('idle-standby-enabled'),minutes=el('idle-standby-minutes');control.disabled=minutes.disabled=true;try{await api.request('system/idle-standby',{enabled:control.checked,minutes:Number(minutes.value)});notice(control.checked?'已开启'+minutes.value+'分钟无操作自动待机':'已关闭自动待机');}catch(e){notice(e.message);}finally{minutes.disabled=false;idleStandby();}}
  function backendName(name){return ({AdbLoopbackBackend:'本机调试',PrivilegedBackend:'系统权限',AccessibilityBackend:'辅助功能',adb:'本机调试',privileged:'系统权限',accessibility:'辅助功能'})[name]||'系统控制';}
  var idleControl=el('idle-standby-enabled');if(idleControl)idleControl.onchange=saveIdle;el('idle-standby-minutes').onchange=saveIdle;
  el('idle-standby-preview').disabled=api.mock;el('idle-standby-preview').title=api.mock?'电脑模拟无法显示电视待机倒计时':'';
  el('idle-standby-preview').onclick=async function(){try{await api.request('system/idle-standby/preview',{});notice('电视已显示一分钟预览，不会真的待机；按任意遥控键取消');}catch(e){notice(e.message);}};
  idleStandby();window.addEventListener('hashchange',function(){if(location.hash==='#device')idleStandby();});
  async function refreshCapabilities() {try {capabilities(await api.request('system/capabilities'));idleStandby();} catch (error) {var badge=el('device-connect-badge');if(badge){badge.className='ui-badge state-badge error';badge.textContent='异常';}notice(error.message);}}
  async function refresh() {
    try {
      var data = await api.request('bootstrap');
      el('connection').textContent = '电视在线 · 接口 v' + data.apiVersion;
      el('summary').textContent = data.time.time + ' · ' + data.time.date + ' · ' + data.weather.city + ' · 可启动应用 ' + data.apps.length + ' 个';
      el('media-status').textContent = data.media.available ? '播放会话：' + (data.media.title || '未命名歌曲') : '当前没有可读取的播放会话';
      el('legacy-console').href = '#legacy';
      try{var device=await api.request('status'),bytes=function(v){return typeof v==='number'?(v/1073741824).toFixed(1)+' GB':'未知';};el('device-state-detail').textContent=['存储：可用 '+bytes(device.storage&&device.storage.free)+' / 共 '+bytes(device.storage&&device.storage.total),'内存：可用 '+bytes(device.memory&&device.memory.available)+' / 共 '+bytes(device.memory&&device.memory.total),'网络：'+(device.network&&device.network.connected?'已连接':'未连接'),'蓝牙：'+(device.bluetooth&&device.bluetooth.enabled?'已开启':'未开启')].join('\n');}catch(e){el('device-state-detail').textContent='设备状态暂不可读：'+e.message;}
      await refreshCapabilities();
    } catch (error) {el('connection').textContent = '连接失败'; notice(error.message);}
  }
  el('adb-connect').onclick = async function () {try {await api.request('system/connect', {}); refreshCapabilities();} catch (error) {notice(error.message);}};
  el('open-theme').onclick = async function () {try {await api.request('themes/open', {}); notice('已在电视打开当前主题');} catch (error) {notice(error.message);}};
  el('watch').onclick = function () {if(watching){stop();return;}if(this.disabled)return;watching = true;this.textContent='停止观看';this.setAttribute('aria-pressed','true'); el('screen').src = '/api/v1/system/screen.mjpeg?fps=' + el('fps').value; el('screen').hidden = false; el('screen-empty').hidden = true;};
  el('screen').onerror = function () {if (watching) {stop(); notice('画面连接已停止，请检查系统能力后手动重试'); refreshCapabilities();}};
  el('stop-watch').onclick = stop;
  el('screen-save').onclick=async function(){var b=this;b.disabled=true;try{var image=await api.request('system/screenshots/capture',{resolution:'native'}),a=document.createElement('a');a.href=image.url+'?download=1';a.download=image.id+'.png';a.click();notice('截图已保存，可在桌面预览中查看或删除');}catch(e){notice(e.message);}finally{b.disabled=el('watch').disabled;}};
  el('screen-fullscreen').onclick=function(){var surface=el('screen').parentElement;if(!watching){notice('请先开始观看');return;}if(surface.requestFullscreen)surface.requestFullscreen().catch(function(){notice('浏览器不支持此全屏操作');});else notice('浏览器不支持全屏');};
  window.addEventListener('aurora-device-tab',function(e){if(e.detail!==1)stop();});
  window.addEventListener('hashchange',function(){if(location.hash!=='#device')stop();});
  el('fps').onchange = function () {if (watching) {stop(); el('watch').click();}};
  var keyBusy=false;
  document.querySelectorAll('[data-key]').forEach(function(button){button.onclick=async function(){if(button.disabled||keyBusy)return;keyBusy=true;button.classList.add('pressed');try{await api.request('system/key',{key:button.dataset.key});}catch(error){notice(error.message);}finally{keyBusy=false;setTimeout(function(){button.classList.remove('pressed');},140);}};});
  document.addEventListener('keydown',function(e){if(location.hash!=='#device'||document.querySelector('[data-panel="device"]').dataset.deviceTab!=='1'||e.altKey||e.ctrlKey||e.metaKey||e.repeat||e.target.closest('input,textarea,select,[contenteditable=true],[role=tab],dialog,a,button:not([data-key])'))return;var key=({ArrowUp:'up',ArrowDown:'down',ArrowLeft:'left',ArrowRight:'right',Enter:'ok'})[e.key];if(!key)return;e.preventDefault();var b=document.querySelector('[data-key="'+key+'"]');if(b&&!b.disabled)b.click();});
  el('standby').onclick = async function () {
    try {var prepared = await api.request('system/standby/confirm', {}); if (!await window.AuroraDrawer.confirm({title:'让电视进入待机？',message:'当前画面和操作将中断，需要使用电视遥控器唤醒。',confirmLabel:'确认待机'})) return;
      await api.request('system/standby', {confirmationToken: prepared.confirmationToken}); stop(); notice('待机指令已提交');
    } catch (error) {notice(error.message);}
  };
  document.addEventListener('visibilitychange', function () {if (document.hidden) stop();});
  window.addEventListener('pagehide', function () {stop(); clearTimeout(statusTimer);});
  el('logout').onclick=async function(){await api.request('auth/logout',{});location.replace('/console/login.html');};
  el('reload').onclick = refresh; refresh();
}());
