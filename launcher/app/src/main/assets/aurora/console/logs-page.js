'use strict';
(function(){
  var api=window.AuroraAPI,UI=window.AuroraConsoleUI,panel=document.createElement('section'),rows=[],busy=false;
  panel.dataset.panel='logs';panel.hidden=true;
  function node(tag,text){var n=document.createElement(tag);if(text!==undefined)n.textContent=text;return n;}
  var title=node('h2','操作日志'),status=node('p'),search=node('input'),refresh=node('button','刷新'),download=node('button','导出完整日志'),clear=node('button','清空日志'),tools=node('div'),list=node('div');
  search.type='search';search.placeholder='搜索操作、IP 或详情';search.setAttribute('aria-label','搜索日志');clear.className='danger';tools.className='actions';tools.append(search,refresh,download,clear);panel.append(title,UI.card('访问与操作记录','显示最近 500 条，导出包含保留的完整记录。',[tools]),status,list);document.getElementById('settings-main').append(panel);
  function render(){list.replaceChildren();var query=search.value.trim().toLowerCase(),found=rows.filter(function(r){return [r.ip,r.action,r.detail,r.status].join(' ').toLowerCase().indexOf(query)>=0;});if(!found.length){list.append(node('p','暂无匹配的操作记录'));return;}found.forEach(function(r){list.append(UI.row(r.action,new Date(r.time).toLocaleString()+' · '+r.ip+' · '+r.status,node('span',r.detail||'—')));});}
  async function load(){if(busy)return;busy=true;refresh.disabled=true;status.textContent='正在读取日志…';try{rows=await api.request('logs');render();status.textContent='已读取 '+rows.length+' 条记录';}catch(e){status.textContent=(e.code==='NOT_IMPLEMENTED'?'暂不支持：':'读取失败：')+e.message;}finally{busy=false;refresh.disabled=false;}}
  refresh.onclick=load;search.oninput=render;
  download.onclick=function(){if(api.mock){status.textContent='模拟数据不包含电视日志，连接电视后可导出。';return;}var link=node('a');link.href='/api/v1/logs/export';link.download='aurora-operation-log.jsonl';document.body.append(link);link.click();link.remove();};
  clear.onclick=async function(){if(!await window.AuroraDrawer.confirm({title:'清空操作日志？',message:'将删除保留的访问和操作记录，清空操作本身仍会被记录。',confirmLabel:'清空日志'}))return;clear.disabled=true;try{await api.request('logs/clear',{confirm:true});await load();}catch(e){status.textContent='清空失败：'+e.message;}finally{clear.disabled=false;}};
  document.querySelector('[data-page="logs"]').addEventListener('click',function(){document.getElementById('native-category').hidden=true;document.querySelectorAll('[data-panel]').forEach(function(p){p.hidden=p!==panel;});load();});
  if(location.hash==='#logs')document.querySelector('[data-page="logs"]').click();
}());
