'use strict';
(function(){
 var root=document.getElementById('settings-main'),queued=false,columns=new Set(),ready=!!window.AuroraSettingsView;if(!root)return;
 function PageContainer(element){element.classList.add('page-container');return element;}
 function CardGrid(element){element.classList.add('card-grid');return element;}
 function measure(element){var width=element.getBoundingClientRect().width;if(width)element.classList.toggle('is-single-column',width<900);}
 var resize=window.ResizeObserver?new ResizeObserver(function(entries){entries.forEach(function(entry){measure(entry.target);});}):null;
 function SettingsColumns(element){element.classList.add('settings-columns');if(!columns.has(element)){columns.add(element);if(resize)resize.observe(element);}measure(element);return element;}
 function settingsRuns(parent){
  var group=null;
  Array.from(parent.children).forEach(function(child){
   var card=child.matches('.ui-card,.device-block')&&!child.matches('.background-pane,.theme-card,.app-tile,.theme-list-wrapper,.traffic-chart-card,.traffic-table-card')&&!child.querySelector('.gallery,.card-grid,.theme-cards,.apps-grid,.traffic-metrics,table');
   if(!card){group=null;return;}
   if(!group){group=document.createElement('div');SettingsColumns(group);child.before(group);}group.append(child);
  });
 }
 function refresh(){
  var workspace=document.getElementById('console-workspace');PageContainer(workspace||root);if(workspace)root.classList.remove('page-container');
  root.querySelectorAll('.theme-cards,.apps-grid,.gallery,.ui-status-grid,.traffic-metrics,.traffic-summary-cards,.traffic-totals,#capture-gallery,#asset-gallery').forEach(CardGrid);
  if(ready)root.querySelectorAll('[data-panel],.device-tab-panel').forEach(function(parent){if(parent.dataset.panel!=='background'&&!parent.classList.contains('device-control-columns'))settingsRuns(parent);});
  root.querySelectorAll('.device-control-columns').forEach(function(element){if(!columns.has(element)){columns.add(element);if(resize)resize.observe(element);}measure(element);});
  columns.forEach(function(element){if(!element.isConnected){if(resize)resize.unobserve(element);columns.delete(element);}else measure(element);});
 }
 new MutationObserver(function(records){if(queued||!records.some(function(record){return record.addedNodes.length;}))return;queued=true;requestAnimationFrame(function(){queued=false;refresh();});}).observe(root,{childList:true,subtree:true});
 document.addEventListener('aurora-settings-ready',function(){ready=true;refresh();});window.addEventListener('resize',refresh);window.addEventListener('hashchange',refresh);window.addEventListener('pagehide',function(){if(resize)resize.disconnect();columns.clear();});
 window.AuroraLayout={PageContainer:PageContainer,CardGrid:CardGrid,SettingsColumns:SettingsColumns,refresh:refresh};refresh();
}());
