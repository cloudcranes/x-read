"""Execute the real frontend script; syntax-only checks miss handler failures."""
import json
from pathlib import Path
import shutil
import subprocess

import pytest

PAGE = Path(__file__).resolve().parents[1] / 'x_read/web/reader.html'


@pytest.mark.parametrize('storage_blocked', [False, True])
def test_startup_and_directory_click(storage_blocked):
    node = shutil.which('node')
    if not node:
        pytest.skip('Node.js required for frontend runtime test')
    script = PAGE.read_text().split('<script>')[-1].split('</script>')[0]
    harness = r'''
const vm = require('node:vm');
const assert = require('node:assert/strict');
const elements = new Map();
function element() {
  return {value:'',style:{},children:[],innerHTML:'',textContent:'',
    appendChild(x){this.children.push(...(x && x.__frag ? x.children : [x]))},
    querySelector(){return null},
    scrollIntoView(){},
    classList:{remove(){},toggle(){},add(){},contains(){return true}},
    addEventListener(){}};
}
function fragment() {
  const f = {children:[], appendChild(n){this.children.push(...(n && n.__frag ? n.children : [n]))}};
  Object.defineProperty(f, '__frag', {value:true});
  return f;
}
const get = id => {if(!elements.has(id)) elements.set(id, element()); return elements.get(id)};
let calls = 0;
const context = {console, document:{getElementById:get,createElement:element,createDocumentFragment:fragment,querySelectorAll:()=>[]},
  window:{addEventListener(){},scrollTo(){}},
  localStorage:{getItem(){if(BLOCKED) throw Error('storage disabled');return null},setItem(){if(BLOCKED) throw Error('storage disabled')}},
  fetch:async()=>{calls++;return {ok:true,json:async()=>({data:[]})}},alert(){}};
vm.createContext(context);
vm.runInContext(SCRIPT, context);
assert.ok(calls >= 1, 'startup must fetch shelf even without browser storage');
vm.runInContext("saveToken(); closeAllDrawers();", context);
vm.runInContext(`
 state.directory=[{chapter_id:'11',title:'First'},{chapter_id:'12',title:'Second'}];
 buildToc();
`, context);
let opened;
context.loadChapter = id => {opened=id};
get('chapter-list').children[1].onclick();
assert.equal(opened, '12', 'directory click must reach chapter loader');
'''
    harness = harness.replace('BLOCKED', json.dumps(storage_blocked)).replace('SCRIPT', json.dumps(script))
    result = subprocess.run([node, '-e', harness], capture_output=True, text=True, timeout=15)
    assert result.returncode == 0, result.stderr


def test_frontend_copies_match():
    assert PAGE.read_bytes() == (PAGE.parents[2]/'reader.html').read_bytes()
