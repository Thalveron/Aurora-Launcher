'use strict';
(function(){
 var api=window.AuroraAPI, active='',nativeTheme=false,legacyBase='',themeId='',themeName='';function el(id){return document.getElementById(id);}
 function message(s){el('theme-status').textContent=s;}
 var nativePages={style:'appearance',layout:'layout',music:'music',library:'apps',usb:'usb',logs:'logs',advanced:'advanced'};
 function nativePage(page,title){var mapped=nativePages[page],show=!!mapped&&!!legacyBase&&(nativeTheme||['library','usb','logs'].indexOf(page)>=0);
 var clock=el('native-clock-settings');if(clock)clock.hidden=!(nativeTheme&&page==='style');el('native-category').hidden=!show;if(show){document.querySelectorAll('[data-panel]').forEach(function(p){p.hidden=true;});el('live-preview').hidden=true;document.body.classList.add('wide-workspace');el('native-category-title').textContent=title;var src=legacyBase+'?section='+mapped+'&theme='+encodeURIComponent(themeId)+'#embedded/'+mapped;var frame=el('native-category-frame');if(frame.getAttribute('src')!==src){if(frame._sizeObserver)frame._sizeObserver.disconnect();var fresh=frame.cloneNode(false);fresh.removeAttribute('src');fresh.style.visibility='hidden';frame.replaceWith(fresh);frame=fresh;frame.onload=function(){try{if(frame.contentWindow.location.search.indexOf('section='+mapped)<0||frame.contentDocument.documentElement.dataset.sectionReady!==mapped)return;}catch(e){return;}window.dispatchEvent(new Event('aurora-frame-ready'));frame.style.visibility='visible';};frame.src=src;}}
 }
 function route(){var page=location.hash.slice(1)||'themes';if(page==='css')page='themes';if(page==='advanced'&&!nativeTheme)page='legacy';var b=document.querySelector('[data-page="'+page.replace(/[^a-z]/g,'')+'"]');if(b)b.click();}
 window.addEventListener('hashchange',route);
 window.addEventListener('message',function(e){var frame=el('native-category-frame');if(e.origin===location.origin&&frame&&e.source===frame.contentWindow&&e.data&&e.data.type==='aurora-open-backgrounds')document.querySelector('[data-page="background"]').click();});
 el('menu-toggle').onclick=function(){var open=document.body.classList.toggle('menu-open');this.setAttribute('aria-expanded',String(open));};
 document.querySelectorAll('[data-page]').forEach(function(b){b.addEventListener('click',function(){
  document.body.classList.remove('menu-open');el('menu-toggle').setAttribute('aria-expanded','false');active=b.dataset.page;el('live-preview').hidden=['background','themes','capture','security','device','traffic','legacy','inputs','services','library','language'].indexOf(active)>=0;
  document.body.classList.toggle('wide-workspace',el('live-preview').hidden);
  var themeSection=['legacy','style','layout','music','advanced'].indexOf(active)>=0;el('theme-settings-shell').hidden=!themeSection;
  if(active==='legacy'){document.querySelector('[data-page="style"]').click();return;}
  nativePage(active,b.textContent);
  document.querySelectorAll('[data-page]').forEach(function(t){t.setAttribute('aria-current',t===b?'page':'false');});
  var owner=document.querySelector('#tabs [data-page="legacy"]');owner.classList.toggle('active',themeSection);owner.setAttribute('aria-current',themeSection?'page':'false');
 });});

 el('save-spectra').onclick=function(){el('save-style').click();};
 async function themes(){try{var data=await Promise.all([api.request('themes'),api.request('config')]);var current=data[1].theme;themeId=current;nativeTheme=current==='legacy';var chosen=data[0].filter(function(t){return t.id===current;})[0];themeName=chosen?chosen.name:current;var advanced=document.querySelector('[data-page="advanced"]');if(advanced)advanced.hidden=!nativeTheme;el('current-theme-title').textContent=themeName+' · 当前主题设置';el('current-theme-description').textContent='仅修改「'+themeName+'」，设置自动独立保存；下次切换回来继续使用。';el('background-theme-note').textContent='当前正在设置：'+themeName+'。素材可供所有主题使用，桌面背景按主题保存，屏保背景全局共用。';
 el('theme-cards').replaceChildren();el('development-themes').replaceChildren();
 data[0].filter(function(t){return t.available;}).sort(function(a,b){return (a.id==='legacy'?-1:0)-(b.id==='legacy'?-1:0);}).forEach(function(t){var card=document.createElement('article');card.className='theme-card'+(t.id===current?' current':'');var title=document.createElement('h3');title.textContent=t.name;var desc=document.createElement('p');desc.textContent=t.id==='legacy'?'电视当前原生桌面 · 固定内置主题 · 保留原有功能':t.stage||'固定内置主题';var button=document.createElement('button');button.disabled=!t.available;button.textContent=!t.available?'尚未制作':t.id===current?'打开此主题':'使用此主题';button.onclick=async function(){try{await api.request('actions',{action:'switch_theme',params:{theme:t.id}});await api.request('themes/open',{});message('已在电视打开：'+t.name);location.reload();}catch(e){message(e.message);}};card.append(title,desc,button);
 if(t.id===current&&t.available){var settings=document.createElement('a');settings.href='#legacy';settings.textContent='桌面设置';card.append(settings);}
 if(t.custom){var del=document.createElement('button');del.className='secondary';del.textContent='删除';del.disabled=t.id===current;del.onclick=async function(){if(confirm('删除这个自定义主题？'))try{await api.request('themes/custom/delete',{id:t.id,confirm:true});themes();}catch(e){message(e.message);}};card.append(del);}
 el(t.id==='foundation'?'development-themes':'theme-cards').append(card);});
 }catch(e){message(e.message);}}
 api.request('bootstrap').then(function(d){legacyBase=d.legacyConsole;themeId=d.config.theme;nativeTheme=themeId==='legacy';route();}).catch(function(){});
 var themePoll=setInterval(function(){if(document.hidden)return;api.request('config').then(function(c){if(themeId&&c.theme!==themeId)location.reload();}).catch(function(){});},2500);
 window.addEventListener('pagehide',function(){clearInterval(themePoll);});
 route();themes();
}());
