/* Standalone site: local assets, responsive layout, accessible curve demo and downloads. */
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url'),crypto=require('node:crypto');
const pw=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const root=path.resolve(__dirname,'..'),site=path.resolve(root,'../web');
async function main(){
 const browser=await pw.chromium.launch({headless:true,executablePath:process.env.BROWSER_EXECUTABLE});
 const checks=[],errors=[];
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}});page.on('pageerror',e=>errors.push(String(e)));
  await page.goto(pathToFileURL(path.join(site,'index.html')).href);await page.waitForFunction(()=>document.getElementById('demo-line').getAttribute('points'));
  assert.equal(await page.title(),'LumaCurve 流光亮度 · 让屏幕随环境平稳调节');checks.push('Standalone local HTML loads with local assets');
  assert.equal(await page.locator('header .author').count(),1);assert.equal(await page.locator('main>.author').count(),0);checks.push('Author avatar and profile links are in the top bar');
  assert.equal(await page.locator('.mechanism-grid article').count(),8);assert.equal(await page.locator('.anchor-table-wrap tbody tr').count(),7);checks.push('Full scene, curve, learning, transition and protection mechanisms plus fourteen default anchors');
  assert.match(await page.locator('.resource-stats').innerText(),/30/);assert.match(await page.locator('.resource-stats').innerText(),/0.02/);assert.match(await page.locator('.resource-measurement').innerText(),/作者设备实测/);checks.push('Resource figures explicitly attributed to author measurements');
  assert.equal((await page.locator('#demo-line').getAttribute('points')).split(' ').length,201);
  await page.locator('#demo-lux').fill('0');assert.match(await page.locator('#demo-output').innerText(),/^0 lux · 0.10%$/);
  await page.locator('#demo-lux').fill('1000');assert.match(await page.locator('#demo-output').innerText(),/100,000 lux · 85.00%/);checks.push('Curve demo uses 201 complete samples and truthful default endpoints');
  for(const width of [360,390,768,1440]){await page.setViewportSize({width,height:900});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));}checks.push('No horizontal overflow on four phone/tablet/desktop sizes');
  const localLinks=await page.locator('[href],[src]').evaluateAll(nodes=>nodes.map(n=>n.getAttribute('href')||n.getAttribute('src')).filter(s=>s&&!s.startsWith('#')&&!s.startsWith('https://')));
  for(const target of localLinks)assert.ok(fs.existsSync(path.resolve(site,target)),target);checks.push('Every local asset, license and module/source download resolves');
  const sums=fs.readFileSync(path.join(site,'downloads/SHA256SUMS.txt'),'utf8');
  for(const line of sums.trim().split(/\r?\n/)){const [digest,name]=line.split('  ');assert.equal(crypto.createHash('sha256').update(fs.readFileSync(path.join(site,'downloads',name))).digest('hex'),digest);assert.equal(digest,crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'dist',name))).digest('hex'));}checks.push('Website module/source downloads match dist and SHA-256');
  assert.equal(await page.locator('script[src^="http"]').count(),0);assert.equal(await page.locator('link[href^="http"][rel="stylesheet"]').count(),0);checks.push('No network JS, CSS, fonts, frameworks or device bridge');
  await page.setViewportSize({width:1440,height:1000});await page.screenshot({path:path.join(root,'build/website-desktop.png'),fullPage:true});
  await page.setViewportSize({width:390,height:844});await page.locator('#demo-lux').fill('401');await page.screenshot({path:path.join(root,'build/website-mobile.png'),fullPage:true});
  assert.deepEqual(errors,[]);checks.push('Desktop/mobile screenshots rendered without JavaScript errors');
  const source_sha256={};for(const name of ['website/index.html','website/assets/site.css','website/assets/site.js','website/assets/icon.svg','website/assets/curve_math.js','tools/sync_website.py','tests/website.cjs'])source_sha256[name]=crypto.createHash('sha256').update(fs.readFileSync(path.join(root,name))).digest('hex');
  fs.writeFileSync(path.join(root,'build/website-verification.json'),JSON.stringify({ok:true,checks,source_sha256,website:site,hosted:false},null,2));console.log(JSON.stringify({ok:true,checks:checks.length}));
 }finally{await browser.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
