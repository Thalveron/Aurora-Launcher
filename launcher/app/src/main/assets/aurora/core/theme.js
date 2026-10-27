'use strict';
(async function () {
  var api = window.AuroraAPI, state, appSignature = '', media = {}, toastTimer;
  function text(id, value) {document.getElementById(id).textContent = value == null ? '' : value;}
  function toast(message) {text('notice', message); clearTimeout(toastTimer); document.getElementById('notice').hidden = false; toastTimer = setTimeout(function () {document.getElementById('notice').hidden = true;}, 4000);}
  function time(value) {if(window.AuroraPresentation)window.AuroraPresentation.update({time:value});if (!value) return; text('clock', value.time); text('date', value.date + ' ' + value.weekday); text('lunar', value.lunar);}
  function renderWeather(value) {
    if(window.AuroraPresentation)window.AuroraPresentation.update({weather:value});
    text('city', (value.city || '天气')+(value.stale&&value.fetchedAt?' · 缓存 '+new Date(value.fetchedAt).toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'}):''));
    if (!value.available) {text('weather', value.reason || '天气暂不可用'); return;}
    var current = value.current, daily = value.daily;
    text('weather', current && current.temperature_2m != null ? Math.round(current.temperature_2m) + '°C' : '天气数据已更新');
    var box = document.getElementById('forecast'); box.replaceChildren();
    if (daily && daily.time) daily.time.slice(0, 4).forEach(function (date, index) {
      var item = document.createElement('div'), label = document.createElement('small'), temperature = document.createElement('strong');
      label.textContent = ['今天', '明天', '后天', '第四天'][index];
      temperature.textContent = Math.round(daily.temperature_2m_min[index]) + '～' + Math.round(daily.temperature_2m_max[index]) + '°';
      item.append(label, temperature); box.append(item);
    });
  }
  function renderMedia(value) {
    if (!value) return; media = value;if(window.AuroraPresentation)window.AuroraPresentation.update({media:value});
    text('song', value.available ? value.title || '未命名歌曲' : '还没有正在播放的音乐');
    text('artist', value.available ? value.artist || '' : value.reason || '打开播放器后，这里会显示播放状态');
    text('lyric', value.lyrics && value.lyrics.length ? value.lyrics[Math.max(0, Math.min(value.lyrics.length - 1, value.lyricIndex))] : '播放器没有提供歌词时，此处不显示模拟歌词');
    document.getElementById('progress').value = value.durationMs ? Math.min(100, value.positionMs / value.durationMs * 100) : 0;
    var controls = value.controls || {};
    ['prev', 'play', 'next'].forEach(function (action) {var button = document.getElementById(action); button.disabled = !controls[action === 'play' && value.playing ? 'pause' : action];});
    document.getElementById('play').setAttribute('aria-label',value.playing?'暂停':'播放');icon('play',value.playing?'pause':'play');document.getElementById('mode-cycle').disabled=!controls.mode;document.getElementById('queue').disabled=!controls.queue;document.getElementById('mode-cycle').setAttribute('aria-label',['单曲循环','顺序循环','随机播放'][value.mode||0]);
  }
  function apps(values) {
    var signature = JSON.stringify(values || []); if (signature === appSignature) return; appSignature = signature;
    var active = document.activeElement && document.activeElement.dataset.focusId, box = document.getElementById('apps'); box.replaceChildren();
    (values || []).forEach(function (app, index) {
      var button = document.createElement('button'); button.dataset.focusId = 'app-' + app.package; button.className = 'app';
      if (app.icon) {var icon = document.createElement('img'); icon.src = app.icon; icon.alt = ''; icon.onerror = function () {icon.hidden = true;}; button.append(icon);}
      else {var mark = document.createElement('span'); mark.className = 'app-mark'; mark.textContent = ['♫', '▶', '▣', '▦', '▤', '⚙'][index % 6]; button.append(mark);}
      var label = document.createElement('span'); label.textContent = app.name; button.append(label);
      button.onclick = async function () {try {await api.request('actions',{action:'launch_app',params:{package:app.package}}); if (api.mock) toast('模拟启动：' + app.name);} catch (error) {toast(error.message);}};
      box.append(button);
    });
    var old = itemsById(active); if (old) old.focus();
  }
  function icon(id,kind){var paths={prev:'M7 4v16M20 4L9 12l11 8z',next:'M17 4v16M4 4l11 8-11 8z',play:'M7 3l14 9-14 9z',pause:'M7 4v16M17 4v16',shuffle:'M3 5h3l12 14h3m-4-4 4 4-4 3M3 19h3l4-5M14 9l4-4h3m-4-3 4 3-4 4',queue:'M3 5h17M3 11h12M3 17h9M20 12v9l-5-2'};document.getElementById(id).innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="'+paths[kind]+'" fill="'+(/^(play|prev|next)$/.test(kind)?'currentColor':'none')+'" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>';}
  ['prev','next','play','mode-cycle','queue'].forEach(function(id){icon(id,id==='mode-cycle'?'shuffle':id);});document.getElementById('prev').setAttribute('aria-label','上一首');document.getElementById('next').setAttribute('aria-label','下一首');
  document.getElementById('mode-cycle').onclick=async function(){try{await api.request('actions',{action:'media_mode',params:{mode:((media.mode||0)+1)%3}});}catch(error){toast(error.message);}};
  document.getElementById('queue').onclick=async function(){try{var values=await api.request('media/queue'),box=document.createElement('div');box.className='queue-overlay';var panel=document.createElement('div');panel.className='queue-panel';var h=document.createElement('h2');h.textContent='播放器当前队列';panel.append(h);if(!values.length){var p=document.createElement('p');p.textContent='播放器没有提供队列；完整曲库请在音乐软件中查看。';panel.append(p);}values.forEach(function(v){var p=document.createElement('p');p.textContent=v.title||'未命名歌曲';panel.append(p);});var close=document.createElement('button');close.textContent='关闭';close.dataset.focusId='queue-close';close.onclick=function(){box.remove();document.getElementById('queue').focus();};panel.append(close);box.append(panel);document.body.append(box);close.focus();}catch(error){toast(error.message);}};
  function itemsById(id) {return Array.prototype.find.call(document.querySelectorAll('[data-focus-id]'), function (item) {return item.dataset.focusId === id;});}
  ['prev', 'play', 'next'].forEach(function (action) {document.getElementById(action).onclick = async function () {try {renderMedia(await api.request('actions',{action:'media_'+(action==='play'&&media.playing?'pause':action),params:{}}));} catch (error) {toast(error.message);}};});
  document.getElementById('native-home').onclick = function () {if (api.mock) toast('电视上按返回即可回原生桌面'); else api.request('actions',{action:'launch_app',params:{package:'com.gathin.tvhome'}}).catch(function (error) {toast(error.message);});};
  try {
    state = await api.request('bootstrap');if(window.AuroraPresentation)window.AuroraPresentation.update(state); time(state.time); renderWeather(state.weather); renderMedia(state.media); apps(state.apps);
    text('mode', api.mock ? '电脑预览 · 模拟数据 · 方向键 / 回车操作' : '极光桌面 · 第二阶段接口验收 · 非最终主题');
    api.events(function (snapshot) {if(window.AuroraPresentation)window.AuroraPresentation.update(snapshot);if(snapshot.weather)renderWeather(snapshot.weather);time(snapshot.time); renderMedia(snapshot.media); if (snapshot.apps) apps(snapshot.apps);});
    document.documentElement.dataset.auroraReady = 'true'; window.AuroraFocus.first();
    setInterval(async function () {if (!document.hidden) try {renderWeather(await api.request('weather'));} catch (ignored) {}}, 60000);
  } catch (error) {text('mode', '加载失败：' + error.message + '；电视会自动回退');}
}());
