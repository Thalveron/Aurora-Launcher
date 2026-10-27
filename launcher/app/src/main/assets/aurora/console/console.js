'use strict';
(function () {
  var api = window.AuroraAPI, statusTimer, toastTimer, watching = false, systemBackends = {};
  function el(id) {return document.getElementById(id);}
  function notice(message) {el('notice').textContent = message; el('notice').hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(function () {el('notice').hidden = true;}, 5000);}
  function stop() {el('screen').removeAttribute('src'); el('screen').hidden = true; el('screen-empty').hidden = false; watching = false;}
  function capabilities(data) {
    var box = el('capabilities'); box.replaceChildren();
    [['launch', '应用启动'], ['key', '遥控按键'], ['screenshot', '实时画面'], ['standby', '待机']].forEach(function (entry) {
      var capability = data[entry[0]], row = document.createElement('div'), description = document.createElement('div'), label = document.createElement('strong'), detail = document.createElement('small'), badge = document.createElement('span');
      row.className = 'capability'; label.textContent = entry[1];
      detail.textContent = capability.available ? capability.backend : capability.candidates.map(function (backend) {return backend.reason;}).filter(Boolean).join('；');
      description.append(label, detail); badge.className = 'badge' + (capability.available ? '' : ' off'); badge.textContent = capability.available ? '可用' : '未连接';
      var choose = document.createElement('select'), automatic = document.createElement('option'); automatic.value = 'auto'; automatic.textContent = '自动选择'; choose.append(automatic); choose.setAttribute('aria-label', entry[1] + '后端');
      capability.candidates.forEach(function (backend) {var option = document.createElement('option'); option.value = backend.backend; option.textContent = backend.backend + (backend.available ? '' : '（不可用）'); option.disabled = !backend.available; choose.append(option);});
      choose.value = capability.preferred; systemBackends[entry[0]] = capability.preferred;
      choose.onchange = async function () {try {systemBackends[entry[0]] = choose.value; await api.request('config', {system_backends: systemBackends}); refreshCapabilities();} catch (error) {notice(error.message);}};
      var actions = document.createElement('div'); actions.className = 'backend-choice'; actions.append(badge, choose); row.append(description, actions); box.append(row);
    });
    document.querySelectorAll('[data-key]').forEach(function (button) {button.disabled = !data.key.available; button.title = data.key.available ? '' : '请先连接本机ADB或启用辅助功能';});
    el('watch').disabled = !data.screenshot.available; el('standby').disabled = !data.standby.available;
    el('adb-status').textContent = data.adb.message; el('adb-connect').disabled = data.adb.pending;
    if (data.adb.pending) {clearTimeout(statusTimer); statusTimer = setTimeout(refreshCapabilities, 1000);}
  }
  async function idleStandby(){var control=el('idle-standby-enabled');if(!control)return;try{var state=await api.request('system/idle-standby');control.checked=state.enabled;control.disabled=!state.available&&!state.enabled;el('idle-standby-minutes').value=String(state.minutes);el('idle-standby-status').textContent=state.enabled?state.reason+' · 无操作约 '+Math.floor(state.idleSeconds/60)+' 分钟':state.available?'已关闭，开启后重新计时 '+state.minutes+' 分钟':'请先连接电视调试并启用主页辅助服务';}catch(e){el('idle-standby-status').textContent=e.message;}}
  async function saveIdle(){var control=el('idle-standby-enabled'),minutes=el('idle-standby-minutes');control.disabled=minutes.disabled=true;try{await api.request('system/idle-standby',{enabled:control.checked,minutes:Number(minutes.value)});notice(control.checked?'已开启'+minutes.value+'分钟无操作自动待机':'已关闭自动待机');}catch(e){notice(e.message);}finally{minutes.disabled=false;idleStandby();}}
  var idleControl=el('idle-standby-enabled');if(idleControl)idleControl.onchange=saveIdle;el('idle-standby-minutes').onchange=saveIdle;
  el('idle-standby-preview').onclick=async function(){try{await api.request('system/idle-standby/preview',{});notice('电视已显示一分钟预览，不会真的待机；按任意遥控键取消');}catch(e){notice(e.message);}};
  idleStandby();window.addEventListener('hashchange',function(){if(location.hash==='#device')idleStandby();});
  async function refreshCapabilities() {try {capabilities(await api.request('system/capabilities'));idleStandby();} catch (error) {notice(error.message);}}
  async function refresh() {
    try {
      var data = await api.request('bootstrap');
      el('connection').textContent = '电视在线 · 接口 v' + data.apiVersion;
      el('summary').textContent = data.time.time + ' · ' + data.time.date + ' · ' + data.weather.city + ' · 可启动应用 ' + data.apps.length + ' 个';
      el('media-status').textContent = data.media.available ? '播放会话：' + (data.media.title || '未命名歌曲') : '当前没有可读取的播放会话';
      el('legacy-console').href = '#legacy';
      await refreshCapabilities();
    } catch (error) {el('connection').textContent = '连接失败'; notice(error.message);}
  }
  el('adb-connect').onclick = async function () {try {await api.request('system/connect', {}); refreshCapabilities();} catch (error) {notice(error.message);}};
  el('open-theme').onclick = async function () {try {await api.request('themes/open', {}); notice('已在电视打开当前主题');} catch (error) {notice(error.message);}};
  el('watch').onclick = function () {if (watching) return; watching = true; el('screen').src = '/api/v1/system/screen.mjpeg?fps=' + el('fps').value; el('screen').hidden = false; el('screen-empty').hidden = true;};
  el('screen').onerror = function () {if (watching) {stop(); notice('画面连接已停止，请检查系统能力后手动重试'); refreshCapabilities();}};
  el('stop-watch').onclick = stop;
  el('fps').onchange = function () {if (watching) {stop(); el('watch').click();}};
  document.querySelectorAll('[data-key]').forEach(function (button) {button.onclick = async function () {try {await api.request('system/key', {key: button.dataset.key});} catch (error) {notice(error.message);}};});
  el('standby').onclick = async function () {
    try {var prepared = await api.request('system/standby/confirm', {}); if (!confirm('确认让电视进入待机？当前画面和操作将中断。')) return;
      await api.request('system/standby', {confirmationToken: prepared.confirmationToken}); stop(); notice('待机指令已提交');
    } catch (error) {notice(error.message);}
  };
  document.addEventListener('visibilitychange', function () {if (document.hidden) stop();});
  window.addEventListener('pagehide', function () {stop(); clearTimeout(statusTimer);});
  el('logout').onclick=async function(){await api.request('auth/logout',{});location.replace('/console/login.html');};
  el('reload').onclick = refresh; refresh();
}());
