'use strict';
(function () {
  const api = window.AuroraAPI;
  function element(tag, text) {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function button(label, action, status) {
    const node = element('button', label);
    node.onclick = async function () {
      node.disabled = true;
      try { await action(); } catch (error) { status.textContent = error.message; }
      finally { node.disabled = false; }
    };
    return node;
  }
  function card(host, title) {
    const box = element('section');
    box.className = 'ui-card';
    box.append(element('h3', title));
    host.append(box);
    return box;
  }

  const usb=document.querySelector('[data-panel="usb"]');
  const songs=card(usb,'所有歌曲');songs.classList.add('songs-section');
  const columns=element('div');columns.className='songs-columns';
  const library=element('div'),player=element('article');player.className='song-player';
  const songStatus=element('p'),list=element('div');list.className='song-list';
  songStatus.setAttribute('role','status');
  const nowTitle=element('h3','暂无播放'),artist=element('p'),coverBox=element('div'),cover=element('img'),fallback=element('span','♫');
  coverBox.className='song-cover';cover.alt='';cover.hidden=true;coverBox.append(fallback,cover);
  cover.onerror=()=>{cover.hidden=true;fallback.hidden=false;};
  let snapshot,playing=false,imageKey='',refreshing=false;
  const progress=element('progress');progress.max=1;progress.value=0;progress.setAttribute('aria-label','播放进度');
  const controls=element('div');controls.className='song-controls';
  const message=element('p');message.setAttribute('role','status');
  async function readPlayer(){
    if(usb.hidden||document.hidden||refreshing)return;
    refreshing=true;
    try{
      const m=await api.request('media');playing=m.playing;
      nowTitle.textContent=m.title||'暂无播放';artist.textContent=m.artist||'';
      const key=m.cover&&[m.cover,m.title,m.artist,m.durationMs].join('|');
      if(key!==imageKey){imageKey=key;cover.hidden=!key;fallback.hidden=!!key;
        if(key)cover.src=m.cover+'?track='+encodeURIComponent([m.title,m.artist].join('-'));else cover.removeAttribute('src');}
      progress.value=m.durationMs?Math.min(1,m.positionMs/m.durationMs):0;
      setTransportIcon(toggle,playing?'暂停':'播放');
      prev.disabled=!(m.controls&&m.controls.prev);next.disabled=!(m.controls&&m.controls.next);
      toggle.disabled=!(m.controls&&(playing?m.controls.pause:m.controls.play));
    }catch(e){message.textContent=e.message;}finally{refreshing=false;}
  }
  const transportPaths={
    '上一首':'M5 5h3v14H5z M19 5v14L9 12z',
    '播放':'M7 4.5v15l13-7.5z',
    '暂停':'M5.5 4.5h4.5v15H5.5z M14 4.5h4.5v15H14z',
    '下一首':'M16 5h3v14h-3z M5 5l10 7-10 7z'
  };
  function setTransportIcon(control,label){
    if(control.getAttribute('aria-label')===label)return;
    control.setAttribute('aria-label',label);control.title=label;
    const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');
    svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('width','24');svg.setAttribute('height','24');svg.setAttribute('aria-hidden','true');
    const path=document.createElementNS(svg.namespaceURI,'path');path.setAttribute('d',transportPaths[label]);path.setAttribute('fill','currentColor');
    svg.append(path);control.replaceChildren(svg);
  }
  function transport(label,command){
    const control=button('',async()=>{await api.request('media/'+command(),{});await readPlayer();},message);
    setTransportIcon(control,label);return control;
  }
  const prev=transport('上一首',()=>'prev'),toggle=transport('播放',()=>playing?'pause':'play'),next=transport('下一首',()=>'next');
  controls.append(prev,toggle,next);player.append(coverBox,nowTitle,artist,progress,controls,message);
  async function refreshSongs(){
    snapshot=null;songStatus.textContent='正在读取…';list.replaceChildren();
    const data=await api.request('music/library');snapshot=data.request;
    const batch=document.createDocumentFragment();
    data.songs.forEach((song,index)=>{
      const row=element('button');row.type='button';row.className='song-row';
      const info=element('span');info.className='song-info';info.append(element('strong',song.title),element('small',song.artist));
      row.append(element('span',String(index+1)),info,element('span','▷'));
      row.onclick=async()=>{try{await api.request('music/library/play',{request:snapshot,index:song.index});
        message.textContent='已点播：'+song.title;await readPlayer();}catch(e){message.textContent=e.message;}};
      batch.append(row);
    });
    list.append(batch);songStatus.textContent='共 '+data.songs.length+' 首';
  }
  const refresh=button('刷新曲库',refreshSongs,songStatus),toolbar=element('div');toolbar.className='song-toolbar';
  toolbar.append(songStatus,refresh);library.append(toolbar,list);columns.append(library,player);songs.append(columns);
  document.querySelector('[data-page="usb"]').addEventListener('click',()=>{refresh.click();readPlayer();});
  const timer=setInterval(readPlayer,4000);window.addEventListener('pagehide',()=>clearInterval(timer),{once:true});
  if(location.hash==='#usb'){refresh.click();readPlayer();}
  const ui=window.AuroraConsoleUI;
  const delay=element('select'), themeStatus=element('p');
  delay.id='fullscreen-delay';
  [0,1,3,5,8].forEach(minutes=>{
    const option=element('option',minutes?minutes+' 分钟':'关闭');option.value=minutes;delay.append(option);
  });
  const choose=button('从背景库选择',()=>{
    document.querySelector('[data-page="background"]').click();
    document.getElementById('background-tab-fullscreen').click();
  },themeStatus);
  const hideCover=element('input');hideCover.type='checkbox';hideCover.id='fullscreen-hide-cover';
  const lyricsAlign=element('select');lyricsAlign.id='fullscreen-lyrics-align';
  [['center','居中'],['left','靠左'],['right','靠右']].forEach(function(pair){var option=element('option',pair[1]);option.value=pair[0];lyricsAlign.append(option);});
  const lyricsLayout=element('select');lyricsLayout.id='fullscreen-lyrics-layout';
  [['side','右侧滚动（默认）'],['bottom-one','底部一行'],['bottom-two','底部两行错落']].forEach(function(pair){var option=element('option',pair[1]);option.value=pair[0];lyricsLayout.append(option);});
  const specStyle=element('select');
  [['bars','渐变柱状'],['wave','流动波线'],['mirror','镜面频谱'],['dots','光点'],['ring','环形']].forEach(function(pair){var option=element('option',pair[1]);option.value=pair[0];specStyle.append(option);});
  const specColor=window.AuroraColors.button('#55DDFFFF',function(v){patchSpectrum({color:v});});
  const specColor2=window.AuroraColors.button('#8266FFFF',function(v){patchSpectrum({color2:v});});
  const specOpacity=element('input');specOpacity.type='number';specOpacity.min=0;specOpacity.max=100;
  const specSize=element('input');specSize.type='number';specSize.min=30;specSize.max=200;
  const specAngle=element('input');specAngle.type='number';specAngle.min=0;specAngle.max=360;
  function patchSpectrum(patch){if(!full)return;full.patch({spectrum:Object.assign({},full.value.spectrum||{},patch)});}
  const settings=ui.card('全屏音乐播放','在桌面播放音乐且没有遥控操作时进入全屏。',[
    ui.row('进入全屏','按当前主题保存。',delay),
    ui.row('播放背景','可选择背景库中的图片或视频。',choose),
    ui.row('隐藏专辑图片','只显示背景、标题与歌词。',hideCover),
    ui.row('歌词对齐','底部歌词的水平位置，右侧滚动时同样生效。',lyricsAlign),
    ui.row('歌词样式','右侧滚动为原版；底部一行/两行错落适合沉浸歌词。',lyricsLayout),
    themeStatus]);
  const spectrumCard=ui.card('全屏音乐频谱','与屏保同一套 5 种频谱参数，单独保存。',[
    ui.row('频谱样式','',specStyle),
    ui.row('渐变起始色','',specColor),
    ui.row('渐变结束色','',specColor2),
    ui.row('透明度','0 为隐藏。',specOpacity),
    ui.row('尺寸百分比','30～200。',specSize),
    ui.row('渐变方向（度）','',specAngle)]);
  function placeSettings(page){
    const native=document.getElementById('native-category');
    const host=page==='music'?(native.hidden?document.querySelector('[data-panel="music"]'):native):null;
    [settings,spectrumCard].forEach(function(card){if(host){if(!host.contains(card))host.append(card);card.hidden=false;}else card.hidden=true;});
  }
  document.addEventListener('click',event=>{
    const tab=event.target.closest('[data-page]');
    if(tab)placeSettings(tab.dataset.page);
  });
  new MutationObserver(()=>{if(location.hash==='#music'||document.querySelector('[data-page="music"][aria-current="page"]'))placeSettings('music');})
    .observe(document.getElementById('native-category'),{attributes:true,attributeFilter:['hidden']});
  let full;
  window.AuroraFullscreenGallery=function(){
    const box=document.getElementById('fullscreen-slides'), view=window.AuroraSettingsView;
    if(!box||!view)return;
    const items=view.state().assets.filter(a=>a.kind==='background');
    box.replaceChildren();
    if(!full){box.textContent=themeStatus.textContent||'正在读取全屏设置…';return;}
    const selected=full.value.background;
    function pick(tile,id,name){
      tile.classList.add('background-asset');tile.classList.toggle('is-selected',selected===id);
      const radio=element('input');radio.type='radio';radio.name='fullscreen-background';radio.checked=selected===id;
      radio.setAttribute('aria-label',name);tile.prepend(radio);
      tile.onclick=e=>{if(!e.target.closest('button,video'))full.patch({background:id});};box.append(tile);
    }
    const order=JSON.parse(localStorage.getItem('fullscreen-gallery-order')||'[]');
    items.sort((a,b)=>(order.includes(a.id)?order.indexOf(a.id):Infinity)-(order.includes(b.id)?order.indexOf(b.id):Infinity));
    function tileFor(asset){
      const tile=view.card(asset),buttons=element('div');buttons.className='asset-order';
      const neighbors=items.filter(a=>a.id!==selected),index=neighbors.indexOf(asset);
      [-1,1].forEach(step=>{
        const arrow=element('button',step<0?'←':'→');arrow.disabled=index<0||!neighbors[index+step];
        arrow.onclick=()=>{const other=neighbors[index+step];neighbors[index+step]=asset;neighbors[index]=other;
          localStorage.setItem('fullscreen-gallery-order',JSON.stringify(neighbors.map(a=>a.id)));window.AuroraFullscreenGallery();};
        buttons.append(arrow);
      });
      const del=element('button','删除素材');del.className='danger';del.onclick=async()=>{
        if(!confirm('删除「'+asset.name+'」？'))return;
        try{await view.deleteAssets([asset.id]);window.AuroraFullscreenGallery();}catch(e){alert(e.message);}
      };buttons.append(del);tile.append(buttons);return tile;
    }
    const first=items.find(a=>a.id===selected);
    if(first)pick(tileFor(first),first.id,first.name);
    const plain=element('div');plain.className='asset';plain.append(element('label','默认背景'));pick(plain,'','默认背景');
    items.filter(a=>a.id!==selected).forEach(a=>pick(tileFor(a),a.id,a.name));
  };
  [delay,hideCover,lyricsAlign,lyricsLayout,specStyle,specColor,specColor2,specOpacity,specSize,specAngle].forEach(function(control){control.disabled=true;});
  api.request('music/fullscreen').then(initial=>{
    full=ui.draft('fullscreen-music',{label:'全屏音乐播放',initial,render(value){
      delay.value=value.minutes;
      hideCover.checked=!!value.hideCover;
      lyricsAlign.value=value.lyricsAlign||'center';
      lyricsLayout.value=value.lyricsLayout||'side';
      const spec=value.spectrum||{};
      specStyle.value=spec.style||'wave';
      specColor.update(spec.color||'#55DDFFFF');
      specColor2.update(spec.color2||'#8266FFFF');
      specOpacity.value=spec.opacity==null?50:spec.opacity;
      specSize.value=spec.size==null?100:spec.size;
      specAngle.value=spec.angle==null?315:spec.angle;
      window.AuroraFullscreenGallery();
    },save:value=>api.request('music/fullscreen',value)});
    [delay,hideCover,lyricsAlign,lyricsLayout,specStyle,specColor,specColor2,specOpacity,specSize,specAngle].forEach(function(control){control.disabled=false;});
    full.set(full.value);window.AuroraFullscreenGallery();
  }).catch(error=>{themeStatus.textContent=error.message;window.AuroraFullscreenGallery();});
  delay.onchange=()=>full.patch({minutes:Number(delay.value)});
  hideCover.onchange=()=>full&&full.patch({hideCover:hideCover.checked});
  lyricsAlign.onchange=()=>full&&full.patch({lyricsAlign:lyricsAlign.value});
  lyricsLayout.onchange=()=>full&&full.patch({lyricsLayout:lyricsLayout.value});
  specStyle.onchange=()=>patchSpectrum({style:specStyle.value});
  specOpacity.oninput=()=>{var n=Number(specOpacity.value);if(Number.isFinite(n))patchSpectrum({opacity:Math.max(0,Math.min(100,n))});};
  specSize.oninput=()=>{var n=Number(specSize.value);if(Number.isFinite(n))patchSpectrum({size:Math.max(30,Math.min(200,n))});};
  specAngle.oninput=()=>{var n=Number(specAngle.value);if(Number.isFinite(n))patchSpectrum({angle:Math.max(0,Math.min(360,n))});};
  if(location.hash==='#music')placeSettings('music');

  const enabled=element('input'),savedList=element('div'),status=element('p');
  enabled.type='checkbox';enabled.id='wifi-shortcut-enabled';enabled.className='ui-switch';enabled.disabled=true;
  savedList.className='wifi-saved-list';
  const add=element('button','添加网络');add.disabled=true;
  const editor=element('div');editor.className='wifi-editor';
  const name=element('input'),password=element('input');
  name.id='wifi-add-name';name.maxLength=32;password.id='wifi-add-password';password.type='password';password.autocomplete='new-password';
  const nameRow=ui.row('网络名称','',name),passwordRow=ui.row('密码','开放网络可留空。',password);
  nameRow.classList.add('wifi-field');passwordRow.classList.add('wifi-field');editor.append(nameRow,passwordRow);
  const wifi=ui.card('Wi-Fi 密码快捷方式','最多保存五个网络。',[
    ui.row('显示桌面图标','',enabled),savedList,add,status]);
  wifi.classList.add('wifi-settings');document.querySelector('[data-panel="inputs"]').append(wifi);
  let items=[],form,wifiToggle,busy=false;
  const closed={open:false,name:'',password:''};
  function syncControls(){add.disabled=!form||busy||form.value.open||items.length>=5;}
  function drawEditor(value){
    if(value.open){
      if(!editor.isConnected)add.after(editor);
      name.value=value.name;password.value=value.password;
    }else{editor.remove();name.value='';password.value='';}
    syncControls();
  }
  async function change(row,operation){
    if(busy)return;
    if(operation==='remove'&&!confirm('从电视移除「'+row.name+'」？'))return;
    busy=true;syncControls();
    try{const result=await api.request('wifi/settings',{operation,name:row.name,visible:!row.visible});drawList(result);status.textContent='已同步到电视';}
    catch(error){status.textContent=error.message;}
    finally{busy=false;syncControls();}
  }
  function drawList(data){
    items=data.items;savedList.replaceChildren();
    items.forEach(row=>{
      const item=element('article'),info=element('div'),actions=element('div');item.className='wifi-network-item';actions.className='actions';
      info.append(element('strong',row.name),element('small',row.visible?'电视上显示':'电视上隐藏'));
      const visibility=element('button',row.visible?'隐藏':'显示'),remove=element('button','移除');
      visibility.className='secondary';remove.className='secondary';visibility.onclick=()=>change(row,'visibility');remove.onclick=()=>change(row,'remove');
      actions.append(visibility,remove);item.append(info,actions);savedList.append(item);
    });syncControls();
  }
  async function loadWifi(){
    try{
      const data=await api.request('wifi/settings');
      if(!form){
        form=ui.draft('wifi-add',{label:'添加无线网络',initial:closed,render:drawEditor,
          validate(value){if(!value.name.trim()||new Blob([value.name]).size>32)throw Error('请输入有效的网络名称（最多32字节）');
            if(items.some(row=>row.name===value.name))throw Error('该网络已存在');},
          save:value=>api.request('wifi/settings',{operation:'add',name:value.name,password:value.password}),
          saved(result){form.replace(closed);drawList(result);}});
        wifiToggle=ui.draft('wifi-enabled',{label:'桌面 Wi-Fi 图标',initial:data.enabled,render:value=>{enabled.checked=value;},
          save:value=>api.request('wifi/settings',{enabled:value})});
      }
      drawList(data);wifiToggle.replace(data.enabled);enabled.checked=wifiToggle.value;enabled.disabled=false;
    }catch(error){status.textContent=error.message;}
  }
  add.onclick=()=>{form.set({open:true,name:'',password:''});name.focus();};
  name.oninput=password.oninput=()=>form.patch({name:name.value,password:password.value});
  enabled.onchange=()=>wifiToggle.set(enabled.checked);
  document.querySelector('[data-page="inputs"]').addEventListener('click',loadWifi);
  if(location.hash==='#inputs')loadWifi();
}());

(function () {
  const ui = window.AuroraConsoleUI;
  const download = document.createElement('a');
  download.href = '/api/v1/config/backup/download';
  download.textContent = '下载配置备份';
  const file = document.createElement('input');
  file.type = 'file';
  file.accept = '.zip';
  file.hidden = true;
  const restore = document.createElement('button');
  restore.textContent = '上传备份并恢复';
  restore.onclick = () => file.click();
  const status = document.createElement('p');
  status.setAttribute('role', 'status');
  file.onchange = async () => {
    const backup = file.files[0];
    file.value = '';
    if (!backup || !window.confirm('用「' + backup.name + '」中的配置覆盖电视当前设置？')) return;
    restore.disabled = true;
    status.textContent = '正在上传并恢复，请稍候…';
    try {
      const response = await fetch('/api/v1/config/backup/restore', {
        method: 'POST', credentials: 'same-origin', body: backup,
        headers: {'Content-Type': 'application/octet-stream', 'X-Aurora-Restore': 'replace'}
      });
      const result = await response.json();
      if (!response.ok || !result.data?.restored) throw new Error(result.error?.message || '恢复失败，请检查备份文件和登录状态。');
      status.textContent = '配置已恢复。请重新打开电视桌面，并刷新本页。';
    } catch (error) {
      status.textContent = error.message;
    } finally {
      restore.disabled = false;
    }
  };
  document.querySelector('[data-panel="updates"]').append(ui.card('配置备份与恢复',
    '卸载前请将备份下载到手机或电脑。备份含已保存的无线网络密码，请妥善保管。',
    [ui.row('保存当前配置', '恢复时会回到导出时的设置。', download),
     ui.row('从备份恢复', '选择先前下载的压缩包。', restore), file, status]));
})();
