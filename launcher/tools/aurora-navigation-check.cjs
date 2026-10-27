const fs=require("fs"),vm=require("vm"),assert=require("assert"),path=require("path");
const source=fs.readFileSync(path.join(__dirname,"../app/src/main/assets/web/app.js"),"utf8");
const initial=source.split("\n").find(s=>s.startsWith("var initialPage="));assert(initial);
const ids=["overview","layout","appearance","music","ambient","backgrounds","apps","usb","system","devices","logs","advanced","security"];
for(const current of ids){const nodes=Object.fromEntries(ids.map(id=>[id,{hidden:false}]));nodes["page-title"]={};nodes["page-description"]={};let removed=false;const doc={documentElement:{dataset:{}},getElementById:id=>id==="section-boot-style"?{remove(){removed=true;}}:nodes[id]};vm.runInNewContext(initial,{pages:Object.fromEntries(ids.map(id=>[id,[id,id,id]])),pageHash:()=>current,$:id=>nodes[id],document:doc});assert(removed);for(const id of ids)assert.equal(nodes[id].hidden,id!==current);assert.equal(doc.documentElement.dataset.sectionReady,current);}
assert(source.indexOf(initial)<source.indexOf("(async()=>{try{await loadState()"));
console.log("PASS all 13 settings categories selected before network/state loading");
