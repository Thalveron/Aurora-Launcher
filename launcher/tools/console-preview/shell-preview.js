'use strict';
(function(){
 var UI=window.AuroraConsoleUI,initial={name:'我的电视',enabled:true,density:'standard',opacity:80,note:''};
 try{initial=JSON.parse(localStorage.getItem('aurora-shell-demo-settings'))||initial;}catch(e){}
 function el(id){return document.getElementById(id);}function input(type,id){var n=document.createElement('input');n.type=type;n.id=id;return n;}
 var name=input('text','demo-name');name.maxLength=40;var enabled=input('checkbox','demo-enabled');enabled.className='ui-switch';enabled.setAttribute('role','switch');
 var density=document.createElement('select');density.id='demo-density';[['compact','紧凑'],['standard','标准'],['comfortable','舒适']].forEach(function(pair){var o=document.createElement('option');o.value=pair[0];o.textContent=pair[1];density.append(o);});
 var opacity=input('range','demo-opacity');opacity.min=0;opacity.max=100;var out=document.createElement('output');out.htmlFor=opacity.id;out.id='demo-opacity-value';
 var note=input('text','demo-note');note.placeholder='可选';
 function render(v){name.value=v.name;enabled.checked=v.enabled;density.value=v.density;opacity.value=v.opacity;out.value=v.opacity+'%';note.value=v.note;}
 var draft=UI.draft('shell-demo',{label:'控件预览',initial:initial,render:render,validate:function(v){if(!v.name.trim())throw Error('显示名称不能为空');},save:async function(v){localStorage.setItem('aurora-shell-demo-settings',JSON.stringify(v));}});
 name.oninput=function(){draft.patch({name:name.value});};enabled.onchange=function(){draft.patch({enabled:enabled.checked});};density.onchange=function(){draft.patch({density:density.value});};opacity.oninput=function(){draft.patch({opacity:Number(opacity.value)});};note.oninput=function(){draft.patch({note:note.value});};
 var opacityRow=UI.row('透明度','滑杆旁始终显示当前数值',opacity);opacityRow.querySelector('.setting-row-control').append(out);
 el('demo-settings').append(UI.card('共用设置组件','左侧名称与说明，右侧控件，相关选项放在同一张卡片。',[UI.row('显示名称','文本输入示例，不修改电视名称',name),UI.row('启用功能','独立开关，无需展开额外表单',enabled),UI.row('信息密度','下拉选项与其他控件对齐',density),opacityRow]));
 el('demo-advanced').append(UI.row('备注','高级项目默认折叠，按需展开',note));render(initial);
 document.querySelectorAll('#tabs [data-page]').forEach(function(button){button.onclick=function(){if(button.hidden)return;location.hash=button.dataset.page;activate();};});
 function activate(){var id=location.hash.slice(1)||'services';document.querySelectorAll('#tabs [data-page]').forEach(function(b){var active=b.dataset.page===id;b.classList.toggle('active',active);b.setAttribute('aria-current',active?'page':'false');if(active)el('demo-page-title').textContent=b.textContent;});document.body.classList.remove('menu-open');UI.refreshRoute();}
 window.addEventListener('hashchange',activate);activate();
}());
