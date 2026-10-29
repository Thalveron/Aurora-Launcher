/* Cards-only visual layout editor; percentages refer to the full TV viewport. */
(function(){
var defaults={clock:{x:4,y:11,w:40,h:18},weather:{x:46,y:11,w:50,h:18},music:{x:4,y:33,w:40,h:56},apps:{x:46,y:33,w:25,h:26},shortcuts:{x:73,y:33,w:23,h:26},detail:{x:46,y:62,w:25,h:27},info:{x:73,y:62,w:23,h:27}};
window.AuroraFreeLayout=function(o){
 var old=document.getElementById('free-layout-tools');if(old)old.remove();
 o.box.classList.remove('free-layout-canvas');if(o.theme!=='cards')return;
 var tools=document.createElement('div');tools.id='free-layout-tools';tools.className='free-layout-tools';
 var note=document.createElement('p');note.textContent='仅简约卡片：拖动区块换位置，拖右下角改变大小。松手更新预览，点击底部保存后应用；数值以整个电视画面的百分比表示。区块可重叠，请保留应用操作空间。';tools.append(note);
 var toggle=document.createElement('button');toggle.textContent=o.value.freeform?'恢复网格布局':'启用拖动与缩放';tools.append(toggle);
 var snap=document.createElement('input');snap.type='checkbox';snap.checked=true;var sl=document.createElement('label');sl.append(snap,document.createTextNode(' 吸附到 1% 网格'));tools.append(sl);
 var fields=document.createElement('div');fields.className='free-layout-fields';tools.append(fields);
 o.box.before(tools);
 toggle.onclick=function(){if(o.value.freeform)delete o.value.freeform;else o.value.freeform=JSON.parse(JSON.stringify(defaults));o.change();o.save();window.AuroraFreeLayout(o);};
 if(!o.value.freeform)return;
 o.box.classList.add('free-layout-canvas');o.box.replaceChildren();
 var selected,inputs={},items={};
 function paint(k){var r=o.value.freeform[k],n=items[k];n.style.left=r.x+'%';n.style.top=r.y+'%';n.style.width=r.w+'%';n.style.height=r.h+'%';if(selected===k)Object.keys(inputs).forEach(function(a){inputs[a].value=r[a];});}
 function limit(r){r.w=Math.max(8,Math.min(100,r.w));r.h=Math.max(8,Math.min(100,r.h));r.x=Math.max(0,Math.min(100-r.w,r.x));r.y=Math.max(0,Math.min(100-r.h,r.y));Object.keys(r).forEach(function(a){r[a]=Math.round(r[a]*100)/100;});}
 function choose(k){selected=k;Object.keys(items).forEach(function(a){items[a].classList.toggle('selected',a===k);});fields.replaceChildren();var title=document.createElement('strong');title.textContent=o.names[k];fields.append(title);inputs={};[['x','横向位置'],['y','纵向位置'],['w','宽度'],['h','高度']].forEach(function(pair){var label=document.createElement('label');label.textContent=pair[1]+' %';var input=document.createElement('input');input.type='number';input.min=pair[0]==='w'||pair[0]==='h'?8:0;input.max=100;input.step=.1;input.value=o.value.freeform[k][pair[0]];inputs[pair[0]]=input;input.onchange=function(){if(!isFinite(+input.value))return;var r=o.value.freeform[k];r[pair[0]]=+input.value;limit(r);paint(k);o.change();o.save();};label.append(input);fields.append(label);});}
 o.value.order.forEach(function(k){
  if(!o.value.freeform[k])o.value.freeform[k]=JSON.parse(JSON.stringify(defaults[k]));if(o.value.hidden.indexOf(k)>=0)return;
  var n=document.createElement('div');n.className='free-layout-block';n.tabIndex=0;n.textContent=o.names[k];n.setAttribute('aria-label',o.names[k]+'：拖动移动，右下角缩放');items[k]=n;var handle=document.createElement('span');handle.className='free-layout-handle';handle.textContent='◢';handle.title='拖动调整大小';n.append(handle);o.box.append(n);paint(k);n.onfocus=function(){choose(k);};
  n.onpointerdown=function(e){if(e.button!==0)return;e.preventDefault();choose(k);var resize=e.target===handle,start={x:e.clientX,y:e.clientY},rect=Object.assign({},o.value.freeform[k]),bounds=o.box.getBoundingClientRect();n.setPointerCapture(e.pointerId);
   n.onpointermove=function(ev){var dx=(ev.clientX-start.x)/bounds.width*100,dy=(ev.clientY-start.y)/bounds.height*100,r=o.value.freeform[k];if(resize){r.w=Math.min(100-rect.x,rect.w+dx);r.h=Math.min(100-rect.y,rect.h+dy);}else{r.x=rect.x+dx;r.y=rect.y+dy;}if(snap.checked)Object.keys(r).forEach(function(a){r[a]=Math.round(r[a]);});limit(r);paint(k);o.change();};
   function finish(){n.onpointermove=null;n.onpointerup=null;n.onpointercancel=null;o.save();}
   n.onpointerup=finish;n.onpointercancel=finish;
  };
 });
 if(Object.keys(items).length)choose(Object.keys(items)[0]);
};
})();
