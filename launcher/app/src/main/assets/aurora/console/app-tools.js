'use strict';
(function(){
 var api=window.AuroraAPI,UI=window.AuroraConsoleUI;
 function node(tag,text){var n=document.createElement(tag);if(text!==undefined)n.textContent=text;return n;}
 function mount(host,reload){
  if(host.dataset.ready)return;host.dataset.ready='1';
  var input=node('input'),upload=node('button','选择 APK 安装'),status=node('p'),progress=node('progress'),cards=node('div'),layout=node('div');input.type='file';input.accept='.apk';input.hidden=true;progress.max=100;progress.hidden=true;
  host.append(UI.card('安装应用','Aurora Launcher 自身请使用桌面更新页面。上传完成后校验安装结果，不把排队当作安装成功。',[upload,input,progress,status]),layout,cards);
  upload.onclick=function(){input.click();};
  input.onchange=async function(){var file=input.files[0];if(!file)return;if(api.mock){status.textContent='模拟数据不执行 APK 安装，请连接电视。';return;}upload.disabled=true;progress.hidden=false;
   try{var uploaded=await new Promise(function(resolve,reject){var xhr=new XMLHttpRequest();xhr.open('POST','/api/v1/assets/upload');xhr.setRequestHeader('X-File-Name',encodeURIComponent(file.name));xhr.timeout=3600000;xhr.upload.onprogress=function(e){if(e.lengthComputable)progress.value=e.loaded/e.total*100;};xhr.onload=function(){try{var v=JSON.parse(xhr.responseText);if(!v.ok)throw Error(v.error&&v.error.message||'上传失败');resolve(v.data);}catch(e){reject(e);}};xhr.onerror=function(){reject(Error('上传连接中断'));};xhr.ontimeout=function(){reject(Error('上传超时'));};xhr.send(file);});
    var library=await api.request('assets');if(!library.some(function(item){return item.id===uploaded.id&&item.bytes===file.size;}))throw Error('上传后素材回读失败，未发起安装');
    var job=await api.request('apps/install',{id:uploaded.id});status.textContent='安装任务已提交，正在等待系统结果…';
    for(var count=0;count<120;count++){await new Promise(function(resolve){setTimeout(resolve,1000);});var jobs=await api.request('apps/jobs'),current=jobs.filter(function(j){return j.id===job.id;})[0];if(!current)throw Error('安装任务已失效，请查看系统应用列表');if(current.state==='failed')throw Error(current.reason||'系统安装失败');if(current.state==='done'){status.textContent='应用已安装';await reload();return;}}
    throw Error('等待安装结果超时，请在电视确认安装或查看操作日志');
   }catch(e){status.textContent='安装未完成：'+e.message;}finally{upload.disabled=false;progress.hidden=true;input.value='';}
  };
  async function nativeTools(){try{var config=await api.request('config');if(config.theme!=='legacy'){layout.append(node('p','当前主题的显示与排序请在当前主题设置调整。模块卡片暂不支持此主题。'));return;}
   var data=await api.request('apps/layout'),nativeState=await api.request('config/native'),automatic=nativeState.fields.some(function(field){return field.id==='config:dock_row'&&field.value===true;}),rows=[];
   data.forEach(function(app){var pin=node('input');pin.type='checkbox';pin.checked=app.pinned;pin.disabled=automatic;pin.title=automatic?'独立卡片栏按应用排序自动填满第一排，请调整应用顺序':'';pin.onchange=function(){app.pinned=pin.checked;draft.set(data);};rows.push(UI.row(app.label,'固定到原生桌面第一排；保存后按当前应用顺序排列。',pin));});
   var draft=UI.draft('native-app-layout',{label:'原生桌面固定应用',initial:data,render:function(value){data=value;layout.querySelectorAll('input').forEach(function(control,index){control.checked=data[index].pinned;});},save:async function(value){var latest=await api.request('apps/layout');var pins={};value.forEach(function(item){pins[item.package]=item.pinned;});if(latest.length!==value.length||latest.some(function(item){return !Object.prototype.hasOwnProperty.call(pins,item.package);}))throw Error('应用列表已变化，请刷新');latest.forEach(function(item){item.pinned=pins[item.package];});var result=await api.request('apps/layout',{apps:latest});if(result.length!==latest.length||latest.some(function(item){return !result.some(function(actual){return actual.package===item.package&&actual.pinned===item.pinned&&actual.visible===item.visible;});}))throw Error('应用设置回读不一致，请刷新');}});layout.append(UI.card('第一排固定应用','显示、隐藏与前后排序在上方应用卡片调整。',rows));
   var list=await api.request('apps/cards'),cardRows=list.map(function(item){var check=node('input');check.type='checkbox';check.checked=item.shown;check.onchange=async function(){var target=check.checked;check.disabled=true;try{var result=await api.request('apps/cards',{package:item.package,shown:target});if(!result.some(function(row){return row.package===item.package&&row.shown===target;}))throw Error('回读结果不一致');status.textContent='模块卡片已同步';}catch(e){check.checked=!target;status.textContent='保存失败：'+e.message;}finally{check.disabled=false;}};return UI.row(item.label,item.caption,check);});cards.append(UI.card('应用模块卡片','由支持此能力的应用提供。',cardRows.length?cardRows:[node('p','当前没有可用模块卡片')]));
  }catch(e){layout.append(node('p',(e.code==='NOT_IMPLEMENTED'?'暂不支持：':'读取失败：')+e.message));}}
  nativeTools();
 }
 window.AuroraAppTools={mount:mount};
}());
