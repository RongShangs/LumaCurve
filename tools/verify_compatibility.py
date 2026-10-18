"""Verify the maintained compatibility contract; never probes a phone or publishes files.

Java catalogue = feature/route identities and display conditions.
JSON manifest = ownership, selection, evidence, teardown and validation responsibilities.
The Markdown index is generated, not a third manually maintained route list.
"""
from pathlib import Path
import argparse, base64, collections, json, re, subprocess

ROOT=Path(__file__).resolve().parents[1]
ARCH=ROOT/'docs/architecture'
SOURCE=ROOT/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
REQUIRED_INVARIANTS={'preserve_existing_routes','preserve_saved_preferences','actual_capabilities_not_os_or_model',
    'reject_partial_modern_abi','no_runtime_failure_downgrade','main_display_and_user_ownership',
    'bounded_event_driven_work','transactional_required_hook_installation','independent_optional_features',
    'evidence_absence_is_unknown','website_sync_on_explicit_publication'}
LAYERS={'bootstrap','orchestration','adapters','presentation','configuration','bridge','diagnostics','policy','network','hook_backend'}

def load_catalogue():
    out=ROOT/'build/compatibility-contract';out.mkdir(parents=True,exist_ok=True)
    driver=out/'CatalogDump.java'
    driver.write_text('''package top.rongshangs.lumacurve.refactor;
import java.util.*;import java.nio.charset.StandardCharsets;
public class CatalogDump {
 static String b64(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
 public static void main(String[] args){for(String id:CompatibilityCatalog.features())
  for(CompatibilityCatalog.Route r:CompatibilityCatalog.catalogue(id))
   System.out.println(id+"\\t"+CompatibilityCatalog.group(id)+"\\t"+r.id+"\\t"+b64(r.name)+"\\t"+b64(r.condition));}
}''',encoding='utf-8')
    classes=out/'classes';classes.mkdir(exist_ok=True)
    subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',str(classes),str(SOURCE/'CompatibilityCatalog.java'),str(driver)],check=True,capture_output=True)
    text=subprocess.check_output(['java','-cp',str(classes),'top.rongshangs.lumacurve.refactor.CatalogDump'],text=True,encoding='utf-8')
    result={}
    for line in text.splitlines():
        feature,group,route,name,condition=line.split('\t')
        rows=result.setdefault(feature,{'group':int(group),'routes':[]})['routes']
        if route in {r['id'] for r in rows}:raise ValueError('Duplicate Java route: '+feature+'/'+route)
        rows.append({'id':route,'name':base64.b64decode(name).decode('utf-8'),'condition':base64.b64decode(condition).decode('utf-8')})
    return result

def validate(manifest,catalogue,source_map,baseline,root=ROOT):
    errors=[]
    def check(ok,message):
        if not ok:errors.append(message)
    check(manifest.get('schema')==1,'manifest schema')
    check(REQUIRED_INVARIANTS.issubset(set(manifest.get('invariants',[]))),'missing highest-priority invariant')
    check(manifest.get('selection_authority')=='production adapters only; catalogue is explanatory','catalogue must not own runtime selection')
    owners=set();features=[];family_ids=[]
    for family in manifest.get('families',[]):
        fid=family.get('id','');family_ids.append(fid)
        check(bool(re.fullmatch('[a-z][a-z0-9_]*',fid)),'invalid family id: '+fid)
        for key in ['name','selector','cleanup']:check(isinstance(family.get(key),str) and bool(family[key].strip()),fid+': missing '+key)
        for key in ['features','routes','owners','evidence','tests']:check(isinstance(family.get(key),list) and bool(family[key]),fid+': missing '+key)
        check(family.get('composition') in {'exclusive','per_feature','two_dimensions','additive'},fid+': invalid composition')
        for key in ['features','routes','owners','tests']:check(len(family.get(key,[]))==len(set(family.get(key,[]))),fid+': duplicate '+key)
        seen_routes=set()
        for feature in family.get('features',[]):
            features.append(feature)
            check(feature in catalogue,fid+': unknown feature '+feature)
            selected={r['id'] for r in catalogue.get(feature,{}).get('routes',[])}
            check('unreported' not in selected,fid+': missing Java catalogue entry '+feature)
            seen_routes.update(selected)
        check(seen_routes==set(family.get('routes',[])),fid+': route catalogue/manifest drift')
        for name in family.get('owners',[]):
            owners.add(name)
            check((SOURCE/name).is_file(),fid+': missing owner '+name)
        for name in family.get('tests',[]):
            check(name.startswith('tests/') and name.endswith('.py') and (root/name).is_file(),fid+': missing validation '+name)
        if family.get('composition')=='two_dimensions':
            check(family.get('dimensions')==[['enabled_field','native_support_method'],['timed_budget','ambient_only']],fid+': HBM controller and timing must be separate')
    check(len(family_ids)==len(set(family_ids)),'duplicate family id')
    check(len(features)==len(set(features)),'feature has multiple owners')
    check(set(features)==set(catalogue),'Java features and maintenance manifest differ')
    for feature,routes in baseline.get('features',{}).items():
        check(set(routes).issubset({r['id'] for r in catalogue.get(feature,{}).get('routes',[])}),'removed baseline route: '+feature)
    check(baseline.get('schema')==1 and bool(baseline.get('features')),'missing preserved-route baseline')
    paths=[]
    for entry in source_map.get('files',[]):
        path=entry.get('file','');paths.append(path)
        check(entry.get('layer') in LAYERS,'unknown layer: '+path)
        check(bool(entry.get('purpose','').strip()),'missing responsibility: '+path)
    actual={p.relative_to(SOURCE).as_posix() for p in SOURCE.rglob('*.java')}
    check(len(paths)==len(set(paths)),'source has duplicate owners')
    check(set(paths)==actual,'source ownership drift: '+str(sorted(set(paths)^actual)))
    check(source_map.get('source_root')==SOURCE.relative_to(ROOT).as_posix(),'source-root drift')
    check(owners.issubset(set(paths)),'compatibility owner missing source registration')
    native_paths=[entry.get('file','') for entry in source_map.get('native_files',[])]
    native_actual={p.name for p in (ROOT/'experimental/refactor_hook/native').glob('*') if p.is_file()}
    check(len(native_paths)==len(set(native_paths)) and set(native_paths)==native_actual,'native source ownership drift')
    native_ids=[]
    for variant in manifest.get('native_variants',[]):
        native_ids.append(variant.get('id'))
        check(variant.get('owner') in native_actual,'unregistered native variant owner')
        check(len(variant.get('alternatives',[]))>=2 and bool(variant.get('rule','').strip()),'native variant alternatives/rule missing')
        check(variant.get('test','').startswith('tests/') and (root/variant.get('test','missing')).is_file(),'native variant verification missing')
    check(bool(native_ids) and len(native_ids)==len(set(native_ids)),'native variant catalogue missing/duplicate')
    variant_ids=[]
    for variant in manifest.get('abi_variants',[]):
        variant_ids.append(variant.get('id'))
        check(variant.get('owner') in set(paths),'unregistered ABI variant owner')
        check(len(variant.get('alternatives',[]))>=2 and len(variant.get('alternatives',[]))==len(set(variant.get('alternatives',[]))),'ABI variant alternatives missing/duplicate')
        check(bool(variant.get('rule','').strip()),'ABI variant selection rule missing')
        check(variant.get('test','').startswith('tests/') and (root/variant.get('test','missing')).is_file(),'ABI variant verification missing')
    check(bool(variant_ids) and len(variant_ids)==len(set(variant_ids)),'ABI variant catalogue missing/duplicate')
    # Compare all currently emitted self-check IDs, including the two bounded arrays.
    reported=set()
    for name in ['RuntimeFeatureChecks.java','RootFeatureChecks.java','MainActivity.java']:
        reported.update(re.findall(r'\b(?:f|app)\.check\("([a-z_]+)"\s*,',(SOURCE/name).read_text(encoding='utf-8')))
    for name,array,prefix in [('AdvancedOptions.java','GROUPS',''),('SceneOptions.java','IDS','scene_')]:
        text=(SOURCE/name).read_text(encoding='utf-8');values=re.search(r'\b'+array+r'=\{([^}]+)\}',text).group(1)
        reported.update(prefix+v for v in re.findall(r'"([a-z_]+)"',values))
    check(reported==set(catalogue),'self-check/catalogue drift: '+str(sorted(reported^set(catalogue))))
    check('.install(' not in (SOURCE/'RuntimeFeatureChecks.java').read_text(encoding='utf-8'),'self-check must not install hooks')
    brightness=(SOURCE/'BrightnessControl.java').read_text(encoding='utf-8')
    status=brightness[brightness.index('    JSONObject status()'):brightness.index('    void stopRaw()')]
    check('.install(' not in status,'status reporting must not install hooks')
    # Device adaptation may not depend on presentation or product version policies.
    ui={Path(e['file']).stem for e in source_map.get('files',[]) if e.get('layer')=='presentation'}
    for entry in source_map.get('files',[]):
        if entry.get('layer') not in {'bootstrap','orchestration','adapters'}:continue
        text=(SOURCE/entry['file']).read_text(encoding='utf-8')
        code=re.sub(r'/\*.*?\*/|//[^\n]*|"(?:[^"\\]|\\.)*"',' ',text,flags=re.S)
        refs=set(re.findall(r'\b[A-Z][A-Za-z0-9_]*\b',code))
        check(not refs.intersection(ui),'runtime depends on presentation: '+entry['file']+' '+str(sorted(refs.intersection(ui))))
        check(not re.search(r'SystemVersion\s*\.\s*(read|inspect)|Build\s*\.\s*(MODEL|DEVICE|VERSION)',code),'version/model gate in adapter: '+entry['file'])
    return errors

def render(manifest,catalogue,source_map):
    lines=['# 兼容路线索引（自动生成）','','来源：`CompatibilityCatalog.java` ＋ `compatibility-manifest.json` ＋ `source-map.json`。修改来源后运行 `python tools/verify_compatibility.py --write-doc`；普通校验会拒绝过期索引。','','此索引说明已实现和验证责任，不代表所有功能已在所有设备启用或实测。','',f"共 {len(catalogue)} 项功能、{len(manifest['families'])} 组适配、{len(source_map['files'])} 份生产 Java。",'']
    for f in manifest['families']:
        lines += ['## '+f['name']+' · '+f['id'],'','功能：'+ '、'.join('`'+v+'`' for v in f['features'])+'。','','选择：'+f['selector'],'','组合关系：`'+f['composition']+'`；互斥、独立功能、双维度和补充组件分别处理。','','| 路线 | 条件 |','| --- | --- |']
        routes={r['id']:r for feature in f['features'] for r in catalogue[feature]['routes']}
        for rid in f['routes']:lines.append('| `'+rid+'` · '+routes[rid]['name']+' | '+routes[rid]['condition'].replace('\n','<br>')+' |')
        lines += ['','实现：'+ '、'.join('['+n+'](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/'+n+')' for n in f['owners'])+'。','','证据：'+ '、'.join('`'+v+'`' for v in f['evidence'])+'。','','退出 / 回滚：'+f['cleanup'],'','验证：'+ '、'.join('['+Path(n).name+'](../../'+n+')' for n in f['tests'])+'。','']
    lines += ['## ABI 内部变体','','以下是功能路线内部的字段、签名、协议或迁移选择；不应全部变成 APP 开关。相关测试表示验证入口，具体主机 / 固件 / 真机覆盖以测试输出与复审记录为准。','','| 变体与实现 | 已有分支 | 选择及保护 | 相关验证 |','| --- | --- | --- | --- |']
    for v in manifest['abi_variants']:
        lines.append('| `'+v['id']+'` · `'+v['owner']+'` | '+' / '.join('`'+a+'`' for a in v['alternatives'])+' | '+v['rule']+' | ['+Path(v['test']).name+'](../../'+v['test']+') |')
    lines += ['','## 原生协议与运行分支','','| 分支与实现 | 已有路径 | 选择及保护 | 相关验证 |','| --- | --- | --- | --- |']
    for v in manifest['native_variants']:
        lines.append('| `'+v['id']+'` · `'+v['owner']+'` | '+' / '.join('`'+a+'`' for a in v['alternatives'])+' | '+v['rule']+' | ['+Path(v['test']).name+'](../../'+v['test']+') |')
    lines += ['','## 源码职责索引','','每份生产 Java / C / 头文件必须有明确归属；新增文件漏登记会使检查失败。','']
    for layer in sorted(LAYERS):
        files=[e for e in source_map['files'] if e['layer']==layer]
        lines += ['### '+layer+' · '+files[0]['purpose'],'','、'.join('`'+e['file']+'`' for e in files)+'。','']
    lines += ['### native · 原生主屏工具','']
    for entry in source_map['native_files']:lines.append('- `'+entry['file']+'`：'+entry['purpose'])
    return '\n'.join(lines)+'\n'

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--write-doc',action='store_true');args=parser.parse_args()
    manifest=json.loads((ARCH/'compatibility-manifest.json').read_text(encoding='utf-8'))
    mapping=json.loads((ARCH/'source-map.json').read_text(encoding='utf-8'))
    baseline=json.loads((ARCH/'compatibility-baseline.json').read_text(encoding='utf-8'))
    catalogue=load_catalogue();errors=validate(manifest,catalogue,mapping,baseline)
    if errors:raise SystemExit('\n'.join(errors))
    text=render(manifest,catalogue,mapping);document=ARCH/'compatibility-index.md'
    if args.write_doc:document.write_text(text,encoding='utf-8')
    elif not document.is_file() or document.read_text(encoding='utf-8')!=text:raise SystemExit('Compatibility index is stale; run --write-doc')
    print(f'Compatibility maintenance: {len(catalogue)} features, {len(manifest["families"])} families, {len(mapping["files"])} Java files PASS; static contract only')

if __name__=='__main__':main()
