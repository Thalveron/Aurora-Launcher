'use strict';
(function(){
 var api=window.AuroraAPI,all=[],selected='',draft=false,opening=false;
 function el(id){return document.getElementById(id);}
 function message(s){el('css-editor-status').textContent=s;}
 async function load(){all=await api.request('themes');var select=el('css-theme-select'),base=el('css-base-select');select.replaceChildren();base.replaceChildren();all.forEach(function(t){var o=document.createElement('option');o.value=t.id;o.textContent=t.name;select.append(o);if(t.available&&!t.custom&&t.id!=='legacy'){var b=document.createElement('option');b.value=t.id;b.textContent=t.name;base.append(b);}});if(!all.some(function(t){return t.id===selected;}))selected=(await api.request('config')).theme;select.value=selected;await read();}
 async function read(){selected=el('css-theme-select').value;var t=all.filter(function(v){return v.id===selected;})[0],value=await api.request('themes/css/'+selected);el('theme-css-code').value=value.css;el('css-theme-note').textContent=selected==='legacy'?'极简桌面当前由原生界面绘制，CSS可以保存，但不会改变该原生界面。':t&&!t.available?'此主题尚未制作；CSS可以先保存，完成主题后应用。':'样式仅应用到此主题；保存后自动更新。';message('');}
 function showNew(){draft=true;el('css-create-form').hidden=false;el('css-new-name').value='';message('输入名称并选择基础主题，保存后会保留为独立条目。');}
 window.AuroraThemeCSS={edit:async function(id){selected=id;opening=true;document.querySelector('[data-page="css"]').click();opening=false;try{await load();}catch(e){message(e.message);}}};
 document.querySelector('[data-page="css"]').addEventListener('click',function(){if(opening)return;load().catch(function(e){message(e.message);});});
 el('css-theme-select').onchange=function(){draft=false;el('css-create-form').hidden=true;read().catch(function(e){message(e.message);});};
 el('theme-css-save').onclick=async function(){try{await api.request('themes/css/'+selected,{css:el('theme-css-code').value});message('此主题 CSS 已保存');}catch(e){message(e.message);}};
 el('theme-css-reset').onclick=async function(){if(confirm('清空此主题的自定义 CSS？'))try{await api.request('themes/css/'+selected,{css:''});el('theme-css-code').value='';message('已恢复此主题基础样式');}catch(e){message(e.message);}};
 el('theme-css-new').onclick=showNew;
 el('new-css-theme').onclick=async function(){opening=true;document.querySelector('[data-page="css"]').click();opening=false;try{await load();el('theme-css-code').value='';showNew();}catch(e){message(e.message);}};
 el('css-create-save').onclick=async function(){try{var value=await api.request('themes/custom',{name:el('css-new-name').value,base:el('css-base-select').value,css:el('theme-css-code').value});selected=value.id;location.href=location.pathname+'#themes';location.reload();}catch(e){message(e.message);}};
})();
