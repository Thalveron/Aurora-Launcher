'use strict';
(function () {
  var api = window.AuroraAPI, timer, volumeTimer, volumeTask=null, volumePending=null;
  function el(id) {return document.getElementById(id);}
  var reasons = {stopped: '已停止', desktop_hidden: '桌面不在前台，已暂停', playing: '正在播放', preparing: '正在准备音频',
    manual_pause: '已手动暂停', other_media: '其他音乐正在播放，已让出声音', focus_lost: '音频焦点已让出', ducked: '其他应用暂时使用声音', focus_denied: '暂时无法获得音频焦点'};
  function show(state, form) {
    el('bgm-status').textContent = (reasons[state.reason] || state.reason) + ' · 单曲循环 · 独立屏保音量 ' + state.volume + '%';
    if (form) {el('bgm-volume').max=state.volumeMax||100;el('bgm-volume').value = state.volume;el('bgm-volume-number').value=state.volume; el('bgm-volume-value').textContent = state.volume + '%';
      el('bgm-enabled').checked = state.enabled; el('bgm-autoplay').checked = state.autoplay; el('bgm-fade').value = String(state.fadeMs);
      if (state.selected) el('bgm-library').value = state.selected;}
  }
  async function refresh(form) {try {show(await api.request('bgm'), form);} catch (error) {el('bgm-status').textContent = error.message;}}
  async function library() {
    var files = await api.request('bgm/library'), select = el('bgm-library'), previous = select.value; select.replaceChildren();
    var empty = document.createElement('option'); empty.value = ''; empty.textContent = '请选择已上传音乐'; select.append(empty);
    files.forEach(function (file) {var option = document.createElement('option'); option.value = file.id; option.textContent = file.name; select.append(option);}); select.value = previous;var tracks=el('saver-track'),chosen=tracks.value;tracks.replaceChildren();var none=document.createElement('option');none.value='';none.textContent='请选择屏保音乐';tracks.append(none);files.forEach(function(file){var option=document.createElement('option');option.value=file.id;option.textContent=file.name;tracks.append(option);});tracks.value=chosen;
  }
  async function command(action, data) {try {show(await api.request('bgm/' + action, data || {}), true);} catch (error) {el('bgm-status').textContent = error.message;}}
  el('bgm-play').onclick = function () {var id = el('bgm-library').value; if (!id) {el('bgm-status').textContent = '请先选择音乐'; return;} command('play', {id: id});};
  el('bgm-pause').onclick = function () {command('pause');}; el('bgm-stop').onclick = function () {command('stop');};
  function sendVolume(){if(volumeTask)return volumeTask;volumeTask=(async function(){while(volumePending!==null){var target=volumePending;volumePending=null;try{show(await api.request('bgm/volume',{volume:target}),false);}catch(error){el('bgm-status').textContent='音量未保存：'+error.message;}}})();return volumeTask.finally(function(){volumeTask=null;if(volumePending!==null)sendVolume();});}
  el('bgm-volume').oninput=function(){el('bgm-volume-value').textContent=this.value+'%';el('bgm-volume-number').value=this.value;volumePending=Number(this.value);clearTimeout(volumeTimer);volumeTimer=setTimeout(sendVolume,100);};
  el('bgm-volume').onchange=function(){el('bgm-volume-number').value=this.value;el('bgm-volume-value').textContent=this.value+'%';volumePending=Number(this.value);clearTimeout(volumeTimer);sendVolume();};
  el('bgm-volume-number').onchange=function(){var value=Number(this.value);if(this.value===''||!Number.isInteger(value)||value<0||value>100){el('bgm-status').textContent='请输入 0～100 的整数百分比';return;}el('bgm-volume').value=String(value);el('bgm-volume').onchange();};
  el('bgm-save').onclick = async function () {volumePending=Number(el('bgm-volume').value);clearTimeout(volumeTimer);await sendVolume();command('settings', {enabled: el('bgm-enabled').checked, autoplay: el('bgm-autoplay').checked, fadeMs: Number(el('bgm-fade').value)});};
  el('bgm-upload').onchange = async function () {
    var file = this.files[0]; if (!file) return;
    var result=el('bgm-upload-status');if(file.size>256*1024*1024){result.textContent='文件超过单首256 MB限制：'+(file.size/1024/1024).toFixed(1)+' MB';this.value='';return;}this.disabled = true; result.textContent = '正在上传 ' + file.name;
    try {var response = await fetch('/api/v1/bgm/upload', {method: 'POST', credentials: 'same-origin', headers: {'X-File-Name': encodeURIComponent(file.name), 'Content-Type': 'application/octet-stream'}, body: file});
      var value = await response.json(); if (!response.ok || !value.ok) throw Error(value.error && value.error.message || '上传失败');
      await library(); el('bgm-library').value = value.data.id; el('saver-track').value=value.data.id;result.textContent = '上传成功：'+file.name+'。可试听，或点击“保存音乐与歌词”设为屏保音乐。';
    } catch (error) {result.textContent = '上传失败：'+error.message;} finally {this.disabled = false; this.value = '';}
  };
  library().then(function () {refresh(true);}).catch(function (error) {el('bgm-status').textContent = error.message;});
  timer = setInterval(function () {if (!document.hidden) refresh(false);}, 4000); window.addEventListener('pagehide', function () {clearInterval(timer);});
}());
