'use strict';
(function(){
 var panel=document.querySelector('[data-panel="device"]');
 function node(tag,cls,text){var n=document.createElement(tag);if(cls)n.className=cls;if(text)n.textContent=text;return n;}
 var blocks=Array.from(panel.querySelectorAll('.device-block')),tabs=node('div','ui-tabs device-tabs'),groups=[];
 tabs.setAttribute('role','tablist');tabs.setAttribute('aria-label','遥控与设备');
 ['连接','遥控与画面','诊断'].forEach(function(label,i){var b=node('button','secondary',label),section=node('div','device-tab-panel');b.id='device-tab-'+i;b.setAttribute('role','tab');b.setAttribute('aria-controls','device-pane-'+i);section.id='device-pane-'+i;section.setAttribute('role','tabpanel');section.setAttribute('aria-labelledby',b.id);b.onclick=function(){select(i);};b.onkeydown=function(e){if(e.key==='ArrowRight'||e.key==='ArrowLeft'){e.preventDefault();var next=(i+(e.key==='ArrowRight'?1:2))%3;select(next);tabs.children[next].focus();}};tabs.append(b);groups.push(section);});
 function select(index){groups.forEach(function(s,i){s.hidden=i!==index;tabs.children[i].setAttribute('aria-selected',String(i===index));tabs.children[i].tabIndex=i===index?0:-1;});panel.dataset.deviceTab=String(index);window.dispatchEvent(new CustomEvent('aurora-device-tab',{detail:index}));}
 blocks.forEach(function(block){block.classList.add('ui-card');});panel.prepend(tabs);groups.forEach(function(s){panel.append(s);});groups[0].append(blocks[0],blocks[1],blocks[3]);groups[1].classList.add('device-control-columns');groups[1].append(blocks[4],blocks[2]);groups[2].append(blocks[5]);
 var diagnostic=node('section','ui-card device-diagnostics');diagnostic.append(node('h3','','设备状态'));var state=node('pre');state.id='device-state-detail';state.textContent='正在读取设备状态…';diagnostic.append(state);groups[2].append(diagnostic);
 var connectionBadge=node('span','ui-badge state-badge disconnected','未连接');connectionBadge.id='device-connect-badge';document.getElementById('adb-status').before(connectionBadge);
 var remote=panel.querySelector('.remote'),directions=node('div','remote-disc'),bottom=node('div','remote-bottom');
 ['up','left','ok','right','down'].forEach(function(key){var b=remote.querySelector('[data-key="'+key+'"]');b.classList.add('remote-'+key);b.setAttribute('aria-label',({up:'上',down:'下',left:'左',right:'右',ok:'确定'})[key]);directions.append(b);});
 ['home','back'].forEach(function(key){bottom.append(remote.querySelector('[data-key="'+key+'"]'));});
 [['volume_down','音量 −'],['volume_up','音量 ＋']].forEach(function(v){var b=node('button','secondary',v[1]);b.dataset.key=v[0];bottom.append(b);});document.getElementById('standby').textContent='待机';bottom.append(document.getElementById('standby'));remote.replaceChildren(directions,bottom);
 var hint=node('p','ui-help');hint.id='device-preview-warning';hint.textContent='电脑模拟无法连接电视、发送按键或读取真实画面。请连接电视控制台后操作。';hint.hidden=!window.AuroraAPI.mock;tabs.after(hint);
 var empty=document.getElementById('screen-empty');empty.replaceChildren(node('span','screen-empty-icon','▣'),node('span','','点击开始观看'));var help=node('small','','停止观看即结束画面采集任务');empty.append(help);
 document.getElementById('stop-watch').hidden=true;
 var watch=document.getElementById('watch');watch.setAttribute('aria-pressed','false');
 [['screen-save','保存截图'],['screen-fullscreen','全屏']].forEach(function(v){var b=node('button','secondary',v[1]);b.id=v[0];watch.parentElement.append(b);});
 panel.querySelectorAll('a').forEach(function(a){a.classList.add('ui-link-button');});
 var screenHeading=blocks[2].querySelector('.heading');screenHeading.classList.add('ui-card-heading');var screenTitle=screenHeading.querySelector('h2');if(screenTitle){var smallTitle=node('h3','',screenTitle.textContent);screenTitle.replaceWith(smallTitle);}
 [blocks[4],blocks[2]].forEach(function(card){var body=node('div','device-card-content');Array.from(card.children).filter(function(child){return !child.classList.contains('ui-card-heading');}).forEach(function(child){body.append(child);});card.append(body);});
 select(0);
}());
