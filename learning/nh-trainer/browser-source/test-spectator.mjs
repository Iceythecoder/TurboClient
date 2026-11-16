import {chromium} from 'playwright';
import {spawn} from 'node:child_process';
import {readFileSync,existsSync,mkdirSync} from 'node:fs';
import {resolve} from 'node:path';
const root=resolve('../../..');
const jar=resolve(root,readFileSync(resolve(root,'release-current.txt'),'utf8').trim());
const client=resolve(root,readFileSync(resolve(root,'release-runtime.txt'),'utf8').trim());
const java='C:/Users/ruben/AppData/Local/Orion/jre/bin/java.exe';
const child=spawn(java,[`-Dowl.project.root=${root}`,'-Dlogback.configurationFile='+resolve(root,'prototype-logback.xml'),'-cp',jar+';'+client,'owl.nh.BrowserFightSmoke',...(process.argv.includes('--scripted')?['--scripted']:[])],{cwd:root,windowsHide:true});
let browser;const errors=[],requests=[];
try {
 const url=await new Promise((yes,no)=>{let output='';const timeout=setTimeout(()=>no(new Error('Spectator startup timeout')),45000);child.stdout.on('data',d=>{output+=d;const match=output.match(/SPECTATOR_URI=(http:\/\/[^\s]+)/);if(match){clearTimeout(timeout);yes(match[1]);}});child.on('exit',code=>{clearTimeout(timeout);no(new Error('Spectator exited '+code));});});
 const executable=['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe','C:/Program Files/Microsoft/Edge/Application/msedge.exe'].find(existsSync);
 if(!executable)throw new Error('Edge browser not found for rendering check');
 browser=await chromium.launch({executablePath:executable,headless:true,args:['--disable-gpu-sandbox']});
 const context=await browser.newContext({viewport:{width:1240,height:1000}});const page=await context.newPage();
 page.on('pageerror',e=>errors.push(e.message));page.on('response',r=>{if(r.status()>=400)requests.push(r.status()+' '+r.url());});
 await page.goto(url);await page.waitForFunction(()=>window.__owlReady||window.__owlError,{},{timeout:30000});
 const failure=await page.evaluate(()=>window.__owlError);if(failure)throw new Error(failure);
 await page.waitForFunction(()=>window.__owlRendered&&window.__owlFrame?.tick>1);
 const firstInventory=await page.evaluate(async()=>{const data=await(await fetch('api/frame')).json();const inv=data.frame?.fighters?.[0]?.inventory;if(!inv||inv.length!==28)throw new Error('Actual agent inventory not available');return JSON.stringify(inv);});
 await page.waitForFunction(async original=>{const data=await(await fetch('api/frame')).json();return data.frame?.fighters?.[0]?.inventory&&JSON.stringify(data.frame.fighters[0].inventory)!==original;},firstInventory,{timeout:15000});
 const first=await page.evaluate(()=>window.__owlFrame);
 await page.waitForFunction(t=>window.__owlFrame.tick>t,first.tick,{timeout:10000});
 await page.waitForFunction(()=>window.__owlCounters.attacks>0&&window.__owlCounters.hits>0&&window.__owlCounters.prayers>0,{},{timeout:25000});
 mkdirSync(resolve(root,'build/browser-viewer-check'),{recursive:true});await page.screenshot({path:resolve(root,'build/browser-viewer-check/live-fight.png'),fullPage:true});
 if(process.argv.includes('--scripted')){
  await page.waitForFunction(()=>{const m=window.__owlMotion,p=window.__owlPositions;return m?.nativeDD&&!m.renderedDD&&m.progress>0&&m.progress<1&&p&&Math.abs(p[0].x-p[0].toX)+Math.abs(p[0].z-p[0].toZ)>1e-5;},{},{timeout:25000});
  const movingDD=await page.evaluate(()=>({motion:window.__owlMotion,label:document.getElementById('dd-status').textContent,caption:document.getElementById('progress').textContent}));
  if(!['walk','run'].includes(movingDD.motion.animations[0]))throw new Error('DD travel does not play movement animation');
  if(movingDD.label.includes('DD confirmed')||movingDD.caption.includes('DD confirmed'))throw new Error('DD confirmed before visual arrival');

  await page.waitForFunction(()=>document.getElementById('agent').textContent.includes('DD confirmed'),{},{timeout:25000});
  const dd=await page.evaluate(()=>({positions:window.__owlPositions,card:document.getElementById('agent').textContent}));
  if(Math.abs(dd.positions[0].x-dd.positions[1].x)>1e-6||Math.abs(dd.positions[0].z-dd.positions[1].z)>1e-6)throw new Error('Rendered DD actors remain on different tiles');
  if(!dd.card.includes('Distance 0 tiles'))throw new Error('DD card does not show exact overlap');
 }
 const state=await page.evaluate(async()=>{const s=await(await fetch('api/frame')).json();return {display:window.__owlFrame,source:s.frame};});
 if(state.display.episode!==state.source.episode||Math.abs(state.display.tick-state.source.tick)>1)throw new Error('Browser frame is stale');
 const wrong=await page.request.get(new URL('/wrong-token/api/frame',url).href);if(wrong.status()!==404)throw new Error('Spectator route token bypass');
 const post=await page.request.post(url+'api/frame');if(post.status()!==405)throw new Error('Spectator permits mutations');
 const escape=await page.request.get(url+'%2e%2e/release-current.txt');if(escape.status()!==404)throw new Error('Static path escape');
 if(errors.length||requests.length)throw new Error('Browser errors: '+JSON.stringify({errors,requests}));
 console.log('Browser game viewer passed: actual live ticks advance, source/display aligned, executed attacks, landed hits and overhead icons render; read-only routes protected.');
}finally{if(browser)await browser.close();child.stdin.end('quit\n');await new Promise(r=>{child.on('exit',r);setTimeout(()=>{child.kill();r();},5000).unref();});}
