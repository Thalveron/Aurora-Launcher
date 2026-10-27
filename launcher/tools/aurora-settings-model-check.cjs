const fs=require('fs'),vm=require('vm'),assert=require('assert');
const text=fs.readFileSync(__dirname+'/../app/src/main/assets/aurora/console/settings.js','utf8'),start=text.indexOf('function reconcile('),end=text.indexOf('  async function save(',start),scope={};
vm.createContext(scope);vm.runInContext(text.slice(start,end)+';this.merge=reconcile;',scope);
const model={greeting:'before',saverSpectrum:{size:60,stops:[{position:20,color:'#ffffff'}]}},field=model,nested=model.saverSpectrum,stop=nested.stops[0];
scope.merge(model,JSON.parse(JSON.stringify(model)));
field.greeting='after second edit';nested.size=160;stop.color='#00ffff';
assert.equal(model.greeting,'after second edit');assert.equal(model.saverSpectrum.size,160);assert.equal(model.saverSpectrum.stops[0].color,'#00ffff');
assert.strictEqual(model.saverSpectrum,nested);assert.strictEqual(model.saverSpectrum.stops[0],stop);
console.log('PASS saved response preserves root, nested controls and gradient stop bindings for subsequent edits');
