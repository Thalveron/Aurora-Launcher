'use strict';
/* Presentation-only composition over the existing console owners and routes. */
(function(){
 var root=document.getElementById('settings-main'),aside=document.getElementById('live-preview');
 if(!root||!aside)return;
 var selected=new Set(),orders={desktop:[],saver:[]},activeBackground='desktop',activeTheme='',appearance={},refreshing=false;
 function el(id){return document.getElementById(id);}
 function node(tag,cls,text){var n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;}
 function text(n,v){if(n&&n.textContent!==String(v))n.textContent=v;}
 function button(label,fn,cls){var b=node('button',cls,label);b.type='button';b.onclick=fn;return b;}
 function setHidden(n,v){if(n&&n.hidden!==v)n.hidden=v;}
 function view(){return window.AuroraSettingsView;}
 function notify(message){var n=el('notice');text(n,message);setHidden(n,false);}
 function choice(select){return select&&select.options[select.selectedIndex]?select.options[select.selectedIndex].textContent:'';}
 function appearanceCategory(key,title){
  if(/^(ambient)/i.test(key)||/氛围灯/.test(title))return 'ambient';
  if(/^(focus|motion)/i.test(key)||/焦点|选中框|边框|发光|过渡/.test(title))return 'focus';
  if(/font|Clock(Font|Size)|iconSize|spacing|greetingSize/i.test(key)||/字体|字号|间距|图标大小/.test(title))return 'fonts';
  return 'colors';
 }
 function organizeAppearance(){
  var panel=root.querySelector('[data-panel=style]');if(!panel)return;
  if(!el('appearance-folds')){
   var folds=node('div');folds.id='appearance-folds';folds.dataset.componentSkip='true';panel.prepend(folds);
   [['fonts','字体'],['colors','颜色'],['focus','焦点效果'],['ambient','氛围灯']].forEach(function(item,index){var card=node('details','ui-card appearance-fold'),summary=node('summary','',item[1]),fields=node('div');card.id='appearance-'+item[0];card.open=index===0;fields.id='appearance-group-'+item[0];card.append(summary,fields);folds.append(card);});
   panel.querySelectorAll(':scope>h2,:scope>p').forEach(function(n){n.hidden=true;});
   var upload=panel.querySelector('.material-link');if(upload){upload.classList.add('ui-link-button');el('appearance-group-fonts').append(upload);}
  }
  ['theme-tuning','appearance-fields'].forEach(function(id){var host=el(id);if(!host)return;host.hidden=true;Array.from(host.children).forEach(function(row){
   if(row.tagName!=='LABEL'&&!row.classList.contains('setting-row'))return;
   var title=row.querySelector('.setting-row-title'),name=title?title.textContent:row.textContent;
   el('appearance-group-'+appearanceCategory(row.dataset.settingKey||'',name)).append(row);
  });});
  var priorities={fontZh:0,fontEn:1,fontSize:2,fontScale:3,iconSize:4,spacing:5,text:0,accent:1,cardOpacity:2,glassBlur:3,backgroundDim:4,cardRadius:5,card:6};
  root.querySelectorAll('#appearance-folds [id^=appearance-group-]').forEach(function(group){var rows=Array.from(group.children).filter(function(n){return n.dataset.fieldOwner;});var sorted=rows.slice().sort(function(a,b){return (priorities[a.dataset.settingKey]==null?99:priorities[a.dataset.settingKey])-(priorities[b.dataset.settingKey]==null?99:priorities[b.dataset.settingKey]);});if(rows.some(function(row,i){return row!==sorted[i];}))sorted.forEach(function(row){group.append(row);});var upload=group.querySelector('.material-link');if(upload&&group.lastElementChild!==upload)group.append(upload);});
  // Original grouping wrappers no longer have visible content after relocation.
  Array.from(panel.children).filter(function(n){return n.classList.contains('ui-card');}).forEach(function(card){
   var hasRows=Array.from(card.querySelectorAll('.setting-row')).some(function(r){return !r.closest('[hidden]');});
   if(!hasRows&&card.id!=='appearance-folds')card.classList.add('ui-obsolete-wrapper');
  });
 }
 function presets(){
  var select=el('user-presets');if(!select||select.dataset.polished)return;select.dataset.polished='true';
  var row=node('div','ui-presets'),more=node('details','ui-more'),summary=node('summary','','⋯'),menu=node('div','ui-more-menu');summary.setAttribute('aria-label','预设更多操作');
  var panel=root.querySelector('[data-panel=style]');panel.insertBefore(row,el('appearance-folds'));row.append(select,el('preset-save'),more);more.append(summary,menu);
  var importLabel=el('preset-import').closest('label');menu.append(importLabel,el('preset-export'),el('tuning-reset'));
  el('tuning-reset').classList.add('danger');el('preset-apply').hidden=true;
  select.addEventListener('change',function(){if(this.value!=='')el('preset-apply').click();});
  more.addEventListener('click',function(e){if(e.target.closest('button,label')&&e.target!==summary)more.open=false;});
 }
 function numericUnits(){
  root.querySelectorAll('input[type=number]').forEach(function(input){
   if(input.closest('.color-dialog')||input.parentElement.classList.contains('ui-number'))return;
   var unit=input.dataset.unit||(/Seconds$/.test(input.id)?'s':/Minutes$/.test(input.id)?'min':'');
   var wrap=node('span','ui-number');input.before(wrap);wrap.append(input);if(unit)wrap.append(node('span','ui-unit',unit));
   input.title=(input.getAttribute('aria-label')||'数值')+(input.min!==''||input.max!==''?' · '+input.min+'～'+input.max:'')+(unit?' '+unit:'');
  });
 }
 function nativeFontCard(){var clock=el('native-clock-settings');if(!clock)return;var card=el('native-clock-fonts');if(!card){card=node('details','ui-card');card.id='native-clock-fonts';card.open=true;card.append(node('summary','','字体'));clock.before(card);card.append(clock);}setHidden(card,clock.hidden);}
 function configurePreview(){
  if(!el('console-workspace')){var workspace=node('div');workspace.id='console-workspace';root.before(workspace);workspace.append(root,aside);}
  if(!el('preview-collapse')){
   var collapse=button('展开预览',function(){var closed=aside.classList.toggle('preview-collapsed');text(collapse,closed?'展开预览':'收起预览');collapse.setAttribute('aria-expanded',String(!closed));configurePreview();window.dispatchEvent(new CustomEvent('aurora-preview-collapse',{detail:{collapsed:closed&&matchMedia('(max-width:1279px)').matches}}));});
   collapse.id='preview-collapse';collapse.setAttribute('aria-expanded','false');aside.classList.add('preview-collapsed');if(matchMedia('(max-width:1279px)').matches)window.dispatchEvent(new CustomEvent('aurora-preview-collapse',{detail:{collapsed:true}}));aside.querySelector('.preview-heading').append(collapse);
   var demo=node('div','theme-preview-model');demo.id='theme-preview-model';demo.setAttribute('aria-label','原生主题布局示意');
   demo.innerHTML='<div class="mini-clock"><time>--:--</time><small>当前主题布局示意</small></div><div class="mini-weather">☀ 天气</div><div class="mini-music"><span>♫</span><strong>音乐卡片</strong><small>歌词与播放控制</small></div><div class="mini-apps"><span>♫</span><span>▶</span><span>▣</span><span>⚙</span></div>';
   aside.querySelector('.preview-heading').after(demo);
   var sample=el('style-sample');if(sample){sample.classList.add('preview-color-sample');aside.append(sample);}
  }
  var isMock=window.AuroraAPI.mock,legacy=activeTheme==='legacy',viewport=aside.querySelector('.preview-viewport'),frame=el('theme-preview');
  setHidden(el('theme-preview-model'),!isMock||!legacy);setHidden(viewport,!isMock||legacy);
  if(isMock){setHidden(el('shell-preview-retry'),true);setHidden(aside.querySelector('.shell-preview-placeholder'),true);text(aside.querySelector('.preview-heading strong'),'当前主题预览');text(aside.querySelector('p'),'电脑端预览不修改电视；拖动时同步显示未保存的外观。');}
  if(viewport&&frame){var width=viewport.clientWidth||aside.clientWidth-32;if(width>0){viewport.style.height=width*9/16+'px';frame.style.transform='scale('+(width/1920)+')';}}
 }
 function contextHeader(){
  var page=location.hash.slice(1),global=['saver','services','language','inputs','device','capture','traffic','updates','security','logs','usb','library'].indexOf(page)>=0;
  var theme=el('shell-current-theme').parentElement,globalTitle=el('shell-global-context');
  if(!globalTitle){globalTitle=node('strong');globalTitle.id='shell-global-context';theme.append(globalTitle);}
  theme.classList.toggle('global-context',global);text(globalTitle,({device:'遥控与设备',library:'应用管理',inputs:'连接与输入源',capture:'桌面预览',traffic:'应用流量监控'})[page]||'全局设置 · 所有主题共用');setHidden(globalTitle,!global);
 }
 function summaryOf(card){
  if(card.id.indexOf('appearance-')===0)return card.querySelectorAll('.setting-row').length+' 项';
  if(card.querySelector('#greeting-fields')){var font=card.querySelector('[data-setting-key=greetingStyle] select'),size=card.querySelector('[data-setting-key=greetingSize] input[type=number]');return choice(font)+(size?' · '+size.value+' px':'');}
  if(card.querySelector('#saver-track'))return choice(el('saver-track'))||'未选择歌曲';
  if(card.querySelector('#bgm-enabled'))return (el('bgm-enabled').checked?'音乐已开启':'音乐已关闭')+' · '+el('bgm-volume').value+'%';
  if(card.querySelector('#saver-spectrum-fields')){var select=card.querySelector('select'),opacity=card.querySelector('[data-setting-key=opacity] input[type=number]');return choice(select)+(opacity?' · '+opacity.value+'%':'');}
  return '';
 }
 function foldSummaries(){root.querySelectorAll('details.ui-card').forEach(function(card){var summary=Array.from(card.children).find(function(c){return c.tagName==='SUMMARY';});if(!summary)return;
  if(!summary.querySelector('.fold-title')){var title=summary.textContent;summary.replaceChildren(node('span','fold-title',title),node('small','fold-summary'),node('span','fold-chevron','▾'));summary.querySelector('.fold-chevron').setAttribute('aria-hidden','true');}
  text(summary.querySelector('.fold-summary'),summaryOf(card));
 });}
 function saver(){
  var panel=root.querySelector('[data-panel=saver]'),toggle=panel&&panel.querySelector('[data-setting-key=saverEnabled] input');if(!toggle)return;
  var descriptions=Array.from(panel.children).filter(function(n){return n.tagName==='P';});descriptions.forEach(function(n,index){if(index===0)text(n,'屏保背景、音乐与显示设置供所有主题共用。');else n.hidden=true;});
  var musicCard=el('bgm-upload')&&el('bgm-upload').closest('details.ui-card');if(descriptions[1]&&musicCard){descriptions[1].hidden=false;musicCard.querySelector('summary').after(descriptions[1]);}panel.classList.toggle('saver-disabled',!toggle.checked);
  panel.querySelectorAll('input,select,textarea,button').forEach(function(control){if(control===toggle)return;
   if(!toggle.checked){if(control.dataset.saverDisabled===undefined){control.dataset.saverDisabled=control.disabled?'true':'false';control.disabled=true;}}
   else if(control.dataset.saverDisabled!==undefined){control.disabled=control.dataset.saverDisabled==='true';delete control.dataset.saverDisabled;}
  });
  panel.querySelectorAll('.setting-row').forEach(function(row){row.classList.toggle('is-disabled',!toggle.checked&&!row.contains(toggle));});
  panel.querySelectorAll('details.ui-card').forEach(function(card){card.classList.toggle('is-disabled',!toggle.checked);});
 }
 function setupBackgrounds(){
  var panel=root.querySelector('[data-panel=background]'),input=el('background-upload');if(!panel||!input||el('background-drop'))return;
  Array.from(panel.children).filter(function(n){return n.tagName==='P';}).forEach(function(n){n.hidden=true;});
  var note=node('p','ui-page-note','共用一个素材库；桌面背景按主题保存，屏保背景全局共用。');panel.querySelector('h2').after(note);
  var oldRow=input.closest('.setting-row')||input.closest('label');if(oldRow)oldRow.hidden=true;input.hidden=true;
  var drop=node('div','upload-drop');drop.id='background-drop';drop.dataset.componentSkip='true';drop.setAttribute('aria-label','上传背景素材');
  var upload=button('上传图片或视频',function(){input.click();}),help=node('details','ui-help-popover'),q=node('summary','','?');q.setAttribute('aria-label','背景格式与解码说明');help.append(q,node('p','','支持 4K 及以上图片和视频。能否流畅播放取决于设备解码能力；视频背景静音。上传只加入素材库，不会自动使用。'));
  drop.append(node('span','upload-symbol','↑'),node('strong','','拖放图片或视频到这里'),node('small','','或选择文件上传，支持多选'),upload,help);
  var progress=node('progress');progress.id='background-upload-progress';progress.max=100;progress.value=0;progress.hidden=true;progress.setAttribute('aria-label','背景上传进度');drop.append(progress,el('background-upload-status'));
  panel.querySelector('h2').after(drop);drop.before(note);
  ['dragenter','dragover'].forEach(function(type){drop.addEventListener(type,function(e){e.preventDefault();drop.classList.add('drag-over');});});
  drop.addEventListener('dragleave',function(){drop.classList.remove('drag-over');});
  drop.addEventListener('drop',async function(e){e.preventDefault();drop.classList.remove('drag-over');if(!view()||input.disabled)return;try{await view().upload(Array.from(e.dataTransfer.files));}catch(error){text(el('background-upload-status'),error.message);}});
  var tabs=node('div','ui-tabs');tabs.setAttribute('role','tablist');tabs.setAttribute('aria-label','背景用途');drop.after(tabs);
  ['desktop','saver'].forEach(function(scope,index){var title=scope==='desktop'?'桌面背景':'屏保背景',tab=button(title,function(){activeBackground=scope;syncBackgroundTabs();});tab.id='background-tab-'+scope;tab.setAttribute('role','tab');tab.setAttribute('aria-controls','background-pane-'+scope);tabs.append(tab);
   var pane=node('section','ui-card background-pane');pane.id='background-pane-'+scope;pane.setAttribute('role','tabpanel');pane.setAttribute('aria-labelledby',tab.id);tabs.after(pane);
   var gallery=el(scope+'-slides'),seconds=el(scope==='desktop'?'slideSeconds':'saverSlideSeconds'),row=seconds.closest('.setting-row')||seconds.closest('label');pane.append(gallery,row);var caption=node('p','ui-card-note','多选后设置用途；拖动缩略图调整顺序，点击底部保存后同步到电视。');pane.append(caption);
  });
  var toolbar=node('div','background-actions');toolbar.id='background-actions';toolbar.append(node('span','', '未选择素材'),button('设为桌面背景',function(){applyBackground('desktop');}),button('设为屏保背景',function(){applyBackground('saver');}),button('删除所选',deleteSelected,'danger'));tabs.after(toolbar);
  Array.from(panel.children).filter(function(n){return n.classList.contains('ui-card')&&!n.classList.contains('background-pane');}).forEach(function(card){card.classList.add('ui-obsolete-wrapper');});
  syncBackgroundTabs();
 }
 function syncBackgroundTabs(){['desktop','saver'].forEach(function(scope){var tab=el('background-tab-'+scope),pane=el('background-pane-'+scope);if(tab){tab.setAttribute('aria-selected',String(scope===activeBackground));tab.tabIndex=scope===activeBackground?0:-1;}setHidden(pane,scope!==activeBackground);});if(view())backgrounds(activeBackground);updateSelection();}
 function updateSelection(){var bar=el('background-actions');if(!bar)return;text(bar.querySelector('span'),selected.size?'已选择 '+selected.size+' 项':'未选择素材');bar.querySelectorAll('button').forEach(function(b){b.disabled=!selected.size;});}
 function selectedOrder(){return orders[activeBackground].filter(function(id){return selected.has(id);});}
 function applyBackground(scope){if(!view()||!selected.size)return;view().choose(scope,selectedOrder());backgrounds('desktop');backgrounds('saver');}
 async function deleteSelected(){if(!view()||!selected.size)return;var ids=selectedOrder();if(!confirm('删除所选 '+ids.length+' 项背景素材？所有主题共用此库，正在使用的素材会受到保护。'))return;var bar=el('background-actions');bar.querySelectorAll('button').forEach(function(b){b.disabled=true;});try{await view().deleteAssets(ids);ids.forEach(function(id){selected.delete(id);});backgrounds('desktop');backgrounds('saver');}catch(error){notify(error.message);}finally{updateSelection();}}
 function moveOrder(scope,from,to){var ids=orders[scope],i=ids.indexOf(from),j=ids.indexOf(to);if(i<0||j<0||i===j)return;ids.splice(j,0,ids.splice(i,1)[0]);var state=view().state(),active=state[scope],next=ids.filter(function(id){return active.indexOf(id)>=0;});if(JSON.stringify(next)!==JSON.stringify(active))view().choose(scope,next);backgrounds(scope);}
 function backgrounds(scope){
  setupBackgrounds();if(!view())return;var state=view().state(),box=el(scope+'-slides');if(!box)return;
  var list=state.assets.filter(function(a){return a.kind==='background';}),byId={};list.forEach(function(a){byId[a.id]=a;});
  var existing=orders[scope].filter(function(id){return !!byId[id];});state[scope].concat(list.map(function(a){return a.id;})).forEach(function(id){if(byId[id]&&existing.indexOf(id)<0)existing.push(id);});existing=state[scope].filter(function(id){return !!byId[id];}).concat(existing.filter(function(id){return state[scope].indexOf(id)<0;}));orders[scope]=existing;
  Array.from(selected).forEach(function(id){if(!byId[id])selected.delete(id);});box.replaceChildren();
  existing.forEach(function(id,index){var asset=byId[id],card=view().card(asset);card.classList.add('background-asset');Array.from(card.querySelectorAll('small')).filter(function(n){return n.textContent==='▶ 视频';}).forEach(function(n){n.remove();});card.dataset.assetId=id;card.draggable=true;card.classList.toggle('is-selected',selected.has(id));
   var check=node('input');check.type='checkbox';check.checked=selected.has(id);check.setAttribute('aria-label','选择素材 '+asset.name);check.onchange=function(){if(check.checked)selected.add(id);else selected.delete(id);card.classList.toggle('is-selected',check.checked);updateSelection();};card.prepend(check);
   card.append(node('small','asset-size',(Number(asset.bytes||0)/1048576).toFixed(1)+' MB'));
   var desktop=(state.appliedDesktop||[]).indexOf(id)>=0,saver=(state.appliedSaver||[]).indexOf(id)>=0;
   if(desktop||saver)card.append(node('span','asset-used',desktop&&saver?'桌面 / 屏保使用中':desktop?'桌面使用中':'屏保使用中'));
   if(asset.video){var badge=node('span','asset-video','▶ 视频');card.prepend(badge);}
   card.ondragstart=function(e){e.dataTransfer.setData('text/aurora-background',id);card.classList.add('dragging');};card.ondragend=function(){card.classList.remove('dragging');};card.ondragover=function(e){if(Array.from(e.dataTransfer.types).indexOf('text/aurora-background')>=0)e.preventDefault();};card.ondrop=function(e){var from=e.dataTransfer.getData('text/aurora-background');if(from){e.preventDefault();moveOrder(scope,from,id);}};
   var arrows=node('div','asset-order');[-1,1].forEach(function(step){var b=button(step<0?'←':'→',function(){moveOrder(scope,id,existing[index+step]);});b.disabled=index+step<0||index+step>=existing.length;b.setAttribute('aria-label',(step<0?'前移 ':'后移 ')+asset.name);arrows.append(b);});card.append(arrows);box.append(card);
  });
  if(!list.length){var empty=node('div','ui-empty');empty.append(node('span','empty-symbol','▧'),node('strong','','还没有背景素材'),node('p','','上传图片或视频后，可分别设置桌面和屏保背景。'),button('上传背景',function(){el('background-upload').click();}));box.append(empty);}
  updateSelection();
 }
 function refresh(){if(refreshing)return;refreshing=true;try{organizeAppearance();presets();numericUnits();nativeFontCard();var upload=el('background-upload'),drop=el('background-drop');if(upload&&drop)drop.querySelectorAll('button').forEach(function(b){b.disabled=upload.disabled;});configurePreview();contextHeader();saver();foldSummaries();}finally{refreshing=false;}}
 window.AuroraConsolePolish={refresh:refresh,backgrounds:backgrounds,appearance:function(value){appearance=value;var model=el('theme-preview-model');if(model){model.style.setProperty('--mini-accent',window.AuroraColors.css(value.accent||'#77e8ef'));model.style.setProperty('--mini-radius',(value.cardRadius||24)/5+'px');model.style.setProperty('--mini-opacity',value.cardOpacity==null?0.24:value.cardOpacity);model.style.setProperty('--mini-font-scale',value.fontScale||1);}refresh();}};
 document.addEventListener('aurora-settings-ready',function(){window.AuroraAPI.request('config').then(function(c){activeTheme=c.theme;refresh();});window.AuroraAPI.request('time').then(function(t){text(aside.querySelector('.mini-clock time'),t.time);}).catch(function(){});backgrounds('desktop');backgrounds('saver');refresh();});
 document.addEventListener('aurora-settings-saved',function(){if(view()){backgrounds('desktop');backgrounds('saver');}refresh();});
 ['input','change'].forEach(function(type){root.addEventListener(type,function(){Promise.resolve().then(refresh);});});
 root.addEventListener('click',function(){Promise.resolve().then(refresh);});
 el('tabs').addEventListener('click',function(){setTimeout(refresh,0);});window.addEventListener('hashchange',refresh);window.addEventListener('resize',refresh);
 refresh();
}());
