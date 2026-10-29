'use strict';
(function(){
 var root=document.getElementById('settings-main'),queued=false,columns=new Set(),ready=!!window.AuroraSettingsView;if(!root)return;
 function PageContainer(element){element.classList.add('page-container');return element;}
 function CardGrid(element){element.classList.add('card-grid');return element;}
 function measure(element){var width=element.getBoundingClientRect().width;if(width)element.classList.toggle('is-single-column',width<900);var ports=element.querySelector('.input-ports-card');if(ports){var left=element.querySelector('.settings-lane:first-child>.ui-card');if(left&&left!==ports){var height=left.getBoundingClientRect().height;if(height)ports.style.setProperty('--input-ports-height',Math.max(240,height)+'px');}}}
 var resize=window.ResizeObserver?new ResizeObserver(function(entries){entries.forEach(function(entry){measure(entry.target);});}):null;
 function SettingsColumns(element){element.classList.add('settings-columns');if(!element.querySelector('.settings-lane')){for(var i=0;i<2;i++){var lane=document.createElement('div');lane.className='settings-lane';element.append(lane);}}if(!columns.has(element)){columns.add(element);if(resize)resize.observe(element);}measure(element);return element;}
 function settingsRuns(parent){
  var group=null;
  Array.from(parent.children).forEach(function(child){
   var card=child.matches('.ui-card,.device-block')&&!child.matches('.background-pane,.theme-card,.app-tile,.theme-list-wrapper,.traffic-chart-card,.traffic-table-card')&&!child.querySelector('.gallery,.card-grid,.theme-cards,.apps-grid,.traffic-metrics,table');
   if(!card){group=null;return;}
   if(!group){group=document.createElement('div');SettingsColumns(group);child.before(group);group.dataset.nextLane='0';}var index=Number(group.dataset.nextLane);group.children[index].append(child);group.dataset.nextLane=String(1-index);
  });
 }
 function refresh(){
  var workspace=document.getElementById('console-workspace');PageContainer(workspace||root);if(workspace)root.classList.remove('page-container');
  root.querySelectorAll('.theme-cards,.apps-grid,.gallery,.ui-status-grid,.traffic-metrics,.traffic-summary-cards,.traffic-totals,#capture-gallery,#asset-gallery').forEach(CardGrid);
  if(ready)root.querySelectorAll('[data-panel],.device-tab-panel').forEach(function(parent){if(['background','logs'].indexOf(parent.dataset.panel)<0&&!parent.classList.contains('device-control-columns'))settingsRuns(parent);});
  root.querySelectorAll('.device-control-columns').forEach(function(element){if(!columns.has(element)){columns.add(element);if(resize)resize.observe(element);}measure(element);});
  var inputs=document.getElementById('input-list');if(inputs&&!inputs.closest('.input-ports-card')){var old=inputs.closest('.ui-card'),card=document.createElement('details'),summary=document.createElement('summary'),body=document.createElement('div');card.className='ui-card input-ports-card';summary.textContent='输入接口';body.className='input-ports-body';card.append(summary,body);if(old){old.replaceWith(card);Array.from(old.children).filter(function(child){return child.tagName!=='H3';}).forEach(function(child){body.append(child);});}else{inputs.before(card);body.append(inputs);}}
  if(inputs){var summary=inputs.closest('.input-ports-card').querySelector('summary'),preview=summary.querySelector('.input-port-preview');if(!preview){if(!summary.querySelector('.fold-title')){summary.replaceChildren();[['span','fold-title','输入接口'],['small','fold-summary',''],['span','fold-chevron','▾']].forEach(function(v){var part=document.createElement(v[0]);part.className=v[1];part.textContent=v[2];summary.append(part);});}preview=document.createElement('div');preview.className='input-port-preview';preview.setAttribute('aria-label','首个输入接口');summary.append(preview);}var first=inputs.querySelector('.input-item>div'),signature=first?first.textContent:inputs.textContent||'正在读取接口…';if(preview.dataset.signature!==signature){preview.dataset.signature=signature;preview.replaceChildren();if(first)Array.from(first.children).forEach(function(child){preview.append(child.cloneNode(true));});else preview.textContent=signature;}}
  var spectrum=document.getElementById('saver-spectrum-fields'),saverRight=root.querySelector('[data-panel="saver"] .settings-columns>.settings-lane:nth-child(2)');if(spectrum&&saverRight){var spectrumCard=spectrum.closest('.ui-card');if(spectrumCard&&spectrumCard.parentElement!==saverRight)saverRight.append(spectrumCard);}
  var clean=document.getElementById('update-clean'),upload=document.getElementById('update-upload');if(clean&&upload){var advanced=clean.closest('details'),uploadCard=upload.closest('.ui-card');if(advanced&&uploadCard&&advanced.previousElementSibling!==uploadCard)uploadCard.after(advanced);}
  columns.forEach(function(element){if(!element.isConnected){if(resize)resize.unobserve(element);columns.delete(element);}else measure(element);});
 }
 new MutationObserver(function(records){if(queued||!records.some(function(record){return record.addedNodes.length;}))return;queued=true;requestAnimationFrame(function(){queued=false;refresh();});}).observe(root,{childList:true,subtree:true});
 document.addEventListener('aurora-settings-ready',function(){ready=true;refresh();});window.addEventListener('resize',refresh);window.addEventListener('hashchange',refresh);window.addEventListener('pagehide',function(){if(resize)resize.disconnect();columns.clear();});
 window.AuroraLayout={PageContainer:PageContainer,CardGrid:CardGrid,SettingsColumns:SettingsColumns,refresh:refresh};refresh();
}());
