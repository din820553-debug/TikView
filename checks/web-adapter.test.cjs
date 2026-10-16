const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const resolver = fs.readFileSync('app/src/main/assets/current-video.js', 'utf8');
const adapter = fs.readFileSync('app/src/main/assets/comments-panel.js', 'utf8');
const link = 'https://www.tiktok.com/@author/video/123456789';
function resolve(href, videos = [], body = {}) {
  return vm.runInNewContext(resolver, {URL, location: {href}, innerWidth: 400, innerHeight: 800,
    document: {body, querySelectorAll: () => videos}});
}
function video(top, links) {
  const parent = {parentElement:null, querySelectorAll:() => links.map(href=>({href}))};
  return {parentElement:parent, querySelectorAll:()=>[],
    getBoundingClientRect:()=>({top,bottom:top+700,left:0,right:400})};
}
test('detail URL is canonicalized without tracking or comments query', () => {
  assert.equal(resolve(link+'?foo=bar#comments'), link);
});
test('preloaded offscreen video is ignored in favor of visible player', () => {
  assert.equal(resolve('https://www.tiktok.com/', [video(900, ['https://www.tiktok.com/@next/video/456']), video(0,[link])]), link);
});
test('multiple unrelated video links must never select the wrong comment thread', () => {
  assert.equal(resolve('https://www.tiktok.com/', [video(0,[link,'https://www.tiktok.com/@other/video/456'])]), null);
});
test('absence of a visible player does not reuse a stale canonical link', () => {
  assert.equal(resolve('https://www.tiktok.com/', [video(900,[link])]), null);
});
test('reject foreign origins, credentials, unsafe ports and non-video paths', () => {
  for (const href of ['https://tiktok.com.evil.test/@a/video/123', 'http://www.tiktok.com/@a/video/123',
    'https://x@www.tiktok.com/@a/video/123','https://www.tiktok.com:8443/@a/video/123','https://www.tiktok.com/@a']) {
    assert.equal(resolve(href), null, href);
  }
});
test('no comment markup: preserve login/captcha page and stop observing after timeout', () => {
  let disconnected = false;
  let timeout;
  const document = {querySelector:()=>null, documentElement:{}};
  vm.runInNewContext(adapter, {window:{},document,
    MutationObserver:class {observe(){} disconnect(){disconnected=true;}},
    setTimeout:fn=>{timeout=fn;return 1;}, clearTimeout:()=>{}});
  assert.equal(typeof timeout, 'function');
  timeout();
  assert.equal(disconnected,true);
});
test('adapt a real list and keep its composer; hide the video column only', () => {
  function element(tag) {
    return {tagName:tag, attributes:{}, children:[], style:{setProperty(){}},
      setAttribute(k,v){this.attributes[k]=v;}, removeAttribute(k){delete this.attributes[k];}, querySelector(){return null;}};
  }
  const body=element('BODY'), container=element('DIV'), side=element('DIV');
  const list=element('DIV'), composer=element('TEXTAREA'), player=element('VIDEO');
  function append(parent,...children) {parent.children.push(...children); for(const n of children)n.parentElement=parent;}
  append(body,container); append(container,player,side); append(side,list,composer);
  side.querySelector=s=>s==='video'?null:composer;
  container.querySelector=s=>s==='video'?player:composer;
  let insertedStyle;
  const document={body,documentElement:element('HTML'), head:{appendChild(s){insertedStyle=s;}},
    querySelector:()=>list, querySelectorAll:()=>[],getElementById:()=>null,createElement:()=>element('STYLE')};
  vm.runInNewContext(adapter,{window:{},document,clearTimeout(){},setTimeout(){throw Error('unexpected timer');}});
  assert.ok(insertedStyle.textContent.includes('data-tv-panel'));
  assert.equal(side.attributes['data-tv-panel'],'1');
  assert.equal(player.attributes['data-tv-hidden'],'1');
  assert.equal(composer.attributes['data-tv-hidden'],undefined);
});
test('comment icon click opens native panel without following app-install link', () => {
  const script=fs.readFileSync('app/src/main/assets/feed-controls.js','utf8');
  let handler, prevented=false, stopped=false;
  class Element {closest(){return this;}}
  const location={href:link};
  const window={};
  const context={window,location,Element,document:{addEventListener(type,fn,capture){assert.equal(type,'click'); assert.equal(capture,true);handler=fn;}}};
  vm.runInNewContext(script,context);
  handler({target:new Element(),preventDefault(){prevented=true;},stopImmediatePropagation(){stopped=true;}});
  assert.equal(location.href,'tikview://comments');
  assert.ok(prevented && stopped);
  context.document.addEventListener=()=>{throw Error('duplicate listener');};
  vm.runInNewContext(script,context);
});
