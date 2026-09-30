/* Browser contract tests. Node + Playwright; production WebUI has no npm dependency. */
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const os = require('node:os');
const {spawnSync} = require('node:child_process');
const {pathToFileURL} = require('node:url');
const root = path.resolve(__dirname, '..');
const pw = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const template = fs.readFileSync(path.join(root, 'module/luma_curve.conf'), 'utf8') + '# 自定义配置应保留\nfuture_option=keep\n';
const checks = [];
function pass(name) { checks.push({name, ok: true}); }
function configShellContract(commands) {
  const command=commands.find(c=>c.startsWith('umask 077;'));
  const payloads=[Buffer.from(command.match(/\[ "\$current" = '([^']+)'/)[1],'base64'),Buffer.from(command.match(/printf %s '([^']+)' \| base64 -d/)[1],'base64')];
  const folder=fs.mkdtempSync(path.join(os.tmpdir(),'luma-config-'));
  const file=path.join(folder,'luma_curve.conf');
  const native=folder.replace(/\\/g,'/').replace(/^([A-Za-z]):/,(_,drive)=>'/'+drive.toLowerCase());
  const script=command.replaceAll('/data/local/tmp/luma_curve',native+'/luma_curve');
  const bash=process.env.BASH_EXECUTABLE||(process.platform==='win32'?'C:/msys64/usr/bin/bash.exe':'bash');
  const run=code=>{
    const result=spawnSync(bash,['-c','export PATH=/usr/bin:/bin:$PATH; '+code],{encoding:'utf8'});
    if(result.error)throw result.error;
    return result;
  };
  try {
    fs.writeFileSync(file,payloads[0]);
    let result=run(script);assert.equal(result.status,0,result.stderr);
    assert.deepEqual(fs.readFileSync(file),payloads[1]);
    assert.deepEqual(fs.readdirSync(folder),['luma_curve.conf']);
    result=run(script);assert.notEqual(result.status,0);
    assert.match(result.stderr,/配置已被其他操作修改/);
    assert.deepEqual(fs.readFileSync(file),payloads[1]);
    fs.writeFileSync(file,payloads[0]);
    result=run('base64() { return 1; }; '+script);assert.notEqual(result.status,0);
    assert.match(result.stderr,/无法读取配置文件进行校验/);
    assert.deepEqual(fs.readFileSync(file),payloads[0]);
    assert.deepEqual(fs.readdirSync(folder),['luma_curve.conf']);
    const read=commands.find(c=>c.startsWith('base64 ')).replace('/data/local/tmp/luma_curve',native+'/luma_curve');
    result=run(read);assert.equal(result.status,0,result.stderr);
    assert.deepEqual(Buffer.from(result.stdout.trim().replace(/\s/g,''),'base64'),payloads[0]);
    pass('Actual shell commands preserve bytes, atomically save, reject genuine conflicts and report comparison failures separately');
  } finally {fs.rmSync(folder,{recursive:true,force:true});}
}
async function edit(page,key,value) {
  const button=page.locator('#edit-'+key);
  await button.evaluate(el=>{const d=el.closest('details');if(d)d.open=true;});
  await button.click();
  await page.locator('#editor-number').fill(value);
  await page.click('#editor-confirm');
  await page.waitForFunction(()=>!document.getElementById('setting-dialog').open);
}
function presetShellContract(commands) {
  const writes=commands.filter(c=>c.startsWith('umask 077;')&&c.includes('_presets.json'));
  assert.equal(writes.length,2);const folder=fs.mkdtempSync(path.join(os.tmpdir(),'luma-presets-'));
  const native=folder.replace(/\\/g,'/').replace(/^([A-Za-z]):/,(_,drive)=>'/'+drive.toLowerCase());
  const bash=process.env.BASH_EXECUTABLE||(process.platform==='win32'?'C:/msys64/usr/bin/bash.exe':'bash');
  const run=command=>spawnSync(bash,['-c','export PATH=/usr/bin:/bin:$PATH; '+command.replaceAll('/data/local/tmp/luma_curve',native+'/luma_curve')],{encoding:'utf8'});
  const file=path.join(folder,'luma_curve_presets.json');
  try {
    let result=run(writes[0]);assert.equal(result.status,0,result.stderr);assert.equal(JSON.parse(fs.readFileSync(file,'utf8')).entries[0].name,'我的日常');
    result=run(writes[1]);assert.equal(result.status,0,result.stderr);assert.equal(JSON.parse(fs.readFileSync(file,'utf8')).entries.length,0);
    const changed='{"format":1,"entries":[],"changed":true}';fs.writeFileSync(file,changed);result=run(writes[1]);assert.notEqual(result.status,0);assert.match(result.stderr,/预设已被修改/);assert.equal(fs.readFileSync(file,'utf8'),changed);
    assert.deepEqual(fs.readdirSync(folder),['luma_curve_presets.json']);
    pass('Real Bash preset writes preserve UTF-8, create/delete atomically, reject concurrent changes and clean temporary files');
  } finally {fs.rmSync(folder,{recursive:true,force:true});}
}
async function main() {
  const browser = await pw.chromium.launch({executablePath: process.env.BROWSER_EXECUTABLE || undefined, headless: true});
  try {
    const preview = await browser.newPage({viewport: {width:390,height:844}, reducedMotion:'reduce'});
    const errors = []; preview.on('pageerror', e => errors.push(String(e)));
    const url = pathToFileURL(path.join(root,'module/webroot/index.html')).href;
    await preview.goto(url);
    assert.equal(await preview.locator('.bar-corner').count(),0);
    assert.deepEqual(await preview.locator('.header,.navigation').evaluateAll(items=>items.map(el=>{
      const c=getComputedStyle(el);return [c.borderTopLeftRadius,c.borderTopRightRadius,c.borderBottomRightRadius,c.borderBottomLeftRadius];
    })),[['0px','0px','18px','18px'],['18px','18px','0px','0px']]);
    pass('Header lower corners center upward and navigation upper corners center downward, both toward the horizontal middle');
    assert.equal(await preview.locator('[data-command="resume"]').isDisabled(), true);
    assert.equal(await preview.locator('#panel-status [data-command]').count(),0);
    assert.equal(await preview.locator('#panel-settings [data-command]').count(),3);
    await preview.click('#demo-button');
    assert.equal(await preview.locator('#brightness').innerText(), '42.0');
    assert.equal(await preview.locator('#connection').innerText(), '示例状态');
    pass('Browser preview is explicitly labelled and device actions are disabled');
    for (const width of [320,390,768,1280]) {
      await preview.setViewportSize({width,height:844});
      for (const panel of ['status','settings','tools','about']) {
        await preview.click('#nav-'+panel);
        if (panel==='settings') await preview.locator('#panel-settings details').evaluateAll(items => items.forEach(d => d.open=true));
        assert.equal(await preview.evaluate(()=>document.documentElement.scrollWidth > innerWidth), false, `${width} ${panel} overflows`);
      }
    }
    pass('All four pages and expanded settings fit 320/390/768/1280 px');
    await preview.click('#nav-tools');
    assert.deepEqual(await preview.evaluate(()=>['body','.header','.navigation'].map(s=>getComputedStyle(document.querySelector(s)).backgroundColor)),['rgb(250, 250, 250)','rgb(250, 250, 250)','rgb(250, 250, 250)']);
    await preview.evaluate(()=>scrollTo(0,600));
    assert.equal(Math.round((await preview.locator('.header').boundingBox()).y),0);
    assert.equal(await preview.locator('#nav-tools').innerText(),'日志');
    assert.equal(await preview.locator('#panel-tools .help-list').count(),0);
    assert.equal(await preview.locator('#panel-settings .page-intro').count(),0);
    pass('FAFAFA base surfaces, sticky header and simplified settings/log pages');
    for(const width of [320,390,768]){
      await preview.setViewportSize({width,height:844});
      const heights=[];
      for(const panel of ['status','settings','tools']){
        await preview.click('#nav-'+panel);
        heights.push((await preview.locator('.header').boundingBox()).height);
      }
      assert.ok(heights.every(h=>Math.abs(h-heights[0])<.5),`${width}: inconsistent header heights ${heights}`);
    }
    pass('Status, Settings and Logs share the same header height on phone and desktop widths');

    await preview.setViewportSize({width:390,height:844});
    await preview.click('#nav-status');
    assert.equal(await preview.locator('#readings-dialog').evaluate(el=>el.open),false);
    assert.equal(await preview.locator('.signal-flow').count(),0);
    assert.equal(await preview.locator('#panel-status').innerText().then(text=>text.includes('亮度如何得来')),false);
    assert.equal(await preview.locator('#front-wire').getAttribute('data-active'),'true');
    assert.equal(await preview.locator('#back-wire').getAttribute('data-active'),'false');
    assert.equal(await preview.locator('#engine-scene button:not(#show-readings), #engine-scene [role="button"], #engine-scene [tabindex]').count(),0);
    assert.equal(await preview.locator('#refresh-state, #live-status, [data-inspect]').count(),0);
    assert.equal(await preview.locator('#engine-scene').evaluate(el=>getComputedStyle(el).userSelect),'none');
    assert.equal(await preview.locator('#scene-smooth').textContent(),'112.8 lux');
    for (const width of [320,390,768]) {
      await preview.setViewportSize({width,height:844});
      const overlapping=await preview.evaluate(()=>{
        const scene=document.getElementById('engine-scene');
        const text=Array.from(scene.querySelectorAll('span,strong,small')).filter(el=>!el.children.length).map(el=>el.getBoundingClientRect());
        return Array.from(scene.querySelectorAll('.connector')).some(el=>{
          const lane=el.getBoundingClientRect();return text.some(r=>r.width&&r.height&&r.left<lane.right&&r.right>lane.left&&r.top<lane.bottom-.5&&r.bottom>lane.top+.5);
        });
      });
      assert.equal(overlapping,false,`${width}: text crosses connector lane`);
    }
    await preview.setViewportSize({width:390,height:844});
    pass('Display-only status graphic, no manual refresh or interval label, separate text and connector lanes');
    for (const panel of ['status','settings','tools','about']) {
      await preview.click('#nav-'+panel);
      if(panel==='tools')await preview.locator('.diagnostics-card').evaluate(el=>el.open=true);
      const unfilled=await preview.locator('button:visible:not([data-panel])').evaluateAll(items=>items.filter(el=>{
        const c=getComputedStyle(el);return c.backgroundColor==='rgba(0, 0, 0, 0)'||c.backgroundColor==='transparent';
      }).map(el=>el.id||el.textContent));
      assert.deepEqual(unfilled,[],`${panel}: button lacks background`);
      const uneven=await preview.locator('.button-row, .control-strip, .settings-secondary').evaluateAll(rows=>rows.filter(row=>{
        if(!row.getBoundingClientRect().height)return false;
        const r=Array.from(row.querySelectorAll(':scope > button')).map(el=>el.getBoundingClientRect());
        return r.some(x=>Math.abs(x.height-r[0].height)>.5||Math.abs(x.y-r[0].y)>.5||Math.abs(x.width-r[0].width)>1);
      }).length);
      assert.equal(uneven,0,`${panel}: grouped buttons do not align`);
    }
    assert.equal(await preview.locator('.navigation button').evaluateAll(items=>items.every(el=>getComputedStyle(el).backgroundColor==='rgba(0, 0, 0, 0)')),true);
    pass('Action buttons are filled and aligned; bottom navigation remains transparent');
    await preview.click('#nav-status');
    await preview.screenshot({path:path.join(root,'build/webui-status-mobile.png'),fullPage:true});
    await preview.click('#nav-about');
    assert.equal(await preview.locator('.author-card h2').innerText(), '戎Shang');
    assert.equal(await preview.locator('img[src="original-author.jpg"], .upstream-credit').count(),0);
    assert.equal(await preview.locator('a[href="https://lc.rongshangs.top"]').count(),1);
    assert.equal(await preview.locator('a[href="https://rongshangs.top"]').count(),1);
    assert.equal(await preview.locator('a[href="https://www.gnu.org/licenses/gpl-3.0.html"]').count(),1);
    assert.equal(await preview.locator('a[href="https://www.coolapk.com/u/3261403"]').count(),1);
    assert.equal(await preview.locator('#github-link').getAttribute('href'),'https://github.com/RongShangs/LumaCurve');
    assert.equal(await preview.evaluate(()=>{
      const order=['.about-intro','.project-links','.author-card','.author-links','.donation-card','.thanks-list'].map(s=>document.querySelector(s));
      return order.every((el,i)=>!i || !!(order[i-1].compareDocumentPosition(el)&Node.DOCUMENT_POSITION_FOLLOWING));
    }),true);
    assert.equal(await preview.locator('.header').isVisible(),false);
    assert.equal(await preview.locator('.about-heading .module-icon').count(),1);
    assert.equal(await preview.locator('.about-heading h1').innerText(),'流光亮度');
    pass('About hides the shared header and presents its own icon and title without punctuation');
    await preview.screenshot({path:path.join(root,'build/webui-about-mobile.png'),fullPage:true});
    assert.match(await preview.locator('.author-card').innerText(),/酷安@戎Shangs/);
    assert.match(await preview.locator('.donation-card').innerText(),/整条备注不超过 30 个字符/);
    assert.equal(await preview.locator('.thanks-list li').innerText(),'戒戒：喵喵喵？');
    pass('Current author, links, GPL, donation note and one-line thank-you entry');
    assert.equal(await preview.evaluate(()=>!!(document.querySelector('.auto-update').compareDocumentPosition(document.querySelector('.project-links'))&Node.DOCUMENT_POSITION_FOLLOWING)),true);
    for(const type of ['wechat','alipay']){
      await preview.click('#donate-'+type);
      await preview.waitForFunction(()=>document.getElementById('donation-qr').complete && document.getElementById('donation-qr').naturalWidth>0);
      assert.equal(await preview.locator('#donation-qr').getAttribute('src'),'donate-'+type+'.jpg');
      assert.equal(await preview.locator('#donation-title').innerText(),type==='wechat'?'微信打赏':'支付宝打赏');
      if(type==='wechat')await preview.click('#close-donation');else await preview.keyboard.press('Escape');
      assert.equal(await preview.locator('#donation-dialog').isVisible(),false);
      assert.equal(await preview.locator('#donate-'+type).evaluate(el=>el===document.activeElement),true);
    }
    pass('Automatic update line precedes project links; free optional support opens local QR codes with close, Escape and focus restoration');

    let update={version:'1.0.0',versionCode:10000,zipUrl:'https://github.com/RongShangs/LumaCurve/releases/download/v1.0.0/luma_curve-1.0.0.zip'};
    await preview.route('https://raw.githubusercontent.com/RongShangs/LumaCurve/main/update.json',route=>route.fulfill({json:update}));
    await preview.click('#nav-status');
    await preview.click('#nav-about');
    await preview.waitForFunction(()=>document.getElementById('update-status').textContent.includes('已是最新'));
    assert.equal(await preview.locator('#update-download').isVisible(),false);
    update={version:'1.0.1',versionCode:10001,zipUrl:'https://github.com/RongShangs/LumaCurve/releases/download/v1.0.1/luma_curve-1.0.1.zip'};
    await preview.click('#nav-status');
    await preview.click('#nav-about');
    await preview.waitForFunction(()=>document.getElementById('update-status').textContent.includes('发现 1.0.1'));
    assert.equal(await preview.locator('#update-download').getAttribute('href'),update.zipUrl);
    update.zipUrl='https://untrusted.example/package.zip';
    await preview.click('#nav-status');
    await preview.click('#nav-about');
    await preview.waitForFunction(()=>document.getElementById('update-status').textContent.includes('更新信息格式异常'));
    assert.equal(await preview.locator('#update-download').isVisible(),false);
    await preview.unroute('**/update.json');
    await preview.route('**/update.json',route=>route.abort());
    await preview.click('#nav-status');
    await preview.click('#nav-about');
    await preview.waitForFunction(()=>document.getElementById('update-status').textContent.includes('自动检查更新：') && !document.getElementById('update-status').textContent.includes('检查更新中'));
    assert.equal(await preview.locator('#check-update').count(),0);
    pass('Update checks handle current/new versions, reject foreign download URLs and recover after network failure');

    const connected = await browser.newPage({viewport:{width:390,height:844},reducedMotion:'reduce'});
    connected.on('pageerror', e=>errors.push(String(e)));
    await connected.addInitScript(({template})=>{
      window.fixture={config:template,commands:[],writes:0,paused:false,failControl:false,failAtomic:false,failState:false,failExport:false,lux:128.4,clipboard:'',overrides:{},browserResolve:'com.android.chrome/com.google.android.apps.chrome.Main',browserQuery:'',failBrowser:false};
      Object.defineProperty(navigator,'clipboard',{value:{writeText:async text=>{window.fixture.clipboard=text;}}});
      window.ksu={exec(command, options, callback) {
        const f=window.fixture;f.commands.push(command);
        let errno=0,stdout='',stderr='';
        if(command.startsWith('umask 077;')) {
          const encoded=[command.match(/\[ "\$current" = '([^']*)'/)[1],command.match(/printf %s '([^']+)' \| base64 -d/)[1]];
          const payloads=encoded.map(value=>decodeURIComponent(Array.from(atob(value),c=>'%'+c.charCodeAt(0).toString(16).padStart(2,'0')).join('')));
          if(command.includes('_presets.json')) {
            if(f.failAtomic || payloads[0] !== (f.presets || '').trimEnd()) {errno=1;stderr='预设已被其他操作修改';}
            else {f.presets=payloads[1];f.presetWrites=(f.presetWrites||0)+1;}
          } else if(f.failAtomic || payloads[0]!==f.config) {errno=1;stderr='配置已被其他操作修改，请重新读取';}
          else {f.config=payloads[1];f.writes++;}
        } else if(command.startsWith('umask 022;')) {
          if(f.failExport){errno=1;stderr='下载目录不可写';}
          else stdout='/storage/emulated/0/Download/LumaCurve-test.log';
        } else if(command.startsWith('cmd package resolve-activity')) stdout=f.browserResolve;
        else if(command.startsWith('cmd package query-activities')) stdout=f.browserQuery;
        else if(command.startsWith('am start ')) {
          if(command.includes('--selector')||f.failBrowser){errno=1;stderr='Error: Activity not started, unable to resolve Intent';}
          else stdout='Starting: Intent';
        }
        else if(command.endsWith('core-status')) stdout=f.coreStatus || (f.paused?'paused':'running');
        else if(command.includes('then printf paused; else printf active; fi')) stdout=f.paused?'paused':'active';
        else if(command.startsWith('sh ')) {
          if(f.failControl) {errno=1;stderr='控制脚本失败';}
          else {
            if(command.includes("'pause'"))f.paused=true;
            if(command.includes("'resume'"))f.paused=false;
            if(command.includes("'set-log-retention'"))f.config=f.config.replace(/^log_retention_days=.*$/m,'log_retention_days='+command.match(/'set-log-retention' '(\d+)'/)[1]);
          }
        } else if(command.startsWith('tail ')) stdout=f.log || '<script>window.injected=true</script>\n正常日志';
        else if(command.includes('_preference')) stdout=f.preference || '';
        else if(command.includes('_presets.json')) stdout=f.presets || '';
        else if(command.startsWith('base64 ')) stdout=btoa(unescape(encodeURIComponent(f.config))).match(/.{1,76}/g).join('\n')+'\n';
        else if(command.includes("luma_curve.conf'")) stdout=f.config;
        else if(command.includes('luma_curve_state')) {
          if(f.failState) {errno=1;stderr='状态读取失败';}
          else stdout='mode=auto\ncurrent_br=1720\ntarget_br=1860\nmax_br=4095\nlux='+f.lux+'\nsmooth=112.8\nbrightness_owner=daemon\ntransition_active=1\nsunlight_active=0\nheat_guard_active=0\nscreen=1\nlux_source=front\nlux_valid=1\nfront_lux='+f.lux+'\nback_lux=85.2\nfront_lux_age_ms=50\nback_lux_age_ms=80\nlux_age_ms=50\nsensor_rate_us=200000\nsensor_rate_mode=active\ntarget_poll_ms=500\nthermal_trusted_temp=38500\nbattery_pct=76\ncharging=0\ndaemon_can_write=1\ntarget_hold_active=0\nupdated_unix='+Math.floor(Date.now()/1000)+'\n_ui_paused='+(f.paused?1:0);
        }
        if(command.includes('luma_curve_state')&&!f.failState)stdout+='\n'+Object.entries(f.overrides).map(([key,value])=>key+'='+value).join('\n');
        // Reproduce managers that discard trailing newlines and use CRLF.
        if(command.startsWith('base64 ')||command.startsWith('cat '))stdout=stdout.replace(/\r?\n/g,'\r\n').trimEnd();
        setTimeout(()=>window[callback](errno,stdout,stderr),10);
      }};
    }, {template});
    await connected.goto(url);
    await connected.waitForFunction(()=>document.getElementById('connection').textContent==='运行中');
    await connected.click('#nav-settings');
    await connected.click('[data-command="pause"]');
    await connected.waitForFunction(()=>document.getElementById('connection').textContent==='已暂停');
    await connected.click('[data-command="resume"]');
    await connected.waitForFunction(()=>document.getElementById('connection').textContent==='运行中');
    await connected.click('#nav-status');
    pass('KernelSU pause/resume controls work from Settings and are absent from the status canvas');
    for(const [width,height] of [[320,640],[360,720],[390,844],[412,915]]){
      await connected.setViewportSize({width,height});
      await connected.evaluate(()=>scrollTo(0,0));
      const boxes=await connected.evaluate(()=>{
        const scene=document.getElementById('engine-scene').getBoundingClientRect();
        const nav=document.querySelector('.navigation').getBoundingClientRect();
        return {sceneTop:scene.top,sceneBottom:scene.bottom,navTop:nav.top};
      });
      assert.equal(await connected.evaluate(()=>document.scrollingElement.scrollHeight>innerHeight),false,`${width}x${height}: status page scrolls`);
      assert.equal(await connected.locator('#engine-scene').evaluate(el=>getComputedStyle(el).borderTopWidth),'0px');
      const story=await connected.locator('.scene-story').boundingBox();
      const phone=await connected.locator('.phone-display').boundingBox();
      assert.ok(story.y>=phone.y+phone.height,`${width}x${height}: story must follow phone`);
      assert.ok(story.y+story.height<=boxes.navTop,`${width}x${height}: story hidden under navigation`);
      assert.ok(boxes.sceneBottom<=boxes.navTop,`${width}x${height}: scene bottom ${boxes.sceneBottom} exceeds navigation ${boxes.navTop}`);
    }
    await connected.setViewportSize({width:390,height:844});
    await connected.evaluate(()=>document.getElementById('toast').classList.remove('show'));
    await connected.screenshot({path:path.join(root,'build/webui-status-device-mobile.png')});
    const placement=await connected.evaluate(()=>{
      const button=document.getElementById('show-readings'),age=document.getElementById('scene-heading');
      const b=button.getBoundingClientRect(),a=age.getBoundingClientRect();
      return {sameRow:button.parentElement===age.parentElement,left:b.right<=a.left,aligned:Math.abs(b.y+b.height/2-a.y-a.height/2)<1,height:b.height,width:b.width};
    });
    assert.ok(placement.sameRow&&placement.left&&placement.aligned);
    assert.ok(placement.height<=28&&placement.width<90);
    pass('Small readings action shares the story title row below the phone');
    assert.equal(await connected.locator('#panel-status #mode, #panel-status #owner').count(),0);
    await connected.click('#show-readings');
    assert.equal(await connected.locator('#readings-dialog').evaluate(el=>el.open),true);
    assert.equal(await connected.locator('#mode').innerText(),'自动亮度');
    await connected.click('#close-readings');
    await connected.click('#show-readings');
    await connected.keyboard.press('Escape');
    assert.equal(await connected.locator('#readings-dialog').evaluate(el=>el.open),false);
    await connected.evaluate(()=>scrollTo(0,200));
    assert.equal(await connected.evaluate(()=>scrollY),0);
    pass('Borderless fixed status page fits four phone viewports; details dialog closes by button or Escape');

    assert.equal(await connected.locator('#sensor-source').textContent(),'前置光感');
    assert.equal(await connected.locator('#sensor-interval').textContent(),'200 ms');
    assert.equal(await connected.locator('#device-temperature').textContent(),'38.5℃');
    assert.equal(await connected.locator('#device-battery').textContent(),'76% · 未充电');
    assert.equal(await connected.locator('#flow-target').textContent(),await connected.locator('#target').innerText());
    await connected.evaluate(()=>fixture.lux=200);
    await connected.waitForFunction(()=>document.getElementById('lux').textContent==='200.0',null,{timeout:3000});
    assert.equal(await connected.locator('#front-lux').textContent(),'200.0 lux');
    assert.equal(await connected.locator('#copy-state, #panel-status > .page-title').count(),0);
    pass('Sensor units, processing relationship, continuous visible refresh and no redundant title/copy row');
    await connected.evaluate(()=>fixture.overrides={scene_relation:'common_rise',scene_hold_active:0,front_reporting_mode:1,front_lux_age_ms:60000});
    await connected.waitForFunction(()=>document.getElementById('filter-label').textContent==='环境变亮');
    assert.equal(await connected.locator('#back-wire').getAttribute('data-active'),'true');
    assert.equal(await connected.locator('#front-age').textContent(),'距最后变化 60000 ms');
    await connected.evaluate(()=>fixture.overrides={scene_relation:'front_only',scene_hold_active:1,scene_hold_left_ms:0});
    await connected.waitForFunction(()=>document.getElementById('filter-label').textContent==='单侧确认中');
    assert.equal(await connected.locator('#filter-node').getAttribute('data-state'),'warning');
    await connected.evaluate(()=>fixture.overrides={scene_relation:'back_only',scene_hold_active:0});
    await connected.waitForFunction(()=>document.getElementById('filter-label').textContent==='正面优先');
    await connected.evaluate(()=>fixture.overrides={});
    await connected.waitForFunction(()=>document.getElementById('filter-label').textContent==='平滑滤波');
    pass('Scene relations render actual paired/unilateral states, including elapsed holds and honest on-change age');
    assert.ok(await connected.evaluate(()=>fixture.commands.some(c=>c.startsWith(": > '/data/local/tmp/luma_curve_ui_watch'; cat "))));
    await connected.evaluate(()=>{fixture.hidden=true;Object.defineProperty(document,'hidden',{get:()=>fixture.hidden,configurable:true});document.dispatchEvent(new Event('visibilitychange'));});
    await connected.waitForTimeout(50);
    const hiddenReads=await connected.evaluate(()=>fixture.commands.filter(c=>c.includes('luma_curve_state')).length);
    const hiddenLeases=await connected.evaluate(()=>fixture.commands.filter(c=>c.includes('luma_curve_ui_watch')).length);
    await connected.waitForTimeout(1200);
    assert.equal(await connected.evaluate(()=>fixture.commands.filter(c=>c.includes('luma_curve_state')).length),hiddenReads);
    assert.equal(await connected.evaluate(()=>fixture.commands.filter(c=>c.includes('luma_curve_ui_watch')).length),hiddenLeases);
    await connected.evaluate(()=>{fixture.hidden=false;fixture.lux=210;document.dispatchEvent(new Event('visibilitychange'));});
    await connected.waitForFunction(()=>document.getElementById('lux').textContent==='210.0',null,{timeout:1500});
    pass('Background visibility stops reads and foreground return refreshes immediately');
    pass('UI observation lease is renewed only by visible status reads and stops in the background');
    const targetPoint=await connected.locator('#response-target').getAttribute('cy');
    const currentPoint=await connected.locator('#response-current').getAttribute('cy');
    assert.ok(Math.abs(Number(targetPoint)-(120-1860/4095*108))<0.06);
    assert.ok(Math.abs(Number(currentPoint)-(120-1720/4095*108))<0.06);
    await connected.evaluate(()=>fixture.overrides={lux_source:'back',sunlight_active:1,hbm_active:1,heat_guard_active:1,thermal_trusted_temp:61000,target_hold_active:1,daemon_can_write:0,target_br:4095});
    await connected.waitForFunction(()=>document.getElementById('confirm-gate').dataset.state==='warning');
    assert.equal(await connected.locator('#front-wire').getAttribute('data-active'),'false');
    assert.equal(await connected.locator('#back-wire').getAttribute('data-active'),'true');
    assert.equal(await connected.locator('#sunlight-gate').getAttribute('data-state'),'active');
    assert.equal(await connected.locator('#thermal-gate').getAttribute('data-state'),'warning');
    assert.equal(await connected.locator('#output-wire').getAttribute('data-active'),'false');
    assert.equal(await connected.locator('#output-wire').getAttribute('data-blocked'),'true');
    assert.equal(await connected.locator('#response-target').getAttribute('cy'),'12.0');
    assert.equal(await connected.locator('#target').innerText(),'100.0%');
    assert.equal(await connected.locator('#device-temperature').textContent(),'61.0℃');
    assert.equal(await connected.locator('#scene-heading').innerText(),'正在限制亮度');
    assert.match(await connected.locator('#explanation').innerText(),/温度偏高/);
    await connected.screenshot({path:path.join(root,'build/webui-status-protected.png'),fullPage:true});
    await connected.evaluate(()=>fixture.overrides={lux_source:'back',sensor_stale:1,sensor_hold_active:1});
    await connected.waitForFunction(()=>document.getElementById('filter-node').dataset.state==='warning');
    assert.equal(await connected.locator('#back-wire').getAttribute('data-active'),'false');
    assert.equal(await connected.locator('#filter-label').innerText(),'保持亮度');
    assert.equal(await connected.locator('#scene-heading').innerText(),'暂时保持亮度');
    assert.match(await connected.locator('#explanation').innerText(),/先保持亮度/);
    assert.equal(await connected.locator('#screen-action').innerText(),'保持亮度');
    await connected.evaluate(()=>fixture.overrides={brightness_owner:'wake_readonly',sensor_stale:1,sensor_hold_active:1,daemon_can_write:0});
    await connected.waitForFunction(()=>document.getElementById('scene-heading').textContent==='刚刚亮屏');
    assert.match(await connected.locator('#explanation').innerText(),/先保留系统亮度/);
    assert.equal(await connected.locator('#screen-action').innerText(),'系统先调节');
    assert.equal(await connected.locator('#owner').textContent(),'唤醒观察');
    await connected.screenshot({path:path.join(root,'build/webui-status-waking.png')});
    pass('Wake observation takes priority over stale sensor wording and explains when the engine will take over');
    await connected.evaluate(()=>fixture.overrides={});
    await connected.waitForFunction(()=>document.getElementById('front-wire').dataset.active==='true');
    pass('Diagram uses real target/current coordinates and responds to source, thermal, sunlight, hold and stale flags');
    await connected.click('#nav-settings');
    await connected.waitForTimeout(50);
    const reads=await connected.evaluate(()=>fixture.commands.filter(c=>c.includes('luma_curve_state')).length);
    await connected.waitForTimeout(1200);
    assert.equal(await connected.evaluate(()=>fixture.commands.filter(c=>c.includes('luma_curve_state')).length),reads);
    pass('Status polling stops while another page is being viewed');
    await connected.locator('#edit-thermal_cap_enable').evaluate(el=>el.closest('details').open=true);
    await connected.click('#edit-thermal_cap_enable');
    await connected.locator('#editor-toggle').uncheck();
    await connected.click('#editor-confirm');
    assert.equal(await connected.locator('#edit-thermal_cap_enable').innerText(),'关闭');
    await connected.click('#edit-thermal_cap_enable');
    await connected.click('#editor-default');
    await connected.click('#editor-confirm');
    assert.equal(await connected.locator('#edit-thermal_cap_enable').innerText(),'启用');
    assert.equal(await connected.locator('#edit-indoor_stability').innerText(),'启用');
    await connected.click('#edit-indoor_stability');
    await connected.locator('#editor-toggle').uncheck();
    await connected.click('#editor-confirm');
    assert.equal(await connected.locator('#edit-indoor_stability').innerText(),'关闭');
    await connected.click('#edit-indoor_stability');
    await connected.click('#editor-default');
    await connected.click('#editor-confirm');
    assert.equal(await connected.locator('#edit-indoor_stability').innerText(),'启用');
    assert.match(await connected.locator('#edit-indoor_stability').evaluate(el=>el.closest('.field').textContent),/场景自适应/);
    pass('Scene adaptation retains the saved configuration key, defaults on and can be changed through the boolean editor');
    await connected.locator('#edit-hbm_node_path').evaluate(el=>el.closest('details').open=true);
    await connected.click('#edit-hbm_node_path');
    await connected.locator('#editor-text').fill('/data/invalid');
    await connected.click('#editor-confirm');
    assert.match(await connected.locator('#editor-error').innerText(),/必须位于 \/sys\//);
    await connected.locator('#editor-text').fill('/sys/class/backlight/panel0-backlight/hbm');
    await connected.click('#editor-confirm');
    assert.match(await connected.locator('#edit-hbm_node_path').innerText(),/\/sys\/class/);
    await connected.click('#edit-hbm_node_path');
    await connected.click('#editor-default');
    await connected.click('#editor-confirm');
    assert.equal(await connected.locator('#edit-hbm_node_path').innerText(),'自动检测');
    assert.equal(await connected.locator('.field .recommendation').count(),await connected.locator('.field-value').count());
    pass('All fields have recommendations; boolean/text modal validation and defaults work');
    const density=await connected.locator('.config-group').first().locator('.field').evaluateAll(rows=>rows.map(row=>({height:row.getBoundingClientRect().height,padding:parseFloat(getComputedStyle(row).paddingTop)})));
    assert.ok(density.every(row=>row.padding<=10&&row.height<=86),JSON.stringify(density));
    pass('Settings rows use compact spacing while retaining hints, recommendations and value editors');

    await connected.click('#edit-brighten_speed');
    assert.match(await connected.locator('#editor-recommended').innerText(),/0.8–1.4/);
    await connected.locator('#editor-range').evaluate(el=>{el.value='1.8';el.dispatchEvent(new Event('input',{bubbles:true}));});
    await connected.click('#editor-cancel');
    assert.equal(await connected.locator('#edit-brighten_speed').innerText(),'1倍');
    await connected.click('#edit-brighten_speed');
    await connected.locator('#editor-number').fill('1.4');
    await connected.screenshot({path:path.join(root,'build/webui-editor-mobile.png')});
    assert.equal(await connected.locator('.navigation').isVisible(),false);
    await connected.setViewportSize({width:390,height:520});
    const confirmBox=await connected.locator('#editor-confirm').boundingBox();
    assert.ok(confirmBox.y+confirmBox.height<=520);
    await connected.setViewportSize({width:390,height:844});
    await connected.click('#editor-confirm');
    await connected.waitForFunction(()=>!document.getElementById('setting-dialog').open);
    await connected.waitForFunction(()=>!document.documentElement.classList.contains('keyboard-open'));
    assert.equal(await connected.locator('.navigation').isVisible(),true);
    assert.equal(await connected.locator('#edit-brighten_speed').innerText(),'1.4倍');
    pass('Slider modal cancel/confirm, recommendations and keyboard-safe footer/dialog');
    await connected.click('#save-config');
    await connected.waitForFunction(()=>document.getElementById('dirty-status').textContent==='已保存并发送重载');
    assert.match(await connected.evaluate(()=>fixture.config), /brighten_speed=1.4/);
    assert.match(await connected.evaluate(()=>fixture.config), /future_option=keep/);
    assert.match(await connected.evaluate(()=>fixture.config), /自定义配置应保留/);
    assert.equal(await connected.evaluate(()=>fixture.writes),1);
    const commands=await connected.evaluate(()=>fixture.commands);
    assert.ok(commands.some(c=>c.includes('[ "$current" = ')&&c.includes('chmod 0600')&&c.includes('mv -f')&&!c.includes('cmp -s')));
    assert.ok(commands.some(c=>c.includes('/data/adb/modules/luma_curve/luma_curvectl.sh')&&c.includes("'reload-config'")));
    configShellContract(commands);
    pass('UTF-8 config save and readback preserve exact bytes despite bridge newline trimming and CRLF conversion');
    for (const ending of ['crlf','none']) {
      await connected.evaluate(ending=>{
        fixture.config=ending==='crlf'?fixture.config.replace(/\r?\n/g,'\r\n'):fixture.config.replace(/[\r\n]+$/,'');
      },ending);
      await connected.click('#reload-config');
      await connected.waitForFunction(()=>document.getElementById('dirty-status').textContent==='未修改'&&!document.getElementById('save-config').disabled);
      await edit(connected,'brighten_speed',ending==='crlf'?'1.5':'1.6');
      await connected.click('#save-config');
      await connected.waitForFunction(()=>document.getElementById('dirty-status').textContent==='已保存并发送重载');
      assert.match(await connected.evaluate(()=>fixture.config),/future_option=keep/);
    }
    assert.equal(await connected.evaluate(()=>fixture.writes),3);
    pass('Existing CRLF and missing-final-newline configurations can both be edited and saved');
    await connected.locator('#edit-thermal_hard_resume').evaluate(el=>el.closest('details').open=true);
    await connected.click('#edit-thermal_hard_resume');
    await connected.locator('#editor-number').fill('65');
    await connected.click('#editor-confirm');
    assert.match(await connected.locator('#editor-error').innerText(), /恢复温度必须低于/);
    assert.equal(await connected.evaluate(()=>fixture.writes),3);
    await connected.click('#editor-cancel');
    await connected.locator('#edit-high_lux_max_active_ms').evaluate(el=>el.closest('details').open=true);
    await connected.click('#edit-high_lux_max_active_ms');
    await connected.locator('#editor-number').fill('1');
    await connected.click('#editor-confirm');
    assert.match(await connected.locator('#editor-error').innerText(), /至少 3 秒/);
    await connected.click('#editor-cancel');
    pass('Invalid temperature relationship and sunlight duration are rejected without writes');
    await connected.evaluate(()=>fixture.config+='external_option=preserve\n');
    await connected.click('#save-config');
    await connected.waitForFunction(()=>document.getElementById('config-error').textContent.includes('保存失败'));
    assert.equal(await connected.evaluate(()=>fixture.writes),3);
    assert.match(await connected.evaluate(()=>fixture.config),/external_option=preserve/);
    await connected.click('#reload-config');
    await connected.waitForFunction(()=>document.getElementById('dirty-status').textContent==='未修改'&&!document.getElementById('save-config').disabled);
    await connected.evaluate(()=>fixture.failControl=true);
    await connected.click('#save-config');
    await connected.waitForFunction(()=>document.getElementById('config-error').textContent.includes('配置已写入'));
    assert.equal(await connected.evaluate(()=>fixture.writes),4);
    pass('Atomic conflict and reload failure never show a false success');
    await connected.evaluate(()=>{fixture.failControl=false;fixture.failState=true;});
    await connected.click('#nav-status');
    await connected.waitForFunction(()=>document.getElementById('connection').textContent==='状态不可用');
    assert.equal(await connected.locator('#brightness').innerText(),'—');
    for(const id of ['sensor-source','sensor-age','device-temperature','front-lux','flow-target'])assert.equal(await connected.locator('#'+id).textContent(),'—');
    assert.equal(await connected.locator('#raw-trend').getAttribute('points'),'');
    assert.equal(await connected.locator('#raw-dot').getAttribute('visibility'),'hidden');
    assert.equal(await connected.locator('#response-target').getAttribute('visibility'),'hidden');
    assert.equal(await connected.locator('#output-wire').getAttribute('data-active'),'false');
    await connected.evaluate(()=>fixture.paused=true);
    await connected.waitForFunction(()=>document.getElementById('connection').textContent==='已暂停');
    assert.match(await connected.locator('#explanation').innerText(),/设置页恢复引擎/);
    await connected.evaluate(()=>fixture.paused=false);
    await connected.evaluate(()=>fixture.failState=false);
    await connected.click('#nav-tools');
    await connected.waitForFunction(()=>document.getElementById('log').textContent.includes('正常日志'));
    assert.equal(await connected.locator('#panel-tools article').first().getAttribute('class'),'card log-view');
    assert.equal(await connected.locator('.diagnostics-card').getAttribute('open'),null);
    await connected.screenshot({path:path.join(root,'build/webui-logs-mobile.png'),fullPage:true});
    pass('Logs lead with recent content, aligned refresh/copy/export actions and collapsed diagnostics');
    assert.equal(await connected.evaluate(()=>window.injected),undefined);
    pass('State read failure clears stale metrics; logs render as text, never HTML');
    await connected.click('#copy-log');
    assert.equal(await connected.evaluate(()=>fixture.clipboard),'<script>window.injected=true</script>\n正常日志');
    await connected.click('#export-log');
    await connected.waitForFunction(()=>document.getElementById('export-status').textContent.includes('已导出'));
    assert.ok(await connected.evaluate(()=>fixture.commands.some(c=>c.startsWith('umask 022;')&&c.includes('am get-current-user')&&c.includes('/storage/emulated/$user_id/Download')&&c.includes("cat '/data/local/tmp/luma_curve.log'"))));
    await connected.evaluate(()=>fixture.failExport=true);
    await connected.click('#export-log');
    await connected.waitForFunction(()=>document.getElementById('export-status').textContent.includes('导出失败'));
    assert.equal(await connected.locator('#export-log').isDisabled(),false);
    pass('Log copy, full-current-log export to active-user Downloads and export failure reporting');
    await connected.selectOption('#log-days','14');
    await connected.click('#save-log-policy');
    await connected.waitForFunction(()=>document.getElementById('tools-feedback').textContent==='日志保留策略已保存');
    assert.match(await connected.evaluate(()=>fixture.config), /log_retention_days=14/);
    pass('Log policy is persisted through validated control arguments');
    await connected.click('#nav-about');
    assert.equal(await connected.locator('#about-core-status').count(),0);
    assert.doesNotMatch(await connected.locator('.about-intro').innerText(),/核心|永久免费/);
    pass('About intro describes the module without process markers or donation claims');
    await connected.click('#github-link');
    await connected.waitForFunction(()=>fixture.commands.some(c=>c.startsWith('am start ')&&c.includes("-d 'https://github.com/RongShangs/LumaCurve'")));
    assert.ok(await connected.evaluate(()=>fixture.commands.some(c=>c.startsWith('am start ')&&c.includes("-n 'com.android.chrome/com.google.android.apps.chrome.Main'")&&c.includes('BROWSABLE')&&!c.includes('--selector'))));
    assert.equal(connected.url().split('#')[0],url);
    pass('External URLs resolve a generic web handler and launch an explicit browser, without MAIN selectors');
    for(const href of ['https://lc.rongshangs.top','https://rongshangs.top','https://www.coolapk.com/u/3261403','https://www.gnu.org/licenses/gpl-3.0.html']){
      await connected.click('a[href="'+href+'"]');
      await connected.waitForFunction(href=>fixture.commands.some(c=>c.startsWith('am start ')&&c.includes("-d '"+new URL(href).href+"'")),href);
    }
    await connected.evaluate(()=>{fixture.browserResolve='priority=0\nandroid/com.android.internal.app.ResolverActivity';fixture.browserQuery='2 activities found:\n  priority=0\n  org.mozilla.firefox/.App\n  com.android.chrome/com.google.android.apps.chrome.Main';});
    await connected.click('#github-link');
    await connected.waitForFunction(()=>fixture.commands.some(c=>c.startsWith('am start ')&&c.includes("-n 'org.mozilla.firefox/.App'")));
    await connected.evaluate(()=>{fixture.browserResolve='No activity found';fixture.browserQuery='No activities found';});
    const startsBefore=await connected.evaluate(()=>fixture.commands.filter(c=>c.startsWith('am start ')).length);
    await connected.click('#github-link');
    await connected.waitForFunction(()=>document.getElementById('toast').textContent.includes('未找到可以打开网页的浏览器'));
    assert.equal(await connected.evaluate(()=>fixture.commands.filter(c=>c.startsWith('am start ')).length),startsBefore);
    await connected.evaluate(()=>{fixture.browserResolve='com.android.chrome/com.google.android.apps.chrome.Main';fixture.failBrowser=true;});
    await connected.click('#github-link');
    await connected.waitForFunction(()=>document.getElementById('toast').textContent.includes('unable to resolve'));
    assert.equal(connected.url().split('#')[0],url);
    await connected.evaluate(()=>fixture.failBrowser=false);
    pass('Site/blog/license links, resolver fallback, missing browser and launch failures preserve the WebUI');
    await connected.evaluate(({template})=>{fixture.config=template;fixture.failAtomic=false;fixture.failControl=false;fixture.failState=false;fixture.overrides={};const base='0.1,0.2,0.75,1.15,5.6,6.6,8.9,9.9,10.9,12.2,22,25,65,85';fixture.preference='format=2\noffset=0\nsamples=2\nconfig_offset=0\nconfig_revision=0\nbase_points='+base+'\nlearned_points='+base.replace(',6.6,',',6.63,')+'\n';},{template});
    await connected.click('#nav-settings');await connected.click('#reload-config');
    await connected.waitForFunction(()=>document.getElementById('preference-status').textContent.includes('已学习 2 次'));
    assert.equal(await connected.locator('#cfg-preference_offset').count(),0);
    assert.equal(await connected.locator('#cfg-preference_learning').evaluate(el=>el.checked),true);
    assert.equal(await connected.locator('#panel-settings>.engine-controls').evaluate(el=>!!(el.compareDocumentPosition(document.getElementById('settings-form'))&Node.DOCUMENT_POSITION_FOLLOWING)),true);
    assert.equal(await connected.locator('#panel-settings>.page-title, #panel-tools>.page-title, #curve-dialog').count(),0);
    assert.equal(await connected.locator('#curve-editor-plot').isVisible(),true);
    assert.equal(await connected.locator('#curve-details').evaluate(el=>el.open),false);
    assert.equal(await connected.locator('#save-config').evaluate(el=>!!(el.compareDocumentPosition(document.getElementById('curve-editor-plot'))&Node.DOCUMENT_POSITION_FOLLOWING)),true);
    assert.equal(await connected.locator('#config-fields').locator(':scope > :first-child h2').innerText(),'照度与背光曲线');
    await edit(connected,'brighten_speed','1.2');await connected.click('#save-config');
    await connected.waitForFunction(()=>document.getElementById('dirty-status').textContent==='已保存并发送重载');
    assert.match(await connected.evaluate(()=>fixture.config),/^preference_offset=0$/m);assert.match(await connected.evaluate(()=>fixture.config),/^preference_revision=0$/m);
    pass('Engine controls lead Settings; learning is inside the curve editor, defaults on and unrelated saves preserve curve learning');
    await connected.locator('#cfg-preference_learning').uncheck();await connected.click('#save-config');
    await connected.waitForFunction(()=>document.getElementById('dirty-status').textContent==='已保存并发送重载');
    assert.match(await connected.evaluate(()=>fixture.config),/^preference_learning=0$/m);
    assert.match(await connected.evaluate(()=>fixture.config),/^preference_revision=0$/m);
    pass('Curve learning can be disabled without resetting learned points');
    await connected.click('#curve-details summary');
    await connected.locator('#curve-anchor').selectOption('5');await connected.locator('#curve-value').fill('8');await connected.click('#curve-apply');
    assert.match(await connected.locator('#curve-status').innerText(),/锚点已调整/);
    await connected.click('#save-config');
    await connected.waitForFunction(()=>document.getElementById('dirty-status').textContent==='已保存并发送重载');
    assert.match(await connected.evaluate(()=>fixture.config),/^curve_custom=1$/m);assert.equal((await connected.evaluate(()=>fixture.config.match(/^curve_points=(.*)$/m)[1])).split(',')[5],'8');
    assert.equal(await connected.locator('#curve-range').getAttribute('max'),'100');
    await connected.locator('#curve-value').fill('50');await connected.click('#curve-apply');assert.match(await connected.locator('#curve-status').innerText(),/联动/);
    assert.equal(await connected.locator('#curve-value').inputValue(),'50');
    await connected.locator('#curve-value').fill('101');await connected.click('#curve-apply');assert.match(await connected.locator('#curve-status').innerText(),/0.1%–100%/);
    await connected.locator('#curve-value').fill('8');await connected.click('#curve-apply');
    await connected.locator('#preset-name').fill('我的日常');await connected.click('#preset-save');
    await connected.waitForFunction(()=>fixture.presetWrites===1);
    const savedConfig=await connected.evaluate(()=>fixture.config);assert.equal(JSON.parse(await connected.evaluate(()=>fixture.presets)).entries[0].points[5],8);
    await connected.click('#curve-default');assert.equal(await connected.locator('#curve-value').inputValue(),'6.6');
    connected.once('dialog',d=>d.accept());await connected.click('#preset-load');assert.equal(await connected.locator('#curve-value').inputValue(),'8');
    assert.equal(await connected.evaluate(()=>fixture.config),savedConfig);
    connected.once('dialog',d=>d.dismiss());await connected.click('#preset-delete');assert.equal(await connected.evaluate(()=>fixture.presetWrites),1);
    connected.once('dialog',d=>d.accept());await connected.click('#preset-delete');await connected.waitForFunction(()=>fixture.presetWrites===2);assert.equal(JSON.parse(await connected.evaluate(()=>fixture.presets)).entries.length,0);
    presetShellContract(await connected.evaluate(()=>fixture.commands));
    pass('Curve anchors edit/save with monotonic validation; named persistent presets save/load/delete without applying the engine configuration');
    await connected.evaluate(()=>{fixture.failState=false;fixture.overrides={front_lux:'0.9',lux:'4.2',smooth:'5.4',target_br:37,current_br:254,max_br:4095};});
    await connected.click('#nav-status');await connected.waitForFunction(()=>document.getElementById('scene-source').textContent==='保持 / 限幅输入');
    assert.equal(await connected.locator('#scene-front').innerText(),'0.9 lux');assert.equal(await connected.locator('#scene-lux').innerText(),'4.2');
    assert.equal(await connected.locator('#target-marker').evaluate(el=>parseFloat(el.style.left)).then(v=>Math.abs(v-37/4095*100)<.001),true);
    assert.equal(await connected.locator('#response-trail').getAttribute('points'),'');assert.ok((await connected.locator('#response-base').getAttribute('points')).split(' ').length>150);
    pass('Original sensor and conditioned control inputs stay distinct; complete configured curve and exact linear sub-1% target marker render together');
    for (const [override,label] of [
      [{thermal_enabled:'0',thermal_trusted_temp:'0',thermal_suspect_temp:'0'},'已关闭'],
      [{thermal_enabled:'1',thermal_scan_done:'0',thermal_trusted_temp:'0',thermal_suspect_temp:'0'},'等待扫描'],
      [{thermal_scan_done:'1',thermal_zone_count:'0'},'未找到温度源'],
      [{thermal_zone_count:'2'},'节点不可读'],
      [{thermal_suspect_temp:'45000'},'辅助 45.0℃'],
      [{thermal_trusted_temp:'49000'},'49.0℃']]) {
      await connected.evaluate(o=>Object.assign(fixture.overrides,o),override);
      await connected.waitForFunction(label=>document.getElementById('scene-thermal').textContent===label,label);
    }
    assert.equal(await connected.locator('#confirm-gate>span').innerText(),'变化确认');
    assert.match(await connected.locator('#confirm-gate').getAttribute('title'),/不等于已到达目标/);
    pass('Thermal UI distinguishes disabled, scanning, absent, unreadable, suspect and trusted sources; debounce confirmation explains its role');
    await connected.evaluate(()=>fixture.log=Array.from({length:250},(_,i)=>'[2026-09-29 16:00:00] 自动调节 '+i).join('\n'));
    await connected.click('#nav-tools');await connected.waitForFunction(()=>document.getElementById('log').textContent.includes('自动调节 249'));
    assert.ok(await connected.locator('#log').evaluate(el=>el.scrollHeight-el.scrollTop-el.clientHeight<4));
    await connected.locator('#log').evaluate(el=>el.scrollTop=0);await connected.click('#refresh-log');await connected.waitForTimeout(100);
    assert.equal(await connected.locator('#log').evaluate(el=>el.scrollTop),0);
    await connected.locator('#log').evaluate(el=>el.scrollTop=el.scrollHeight);await connected.evaluate(()=>fixture.log+='\n[2026-09-29 16:00:01] 自动调节 250');await connected.click('#refresh-log');await connected.waitForTimeout(100);
    assert.ok(await connected.locator('#log').evaluate(el=>el.scrollHeight-el.scrollTop-el.clientHeight<4));
    pass('Timestamped logs initially scroll to latest, preserve manual scroll-up and follow bottom while watching new lines');
    await connected.click('#nav-settings');
    await connected.evaluate(()=>document.getElementById('toast').classList.remove('show'));
    await connected.screenshot({path:path.join(root,'build/webui-settings-mobile.png'),fullPage:true});
    await preview.setViewportSize({width:1280,height:900}); await preview.click('#nav-status');
    await preview.screenshot({path:path.join(root,'build/webui-status-desktop.png'),fullPage:true});
    await preview.setViewportSize({width:390,height:1100});
    await preview.evaluate(()=>scrollTo(0,0));
    await preview.screenshot({path:path.join(root,'build/webui-status-scene-mobile.png')});
    assert.deepEqual(errors,[]);pass('No JavaScript runtime errors in preview or device bridge flows');
    const source_sha256={};for(const file of ['module/webroot/app.js','module/webroot/index.html','module/webroot/styles.css','module/webroot/curve_math.js','module/webroot/kernelsu.js'])source_sha256[file]=require('node:crypto').createHash('sha256').update(fs.readFileSync(path.join(root,file))).digest('hex');
    fs.writeFileSync(path.join(root,'build/webui-verification.json'),JSON.stringify({ok:true,browser:'Chromium/Edge headless',device_bridge:'mock callback contract; no physical device',checks,source_sha256},null,2));
    console.log(JSON.stringify({ok:true,checks:checks.length},null,2));
  } finally {await browser.close();}
}
main().catch(e=>{fs.mkdirSync(path.join(root,'build'),{recursive:true});fs.writeFileSync(path.join(root,'build/webui-verification.json'),JSON.stringify({ok:false,checks,error:String(e.stack)},null,2));console.error(e);process.exitCode=1;});
