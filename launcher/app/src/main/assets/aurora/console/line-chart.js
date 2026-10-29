'use strict';
(function(){
 var entries=new WeakMap(),M=window.AuroraTrafficModel;
 function render(canvas,points,gapMs){
  var old=entries.get(canvas),entry={points:points||[],gapMs:gapMs||90000,index:old&&old.index!=null?Math.min(old.index,points.length-1):null};entries.set(canvas,entry);
  if(!old){canvas.addEventListener('pointermove',function(e){var state=entries.get(canvas);if(!state||!state.points.length)return;var r=canvas.getBoundingClientRect(),relative=(e.clientX-r.left-68)/(r.width-84),start=state.points[0].time,end=state.points[state.points.length-1].time,target=start+relative*(end-start),nearest=0;state.points.forEach(function(p,i){if(Math.abs(p.time-target)<Math.abs(state.points[nearest].time-target))nearest=i;});state.index=nearest;paint(canvas);});canvas.addEventListener('pointerleave',function(){var state=entries.get(canvas);if(state)state.index=null;paint(canvas);});canvas.addEventListener('keydown',function(e){if(e.key!=='ArrowLeft'&&e.key!=='ArrowRight'&&e.key!=='Escape')return;var state=entries.get(canvas);if(!state||!state.points.length)return;e.preventDefault();state.index=e.key==='Escape'?null:Math.max(0,Math.min(state.points.length-1,(state.index==null?state.points.length-1:state.index)+(e.key==='ArrowLeft'?-1:1)));paint(canvas);});canvas.addEventListener('blur',function(){var state=entries.get(canvas);if(state)state.index=null;paint(canvas);});}
  paint(canvas);
 }
 function paint(canvas){
  var entry=entries.get(canvas);if(!entry||!canvas.clientWidth)return;var points=entry.points,width=Math.max(220,canvas.clientWidth),height=204,dpr=Math.min(2,window.devicePixelRatio||1),ctx=canvas.getContext('2d'),style=getComputedStyle(document.documentElement),muted=style.getPropertyValue('--ui-muted').trim()||'#6c7475',border=style.getPropertyValue('--ui-border').trim()||'#e3e7e8';
  canvas.width=Math.round(width*dpr);canvas.height=height*dpr;ctx.scale(dpr,dpr);ctx.clearRect(0,0,width,height);ctx.font='11px system-ui,sans-serif';ctx.fillStyle=muted;ctx.lineWidth=1;
  var max=1024;points.forEach(function(p){if(M.valid(p.rx))max=Math.max(max,p.rx);if(M.valid(p.tx))max=Math.max(max,p.tx);});max=Math.ceil(max/1024)*1024;
  for(var i=0;i<=3;i++){var y=18+i*49;ctx.strokeStyle=border;ctx.beginPath();ctx.moveTo(68,y);ctx.lineTo(width-16,y);ctx.stroke();ctx.fillText(M.rate(max*(3-i)/3),0,y+4);}
  var tooltip=canvas.parentElement.querySelector('.ui-chart-tooltip');if(!points.length){if(tooltip)tooltip.hidden=true;return;}
  var start=points[0].time,end=points[points.length-1].time;if(end===start)end=start+1000;
  function x(p){return 68+(p.time-start)/(end-start)*(width-84);}function pointY(n){return 165-n/max*147;}
  [['rx','#16aeb8'],['tx','#9480e9']].forEach(function(series){ctx.strokeStyle=series[1];ctx.lineWidth=2;ctx.beginPath();var previous=null;points.forEach(function(p){if(!M.valid(p[series[0]])){previous=null;return;}if(!previous||p.time-previous.time>entry.gapMs)ctx.moveTo(x(p),pointY(p[series[0]]));else ctx.lineTo(x(p),pointY(p[series[0]]));previous=p;});ctx.stroke();});
  [start,(start+end)/2,end].forEach(function(t,i){ctx.textAlign=i===2?'right':i===1?'center':'left';ctx.fillStyle=muted;ctx.fillText(new Date(t).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}),i===0?68:i===1?(width+52)/2:width-16,193);});ctx.textAlign='left';
  if(entry.index!=null&&points[entry.index]){var point=points[entry.index],px=x(point);ctx.strokeStyle=muted;ctx.lineWidth=1;ctx.setLineDash([3,3]);ctx.beginPath();ctx.moveTo(px,18);ctx.lineTo(px,165);ctx.stroke();ctx.setLineDash([]);[['rx','#16aeb8'],['tx','#9480e9']].forEach(function(series){if(M.valid(point[series[0]])){ctx.fillStyle=series[1];ctx.beginPath();ctx.arc(px,pointY(point[series[0]]),3,0,Math.PI*2);ctx.fill();}});if(tooltip){tooltip.textContent=new Date(point.time).toLocaleTimeString('zh-CN')+' · 下载 '+M.rate(point.rx)+' · 上传 '+M.rate(point.tx);tooltip.style.left=Math.max(0,Math.min(width-250,px-110))+'px';tooltip.hidden=false;}}
  else if(tooltip)tooltip.hidden=true;
 }
 window.AuroraLineChart={render:render};
}());
