'use strict';
(function () {
  var api=window.AuroraAPI, UI=window.AuroraConsoleUI, fields=[], draft, hosts={}, loading;
  function node(tag,text){var n=document.createElement(tag);if(text!==undefined)n.textContent=text;return n;}
  function render(values){fields.forEach(function(f){var control=document.getElementById('native-field-'+f.id);if(!control)return;if(f.type==='boolean')control.checked=!!values[f.id];else control.value=values[f.id];});}
  function fieldRow(f){
    var control=node(f.type==='select'?'select':'input');control.id='native-field-'+f.id;
    if(f.type==='select')f.options.forEach(function(label,index){var option=node('option',label);option.value=index;control.append(option);});
    else if(f.type==='boolean')control.type='checkbox';
    else if(f.type==='color')control.type='color';
    else if(f.type==='number'){control.type='number';control.min=f.min;control.max=f.max;control.step=1;}
    else{control.type='text';control.maxLength=f.max;}
    control.onchange=function(){if(!control.checkValidity()){control.reportValidity();return;}var patch={};patch[f.id]=f.type==='boolean'?control.checked:(f.type==='select'||f.type==='number')?Number(control.value):control.value;draft.patch(patch);};
    var slot=control;if(f.type==='color'){slot=node('div');slot.append(control);var reset=node('button','恢复默认');reset.type='button';reset.className='secondary';reset.onclick=async function(){if(JSON.stringify(draft.value)!==JSON.stringify(draft.baseline)){reset.title='请先保存或撤销未保存的修改，再恢复默认颜色';reset.textContent='请先保存或撤销';return;}reset.disabled=true;try{var state=await api.request('config/native/reset-color',{id:f.id}),values={};state.fields.forEach(function(field){if(Object.prototype.hasOwnProperty.call(draft.value,field.id))values[field.id]=field.value;});draft.replace(values);}catch(e){reset.title=e.message;reset.textContent='失败：'+e.message;}finally{reset.disabled=false;}};slot.append(reset);}
    return UI.row(f.title,'保存后直接应用到当前极简桌面。',slot);
  }
  function build(state){
    fields=state.fields.filter(function(f){return ['appearance','layout','music','advanced','system'].indexOf(f.section)>=0&&f.id.indexOf('location:')!==0;});
    var initial={};fields.forEach(function(f){initial[f.id]=f.value;});
    draft=UI.draft('native-desktop',{label:'极简桌面设置',initial:initial,render:render,validate:function(){Object.keys(hosts).forEach(function(page){var invalid=hosts[page].querySelector('input:invalid');if(invalid){invalid.reportValidity();throw Error('请检查原生桌面的设置值');}});},save:async function(value){var changes={};Object.keys(value).forEach(function(key){if(value[key]!==draft.baseline[key])changes[key]=value[key];});if(Object.keys(changes).length)await api.request('config/native',{changes:changes});}});
    Object.keys(hosts).forEach(function(page){
      var sections=page==='style'?['appearance']:page==='advanced'?['advanced','system']:[page],rows=fields.filter(function(f){return sections.indexOf(f.section)>=0;}).map(fieldRow);
      hosts[page].replaceChildren(UI.card(page==='style'?'文字与默认背景':page==='layout'?'应用与桌面布局':page==='music'?'播放卡片与歌词':'电视适配','这些设置仅属于极简桌面，其他主题保持独立。',rows));
      if(page==='style'||page==='music'){var upload=node('a',page==='style'?'上传中文／英文字体':'上传 LRC 歌词');upload.href='#assets';upload.className='ui-link-button';hosts[page].append(upload);}
    });render(initial);
  }
  async function show(page){
    if(['style','layout','music','advanced'].indexOf(page)<0)return false;
    var config=await api.request('config');if(config.theme!=='legacy')return false;
    document.querySelectorAll('[data-panel]').forEach(function(p){p.hidden=true;});
    document.getElementById('native-category').hidden=false;
    var frame=document.getElementById('native-settings-anchor');frame.hidden=true;frame.removeAttribute('src');
    document.getElementById('native-category-title').textContent='极简桌面 · 当前主题设置';
    Object.keys(hosts).forEach(function(key){hosts[key].hidden=key!==page;});
    var clock=document.getElementById('native-clock-settings');if(clock)clock.hidden=page!=='style';
    if(!loading)loading=api.request('config/native').then(build).catch(function(error){loading=null;throw error;});
    try{await loading;}catch(error){hosts[page].replaceChildren(node('p','无法读取设置：'+error.message));}return true;
  }
  ['style','layout','music','advanced'].forEach(function(page){var host=node('div');host.hidden=true;hosts[page]=host;document.getElementById('native-category').append(host);});
  window.AuroraNativeSettings={show:show};
}());
