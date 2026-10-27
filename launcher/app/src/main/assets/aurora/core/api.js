'use strict';
(function () {
  // Android 9 TV currently ships Chromium 66 (replaceChildren arrived much later).
  if (!Element.prototype.replaceChildren) Element.prototype.replaceChildren = function () {
    while (this.firstChild) this.removeChild(this.firstChild);
    for (var i = 0; i < arguments.length; i++) this.appendChild(typeof arguments[i] === "string" ? document.createTextNode(arguments[i]) : arguments[i]);
  };

  var mock = new URLSearchParams(location.search).get('mock') === '1';
  var listeners = [], timer, socket, themeContext=null, eventGeneration=0, polling=false;
  function context(path,data){if(themeContext===null){if(path==='config')themeContext=data.theme;else if(path==='bootstrap')themeContext=data.config.theme;}return data;}
  function mockTime() {
    var now = new Date();
    return {epochMs: +now, time: now.toLocaleTimeString('zh-CN', {hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'Asia/Shanghai'}),
      date: now.toLocaleDateString('zh-CN', {timeZone: 'Asia/Shanghai'}), weekday: now.toLocaleDateString('zh-CN', {weekday: 'long', timeZone: 'Asia/Shanghai'}),
      lunar: '农历由电视原生层提供 · 当前为演示', timezone: 'Asia/Shanghai'};
  }
  async function request(path, body) {
    if(mock){var envelope=await window.AuroraMock.response(path,body);if(!envelope.ok){var failure=Error(envelope.error.message);failure.code=envelope.error.code;throw failure;}return context(path,envelope.data);}
    var options = {cache: 'no-store', credentials: 'same-origin'};
    if (body !== undefined) {options.method = 'POST'; options.headers = {'Content-Type': 'application/json'}; options.body = JSON.stringify(body);if(themeContext)options.headers['X-Aurora-Theme']=themeContext;}
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timeout = controller ? setTimeout(function () {controller.abort();}, path==='system/screenshots/capture'?60000:10000) : null;
    if (controller) options.signal = controller.signal;
    try {
      var response = await fetch('/api/v1/' + path, options), value = await response.json();
      if(response.status===401&&location.pathname.indexOf('/console/')===0)location.replace('/console/login.html?next='+encodeURIComponent(location.pathname+location.hash));
      if (!response.ok || !value.ok) {var error = Error(value.error && value.error.message || '电视暂时没有响应'); error.code = value.error && value.error.code; throw error;}
      return context(path,value.data);
    } finally {if (timeout) clearTimeout(timeout);}
  }
  function stopEvents() {
    eventGeneration++;
    clearInterval(timer); timer = null;
    if (socket) {socket.onclose = null; socket.close(); socket = null;}
  }
  function openEvents() {
    stopEvents();
    if (!listeners.length || document.hidden) return;
    var generation=eventGeneration;
    function notify(data) {listeners.forEach(function (fn) {fn(data);});}
    function poll() {
      if(generation!==eventGeneration||document.hidden)return;
      clearInterval(timer);
      timer = setInterval(async function () {
        if(document.hidden||generation!==eventGeneration||polling)return;
        polling=true;
        try {
          var snapshot={}, paths={time:'time',media:'media',weather:'weather',appearance:'appearance',config:'config',saver:'saver'};
          for(var key of Object.keys(paths)){
            if(document.hidden||generation!==eventGeneration)return;
            snapshot[key]=await request(paths[key]);
          }
          if(!document.hidden&&generation===eventGeneration)notify(snapshot);
        } catch (ignored) {} finally {polling=false;}
      }, 3000);
    }
    if (mock) {poll(); return;}
    try {
      var connection = new WebSocket('ws://' + location.host + '/api/v1/events');socket=connection;
      connection.onmessage = function (event) {if(generation!==eventGeneration)return;try {var value = JSON.parse(event.data); if (value.type === 'snapshot') notify(value.data);} catch (ignored) {}};
      connection.onclose = poll;
      connection.onerror = function () {connection.close();};
    } catch (unavailable) {poll();}
  }
  function events(callback) {listeners.push(callback); if (listeners.length === 1) openEvents();}
  document.addEventListener('visibilitychange', function () {if (document.hidden) stopEvents(); else openEvents();});
  window.addEventListener('aurora-hidden', stopEvents);
  window.addEventListener('aurora-visible', openEvents);
  window.addEventListener('pagehide', stopEvents);
  window.AuroraAPI = {request: request, events: events, mock: mock, mockResponse:function(path,body){return window.AuroraMock.response(path,body);}};
}());
