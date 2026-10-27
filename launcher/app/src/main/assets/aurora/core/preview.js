'use strict';
(function () {
  var frame = document.getElementById('canvas'), resolution = document.getElementById('resolution'), viewport = document.getElementById('viewport'), version;
  function resize() {var width = Number(resolution.value), height = width * 9 / 16, scale = viewport.clientWidth / width; frame.style.width = width + 'px'; frame.style.height = height + 'px'; frame.style.transform = 'scale(' + scale + ')'; viewport.style.height = height * scale + 'px';}
  function reload() {frame.src = '/themes/' + document.getElementById('theme').value + '/index.html?mock=1';}
  document.getElementById('reload').onclick = reload; document.getElementById('theme').onchange = reload; resolution.onchange = resize; window.addEventListener('resize', resize); resize();
  document.addEventListener('keydown', function (event) {if (/^Arrow/.test(event.key) || event.key === 'Enter') {if (/^(SELECT|INPUT)$/.test(event.target.tagName)) return; event.preventDefault(); frame.contentDocument.dispatchEvent(new KeyboardEvent('keydown', {key: event.key, bubbles: true}));}});
  setInterval(async function () {if (document.hidden) return; try {var response = await fetch('/__version', {cache: 'no-store'}); if (!response.ok) return; var current = (await response.json()).version; if (version && version !== current) reload(); version = current;} catch (ignored) {}}, 1000);
}());
