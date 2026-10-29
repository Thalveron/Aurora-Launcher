'use strict';
(function(){
 var api=window.AuroraAPI, active='',nativeTheme=false,themeId='',themeName='';function el(id){return document.getElementById(id);}
 function message(s){el('theme-status').textContent=s;}
 function nativePage(page,title){
  var clock=el('native-clock-settings');if(clock)clock.hidden=!(nativeTheme&&page==='style');
  el('native-category').hidden=true;
  if(nativeTheme&&['style','layout','music','advanced'].indexOf(page)>=0)
   window.AuroraNativeSettings.show(page).catch(function(e){message(e.message);});
 }
 function route(){var page=location.hash.slice(1)||'themes';if(page==='css')page='themes';if(page==='advanced'&&!nativeTheme)page='legacy';var b=document.querySelector('[data-page="'+page.replace(/[^a-z]/g,'')+'"]');if(b)b.click();}
 window.addEventListener('hashchange',route);
 el('menu-toggle').onclick=function(){var open=document.body.classList.toggle('menu-open');this.setAttribute('aria-expanded',String(open));};
 document.querySelectorAll('[data-page]').forEach(function(b){b.addEventListener('click',function(){
  window.scrollTo(0,0);document.body.classList.remove('menu-open');el('menu-toggle').setAttribute('aria-expanded','false');active=b.dataset.page;el('live-preview').hidden=['background','themes','capture','security','updates','device','traffic','legacy','inputs','services','library','language'].indexOf(active)>=0;
  document.body.classList.toggle('wide-workspace',el('live-preview').hidden);
  var themeSection=['legacy','style','layout','music','advanced'].indexOf(active)>=0;el('theme-settings-shell').hidden=!themeSection;
  if(active==='legacy'){document.querySelector('[data-page="style"]').click();return;}
  nativePage(active,b.textContent);
  document.querySelectorAll('[data-page]').forEach(function(t){t.setAttribute('aria-current',t===b?'page':'false');});
  var owner=document.querySelector('#tabs [data-page="legacy"]');owner.classList.toggle('active',themeSection);owner.setAttribute('aria-current',themeSection?'page':'false');
 });});

 el('save-spectra').onclick=function(){el('save-style').click();};
 async function themes(){try{var data=await Promise.all([api.request('themes'),api.request('config')]);var current=data[1].theme;themeId=current;nativeTheme=current==='legacy';var chosen=data[0].filter(function(t){return t.id===current;})[0];themeName=chosen?chosen.name:current;var advanced=document.querySelector('[data-page="advanced"]');if(advanced)advanced.hidden=!nativeTheme;el('current-theme-title').textContent=themeName+' · 当前主题设置';el('current-theme-description').textContent='仅修改「'+themeName+'」，设置按主题独立保存；下次切换回来继续使用。';el('background-theme-note').textContent='当前正在设置：'+themeName+'。素材可供所有主题使用，桌面背景按主题保存，屏保背景全局共用。';
 el('theme-cards').replaceChildren();if(el('theme-cards').parentElement.classList.contains('ui-card'))el('theme-cards').parentElement.classList.add('theme-list-wrapper');
 data[0].filter(function(t){return t.available&&t.id!=='foundation';}).sort(function(a,b){return (a.id==='legacy'?-1:0)-(b.id==='legacy'?-1:0);}).forEach(function(t){var card=document.createElement('article');card.className='theme-card'+(t.id===current?' current':'');var thumbnail=document.createElement('img');thumbnail.className='theme-thumbnail';thumbnail.alt=t.name+'布局缩略图';thumbnail.src='/console/thumb-'+(t.id==='legacy'?'legacy':'cards')+'.svg';card.append(thumbnail);if(t.id===current){var used=document.createElement('span');used.className='theme-used';used.textContent='使用中';card.append(used);}var title=document.createElement('h3');title.textContent=t.name;var desc=document.createElement('p');desc.textContent=t.id==='legacy'?'透明音乐卡片与常用应用，保留熟悉的桌面布局。':t.id==='cards'?'用柔和卡片呈现音乐、天气、应用与设备状态。':t.stage||'已安装的主题';var button=document.createElement('button');button.disabled=!t.available;button.textContent='使用此主题';button.onclick=async function(){try{await api.request('actions',{action:'switch_theme',params:{theme:t.id}});await api.request('themes/open',{});message('已在电视打开：'+t.name);location.reload();}catch(e){message(e.message);}};card.append(title,desc,button);
 if(t.id===current&&t.available){var settings=document.createElement('a');settings.href='#legacy';settings.textContent='设置';settings.className='ui-link-button';card.append(settings);}
 if(t.custom){var del=document.createElement('button');del.className='danger';del.textContent='删除';del.disabled=t.id===current;del.onclick=async function(){if(confirm('删除这个自定义主题？'))try{await api.request('themes/custom/delete',{id:t.id,confirm:true});themes();}catch(e){message(e.message);}};card.append(del);}
 el('theme-cards').append(card);});
 }catch(e){message(e.message);}}
 api.request('bootstrap').then(function(d){themeId=d.config.theme;nativeTheme=themeId==='legacy';route();}).catch(function(){});
 var themePoll=setInterval(function(){if(document.hidden)return;api.request('config').then(function(c){if(themeId&&c.theme!==themeId)location.reload();}).catch(function(){});},2500);
 window.addEventListener('pagehide',function(){clearInterval(themePoll);});
 route();themes();
}());
