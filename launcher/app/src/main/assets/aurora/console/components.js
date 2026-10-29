'use strict';
/* Enhance in place: never clone controls, replace handlers or change API calls. */
(function () {
  var root=document.getElementById('settings-main')||document.querySelector('main');
  if(!root)return;
  var hints={
    'security-open':'仅限可信局域网；关闭后恢复账号密码保护',
    'security-username':'用于登录此设备的控制中心',
    'security-password':'留空保留原密码',
    'security-confirm':'与上方新密码保持一致',
    'security-port':'有效范围 1024～65535；修改后访问新端口',
    'security-path':'可留空；设置后统一使用该入口登录',
    'update-auto-check':'后台检查版本，不等于自动安装',
    'update-automatic':'桌面空闲时安装；仍受设备能力限制',
    'input-shortcut-visible':'控制桌面应用区的输入源入口',
    'input-shortcut-id':'快捷方式直接进入此接口',
    'input-shortcut-hint':'切换后提醒使用盒子遥控器',
    'idle-standby-enabled':'播放音视频时暂停计时；有效遥控操作重新计时',
    'idle-standby-minutes':'最后一分钟显示可取消的待机倒计时',
    'desktop-locale':'仅修改电视桌面的显示语言',
    'ui-locale':'仅修改网页控制中心的语言',
    'bgm-volume':'播放器独立音量，不改变电视原来的音量',
    'bgm-enabled':'屏保音乐与桌面音乐软件独立',
    'bgm-autoplay':'进入桌面后按当前音乐设置播放',
    'bgm-fade':'控制播放、停止和切换时的过渡时长',
    'traffic-store':'仅在本机保存历史，按保留天数清理',
    'traffic-dns':'可能向系统 DNS 查询目标 IP 的域名',
    'traffic-retention':'到期自动清理，不影响原始系统计数'
  };
  var descriptions={
    '启用无操作屏保':'停止操作且没有播放其他音乐时进入屏保',
    '播放方式':'选择闲置后进入，或在桌面持续播放',
    '无操作分钟数':'有效遥控器操作会重新计时',
    '屏保信息':'控制时间、日期、天气等信息的显示程度',
    '屏保时间字体':'独立于桌面时间字体，所有主题的屏保共用',
    '屏保时间独立颜色':'仅用于屏保左上角的时间',
    '屏保时间字号':'按电视画面比例缩放时间',
    '问候语内容':'留空时不显示问候语',
    '问候语字体风格':'只影响屏保问候语',
    '问候语字号':'改变屏保问候语的大小',
    '水平位置百分比':'0 为最左侧，100 为最右侧',
    '垂直位置百分比':'0 为顶部，100 为底部',
    '屏保歌词排版':'选择单行、滚动或隐藏歌词',
    '中文字体':'使用系统字体，或在素材中上传字体文件',
    '英文字体':'单独设置拉丁字母与数字使用的字体',
    '基础字号':'仅应用于当前主题',
    '文字颜色':'歌词颜色单独设置，不受此项影响',
    '图标大小':'改变应用图标的显示大小',
    '区块间距':'调整区块之间的留白',
    '字号缩放':'按比例调整当前主题的文字大小',
    '卡片透明度':'调整卡片底色的浓淡，不影响文字',
    '毛玻璃模糊':'柔化卡片后方的背景细节',
    '背景压暗':'降低背景亮度，让前景内容更突出',
    '卡片圆角':'改变卡片边角的弧度',
    '边框粗细':'改变焦点边框的粗细',
    '焦点圆角':'改变选中框边角的弧度',
    '发光强度':'控制焦点周围光晕的大小',
    '过渡时长':'控制焦点移动到新位置所需的时间',
    '氛围灯强度':'改变背景氛围光的强弱',
    '已唱歌词':'已经唱完的歌词使用此颜色',
    '正在唱的歌词':'当前高亮歌词使用此颜色',
    '未唱歌词':'尚未唱到的歌词使用此颜色',
    '本主题强调色':'当前主题按钮和重点元素使用的颜色',
    '播放卡片颜色':'仅修改当前主题的音乐卡片',
    '选中框样式':'设置遥控器当前焦点的外观',
    '焦点移动动画':'设置遥控器切换焦点时的过渡效果',
    '氛围灯取色':'可关闭，或选择颜色的来源',
    '频谱样式':'选择此频谱的呈现方式',
    '渐变起始色':'多色渐变的起点',
    '渐变结束色':'多色渐变的终点',
    '透明度':'数值越小，频谱越淡',
    '尺寸百分比':'按画面比例缩放频谱',
    '渐变方向（度）':'改变渐变颜色排列的方向',
    '自动朗读':'使用所选语音引擎朗读界面内容',
    '朗读焦点名称':'移动遥控器焦点时朗读控件名称',
    '朗读语言':'与桌面显示语言独立设置',
    '语音引擎':'声音来源取决于设备上安装的引擎',
    '朗读声音':'可选声音由所选引擎提供',
    '语速':'1 为正常语速',
    '音调':'1 为原始音调',
    '朗读音量':'相对于电视当前音量调节',
    '屏保歌曲':'仅使用单独上传的屏保音乐',
    '绑定歌词':'可选择已上传的 LRC 文件',
    '试听歌曲':'试听不会修改屏保歌曲的绑定',
    '最多显示应用数':'应用会按可用空间排列；排序与隐藏独立设置'
  };
  function node(tag,cls,text){var n=document.createElement(tag);if(cls)n.className=cls;if(text)n.textContent=text;return n;}
  function enhanceLabel(label){
    if(label.closest('[data-component-skip],.setting-row,.color-dialog,.color-picker,[role=dialog]'))return;
    var control=label.querySelector('input,select,textarea,button');
    if(!control||control.type==='hidden')return;
    // Keep the outer label: range handlers use closest('label').
    var texts=Array.from(label.childNodes).filter(function(n){return n.nodeType===3;});
    var title=texts.map(function(n){return n.textContent;}).join(' ').trim();
    if(!title)title=control.getAttribute('aria-label')||control.getAttribute('placeholder');
    if(!title)return;
    var copy=node('span','setting-row-copy'),name=node('span','setting-row-title',title),slot=node('span','setting-row-control');
    texts.forEach(function(n){n.remove();});
    while(label.firstChild)slot.append(label.firstChild);
    copy.append(name);
    var hint=hints[control.id]||label.dataset.description||descriptions[title];

    if(hint){var detail=node('span','setting-row-description',hint);copy.append(detail);if(control.id){detail.id=control.id+'-description';control.setAttribute('aria-describedby',detail.id);}}
    if(!control.getAttribute('aria-label')&&!control.id)control.setAttribute('aria-label',title);
    label.classList.add('setting-row');
    if(control.tagName==='TEXTAREA')label.classList.add('ui-editor-row');
    if(control.type==='checkbox')control.classList.add('ui-switch');
    label.append(copy,slot);
  }
  function enhanceCard(box){
    if(box.classList.contains('ui-card'))return;
    box.classList.add('ui-card');
    var title=Array.from(box.children).find(function(n){return /^H[23]$/.test(n.tagName);});
    if(title){var heading=node('div','ui-card-heading');title.before(heading);heading.append(title);}
  }
  function enhanceExistingRow(row){
    if(row.querySelector('.setting-row-copy'))return;
    var title=row.querySelector('.setting-label');
    if(!title)return;
    var copy=node('div','setting-row-copy'),slot=node('div','setting-row-control');
    title.classList.add('setting-row-title');copy.append(title);
    while(row.firstChild)slot.append(row.firstChild);
    row.append(copy,slot);
    if(slot.querySelector('textarea'))row.classList.add('ui-editor-row');
    var checkbox=slot.querySelector('input[type=checkbox]');if(checkbox)checkbox.classList.add('ui-switch');
  }
  function groupPage(panel){
    if(panel.dataset.componentReady)return;
    panel.dataset.componentReady='true';panel.classList.add('ui-migrated');
    // Use existing functional blocks as card boundaries, then group unwrapped
    // fields by their original headings. IDs and control objects remain intact.
    panel.querySelectorAll('.device-block,.settings-card').forEach(enhanceCard);
    if(panel.dataset.panel==='services')return;
    var card=null;
    Array.from(panel.children).forEach(function(child){
      if(child.matches('h2,.heading')){card=null;return;}
      if(child.matches('p')&&!card)return;
      if(child.matches('.ui-card,.gallery,iframe,.traffic-scroll,canvas,[data-component-skip]')){card=null;return;}
      if(child.tagName==='H3'){
        var folded=panel.dataset.panel==='saver';
        card=node(folded?'details':'section','ui-card');
        var heading=node(folded?'summary':'div',folded?'':'ui-card-heading');
        child.before(card);
        if(folded){heading.textContent=child.textContent;child.remove();}
        else heading.append(child);
        card.append(heading);return;
      }
      if(!card){card=node('section','ui-card');child.before(card);}
      card.append(child);
    });
  }
  function scan(){
    root.querySelectorAll('[data-panel],.page,#native-clock-settings').forEach(groupPage);
    root.querySelectorAll('.device-block,.settings-card').forEach(enhanceCard);
    root.querySelectorAll('.setting-row').forEach(enhanceExistingRow);
    root.querySelectorAll('label').forEach(enhanceLabel);
    root.querySelectorAll('input[type=number],input[type=range]').forEach(function(input){if(input.min!==''||input.max!=='')input.title=(input.getAttribute('aria-label')||'数值')+' · '+input.min+'～'+input.max;});
    if(window.AuroraConsolePolish)window.AuroraConsolePolish.refresh();
    root.querySelectorAll('p').forEach(function(p){if(p.querySelector('label.setting-row'))p.classList.add('ui-row-group');});
    // A few original controls deliberately had no wrapping label. Give them
    // the same accessible setting row without moving command toolbars.
    [['saver-track','屏保歌曲'],['saver-lrc','绑定歌词'],['bgm-library','试听歌曲']].forEach(function(item){
      var control=document.getElementById(item[0]);
      if(!control||control.closest('.setting-row'))return;
      var label=node('label');control.before(label);label.append(document.createTextNode(item[1]),control);enhanceLabel(label);
    });
  }
  var queued=false;
  new MutationObserver(function(records){
    if(!records.some(function(r){return r.addedNodes.length;}))return;
    if(queued)return;queued=true;
    requestAnimationFrame(function(){queued=false;scan();});
  }).observe(root,{childList:true,subtree:true});
  scan();
  window.AuroraComponents={refresh:scan,enhanceLabel:enhanceLabel,enhanceCard:enhanceCard};
}());
