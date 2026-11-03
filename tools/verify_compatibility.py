"""Verify the maintained compatibility contract; never probes a phone or publishes files.

Java catalogue = feature/route identities and display conditions.
JSON manifest = ownership, selection, evidence, teardown and validation responsibilities.
The Markdown index is generated, not a third manually maintained route list.
"""
from pathlib import Path
import argparse, base64, collections, hashlib, json, re, subprocess

ROOT=Path(__file__).resolve().parents[1]
ARCH=ROOT/'docs/architecture'
SOURCE=ROOT/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
REQUIRED_INVARIANTS={'preserve_existing_routes','preserve_saved_preferences','actual_capabilities_not_os_or_model',
    'reject_partial_modern_abi','no_runtime_failure_downgrade','main_display_and_user_ownership',
    'bounded_event_driven_work','transactional_required_hook_installation','independent_optional_features',
    'evidence_absence_is_unknown','website_sync_on_explicit_publication','normalize_before_shared_management'}
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
        convergence=family.get('convergence',{})
        for key in ['contract','coordinates']:
            check(isinstance(convergence.get(key),str) and bool(convergence[key].strip()),fid+': missing convergence '+key)
        for key in ['boundary','consumers']:
            values=convergence.get(key,[])
            check(isinstance(values,list) and bool(values) and len(values)==len(set(values)),fid+': missing/duplicate convergence '+key)
            check(all(v in {e['file'] for e in source_map.get('files',[])} for v in values),fid+': unknown convergence '+key)
        check(set(convergence.get('boundary',[])).issubset(family.get('owners',[])),fid+': convergence boundary is not owned')
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
    for message in boundary_violations({name:(SOURCE/name).read_text(encoding='utf-8') for name in BOUNDARY_CONSUMERS}):errors.append(message)
    missing=unexecuted_tests(manifest,(root/'tools/build_refactor_hook_test.py').read_text(encoding='utf-8'))
    check(not missing,'registered tests not executed by full build: '+str(sorted(missing)))
    errors.extend(feature_requirements_errors(manifest,catalogue,source_map,root=root))
    try:
        protected=baseline.get('interface_features',[])
        check(isinstance(protected,list) and bool(protected),'missing interface requirement baseline')
        check(set(protected).issubset({r['id'] for r in feature_requirements(root)['interface_features']}),'removed protected interface requirement')
    except (OSError,ValueError,KeyError,TypeError):check(False,'interface requirement baseline unreadable')
    errors.extend(stable_document_errors(root))
    return errors


def unexecuted_tests(manifest,build_text):
    # Packaged filenames do not prove execution. Accept only the two checked execution helpers.
    calls=set(re.findall(r"checked_test\(ROOT/'(tests/[a-z0-9_]+\.py)'",build_text))
    calls.update(re.findall(r"run\(\['python',ROOT/'(tests/[a-z0-9_]+\.py)'",build_text))
    registered={name for family in manifest.get('families',[]) for name in family.get('tests',[])}
    return registered-calls


BOUNDARY_CONSUMERS={'MainActivity.java','CurveComparison.java','SavedCurve.java','MemoryPersistence.java','MemoryLifecycle.java','HookRuntime.java','HookEntry.java','CurveBackend.java'}

def curve_implementations():
    # Derive names from production subclasses, so a future adapter is protected too.
    result=set()
    for path in SOURCE.glob('*.java'):
        result.update(re.findall(r'\bclass\s+(\w+)\s+extends\s+CurveBackend\b',path.read_text(encoding='utf-8')))
    return result

def boundary_violations(sources):
    errors=[]
    implementations=curve_implementations()
    types='(?:'+'|'.join(re.escape(v) for v in sorted(implementations))+')'
    manifest=json.loads((ARCH/'compatibility-manifest.json').read_text(encoding='utf-8'))
    routes=set(next(f['routes'] for f in manifest['families'] if f['id']=='curve'))
    for implementation in implementations:
        routes.update(re.findall(r'\bString\s+name\s*\(\s*\)\s*\{\s*return\s+"([^"]+)"',(SOURCE/(implementation+'.java')).read_text(encoding='utf-8')))
    route_literals=r'"(?:'+'|'.join(re.escape(v) for v in routes)+r')"'
    identity=r'\binstanceof\s+'+types+r'\b|\b'+types+r'\s*\.\s*class\b|\(\s*'+types+r'\s*\)'
    for name,text in sources.items():
        # Remove comments without changing literal strings or quoted comment characters.
        text=re.sub(r'"(?:[^"\\]|\\.)*"|/\*.*?\*/|//[^\n]*',lambda m:m.group(0) if m.group(0).startswith('"') else ' ',text,flags=re.S)
        if name in {'CurveComparison.java','SavedCurve.java','MemoryPersistence.java','MemoryLifecycle.java'} and re.search(route_literals+r'|"(?:mAnchorLux|mAnchorNit)"|'+identity+r'|kernel\.name\(\)\s*\.\s*equals',text):
            errors.append('native curve branch leaked into shared consumer: '+name)
        if name=='MainActivity.java' and re.search(r'(?:getString|optString)\s*\(\s*"curve_(?:backend|route)"|'+identity,text):errors.append('UI selects native curve route')
        # Config identity checks remain valid; selecting behavior by a concrete class is not.
        if name=='HookRuntime.java' and re.search(identity+r'|kernel\.name\(\)\s*\.\s*equals',text):errors.append('runtime selects curve capability by backend identity')
        if name=='HookEntry.java' and re.search(r'\bTraditionalHooks\s*\.\s*install\s*\(',text):errors.append('bootstrap bypasses backend mapping hook contract')
        if name=='CurveBackend.java' and re.search(r'"(?:mAnchorLux|mAnchorNit)"|name\(\)\.equals|'+identity,text):errors.append('shared backend defaults to a native ABI')
    return errors


def feature_requirements(root=ROOT):
    return json.loads((root/'docs/requirements/feature-requirements.json').read_text(encoding='utf-8'))


def stable_document_errors(root=ROOT):
    errors=[]
    for name in ['GLOBAL_RULES.md','PROJECT.md','PROJECT_SCOPE.md','AGENT_HANDOFF.md','docs/README.md','docs/architecture/README.md']:
        path=root/name
        if not path.is_file():errors.append('stable document missing: '+name);continue
        if re.search(r'^(?:20\d\d-\d\d-\d\d\s+)?当前(?:本地|正式|交付)|^(?:下方|以下)(?:为|保留)历史',path.read_text(encoding='utf-8'),re.M):errors.append('version history leaked into stable document: '+name)
    if not (root/'docs/work-status.md').is_file():errors.append('current work status missing')
    return errors


def requirement_source(ref,root=ROOT):
    def array(name,key):
        text=(root/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'/name).read_text(encoding='utf-8')
        match=re.search(r'\b'+re.escape(key)+r'=\{(.*?)\};',text,re.S)
        if not match:raise ValueError('requirement constant missing')
        return json.loads('['+match.group(1).strip().rstrip(',')+']')
    keys=array(ref['keys_file'],ref['keys_array']);values=array(ref['file'],ref['array'])
    if len(keys)!=len(values) or len(keys)!=len(set(keys)) or ref['item'] not in keys:raise ValueError('requirement constant shape')
    return values[keys.index(ref['item'])]


def feature_requirements_errors(manifest,catalogue,source_map,data=None,root=ROOT):
    errors=[]
    def check(ok,message):
        if not ok:errors.append('feature requirements: '+message)
    try:data=feature_requirements(root) if data is None else data
    except (OSError,ValueError):return ['feature requirements: missing or invalid structured source']
    check(data.get('schema')==1,'schema')
    rows=data.get('features',[]);interface=data.get('interface_features',[])
    check(isinstance(rows,list) and isinstance(interface,list),'feature lists')
    if not isinstance(rows,list) or not isinstance(interface,list):return errors
    check({r.get('id') for r in rows}==set(catalogue),'self-check/catalogue coverage drift')
    names={e['file'] for e in source_map['files']}
    families={f:group['id'] for group in manifest['families'] for f in group['features']}
    ids=[r.get('id') for r in rows+interface]
    check(len(ids)==len(set(ids)),'duplicate feature id')
    tokens=set()
    for name in names:
        tokens.update(re.findall(r'"([^"\n]*)"',(root/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'/name).read_text(encoding='utf-8')))
    for row in rows+interface:
        id=row.get('id','')
        check(bool(re.fullmatch('[a-z][a-z0-9_]*',id)),'invalid id')
        for key in ['name','activation','behavior','exit']:
            check(isinstance(row.get(key),str) and bool(row[key].strip()),id+': missing '+key)
        default=row.get('default',{})
        check(isinstance(default,dict) and default.get('mode') in {'native','off','observe','on_request','first_run'} and bool(default.get('detail')),id+': default')
        for key in ['settings_keys','dependencies']:
            values=row.get(key)
            check(isinstance(values,list) and len(values)==len(set(values or [])),id+': '+key)
        check(set(row.get('dependencies',[])).issubset(set(ids)) and id not in row.get('dependencies',[]),id+': unknown/self dependency')
        for key in row.get('settings_keys',[]):check(isinstance(key,str) and key.split('.')[-1] in tokens,id+': configuration key absent from production: '+str(key))
        for ref in row.get('source_refs',[]):
            try:
                check(ref['file'] in names and ref['keys_file'] in names and bool(ref['label']),id+': unknown constant owner')
                check(bool(requirement_source(ref,root)),id+': empty native explanation')
            except (OSError,ValueError,KeyError,TypeError):check(False,id+': invalid native explanation reference')
        if id in families:check(row.get('family')==families[id],id+': family ownership drift')
        else:check(bool(row.get('implementation')) and set(row.get('implementation',[])).issubset(names),id+': missing interface implementation')
    return errors


def feature_documents(manifest,catalogue,data,root=ROOT):
    """One editable requirement source + actual catalogue/owners, no copied route list."""
    result={};rows={r['id']:r for r in data['features']}
    index=['# 按功能维护的需求与兼容文档','','这些页面自动生成，不手改。需求来源于 `docs/requirements/feature-requirements.json`，路线来自生产目录及兼容清单；源码指纹变化会使普通检查拒绝过期页面。','','依赖指能力或数据前提，不代表必须开启同名开关。接口支持、路线采用、用户启用和当前生效分开；这些文档不证明所有设备均已实测。','','| 功能组 | 功能 |','| --- | --- |']
    def feature(row):
        lines=['### '+row['name']+' · `'+row['id']+'`','','| 项目 | 要求 |','| --- | --- |']
        for key,label in [('activation','触发'),('behavior','需求与行为'),('exit','退出 / 失败')]:lines.append('| '+label+' | '+row[key].replace('|','\\|').replace('\n','<br>')+' |')
        for ref in row.get('source_refs',[]):lines.append('| '+ref['label']+' | '+requirement_source(ref,root).replace('|','\\|').replace('\n','<br>')+' |')
        lines += ['| 默认 | `'+row['default']['mode']+'` · '+row['default']['detail']+' |','| 配置键 | '+('、'.join('`'+k+'`' for k in row['settings_keys']) or '无独立持久化亮度配置键')+' |','| 依赖能力 | '+('、'.join('`'+k+'`' for k in row['dependencies']) or '由本项实际接口独立判断')+' |']
        return lines+['']
    def implementation(names):
        lines=['## 实现追溯','','| 文件 | 当前源码 SHA-256 前16位 |','| --- | --- |']
        for name in sorted(set(names)):
            path=root/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'/name
            lines.append('| ['+name+'](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/'+name+') | `'+hashlib.sha256(path.read_bytes().replace(b'\r\n',b'\n')).hexdigest()[:16]+'` |')
        return lines+['']
    def narrative(text):
        parts=text.splitlines()
        return ([('- '+part) if len(parts)>1 else part for part in parts])+['']
    for family in manifest['families']:
        id=family['id'];index.append('| ['+family['name']+']('+id+'.md) | '+'、'.join(rows[f]['name'] for f in family['features'])+' |')
        lines=['# '+family['name']+'：需求与兼容契约','','> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。','','## 功能需求','']
        for f in family['features']:lines+=feature(rows[f])
        c=family['convergence'];lines += ['## 分叉与收束','','### 选择边界','']+narrative(family['selector'])
        lines += ['| 契约 | 内容 |','| --- | --- |','| 组合方式 | `'+family['composition']+'` |','| 统一输出 | '+c['contract']+' |','| 坐标与身份 | '+c['coordinates']+' |','','### 清理及回滚','']+narrative(family['cleanup'])
        lines += ['## 已实现的兼容路线','','| 路线 | 条件 |','| --- | --- |']
        routes={r['id']:r for f in family['features'] for r in catalogue[f]['routes']}
        for route in family['routes']:
            item=routes[route];lines.append('| `'+route+'` · '+item['name']+' | '+item['condition'].replace('\n','<br>')+' |')
        variants=[v for v in manifest['abi_variants'] if v['owner'] in family['owners']]
        if variants:
            lines+=['','路线内部 ABI：'+'、'.join('`'+v['id']+'`' for v in variants)+'；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。']
        lines+=['','运行证据：'+'、'.join('`'+v+'`' for v in family['evidence'])+'。','','验证入口：','']
        lines+=['- ['+Path(v).name+'](../../'+v+')' for v in family['tests']]
        lines+=['']+implementation(family['owners']+c['consumers'])
        result['docs/features/'+id+'.md']='\n'.join(lines).rstrip()+'\n'
    lines=['# 界面与使用工具：长期需求','','> 自动生成；这些交互项不伪装为系统兼容路线，原生能力仍由对应适配器决定。','']
    for row in data['interface_features']:lines+=feature(row)
    lines+=implementation([name for r in data['interface_features'] for name in r['implementation']])
    result['docs/features/interface.md']='\n'.join(lines).rstrip()+'\n'
    index+=['| [界面与使用工具](interface.md) | '+'、'.join(v['name'] for v in data['interface_features'])+' |','','版本变化单独见 [releases](../releases/)，当前任务单独见 [work-status](../work-status.md)。']
    result['docs/features/README.md']='\n'.join(index).rstrip()+'\n'
    return result


def render(manifest,catalogue,source_map):
    lines=['# 兼容路线索引（自动生成）','','本页只作路线导航、ABI变体和源码职责索引。每组完整需求、选择条件、统一契约、证据和验证只放在[按功能文档](../features/README.md)，不再重复整段维护。','','维护来源：生产CompatibilityCatalog、兼容清单、source-map及结构化功能需求；运行`python tools/verify_compatibility.py --write-doc`生成，普通检查拒绝漂移。','','共 '+str(len(catalogue))+' 项运行功能、'+str(len(manifest['families']))+' 组适配、'+str(len(source_map['files']))+' 份生产Java。目录不是设备白名单，也不代表全部设备已实测。','','| 功能组与契约 | 已实现路线 |','| --- | --- |']
    for f in manifest['families']:
        lines.append('| ['+f['name']+'](../features/'+f['id']+'.md) | '+'、'.join('`'+r+'`' for r in f['routes'])+' |')
    lines+=['','## ABI 内部变体','','路线内部结构和选择规则只在这里展示，各功能页引用稳定ID。不是额外的用户开关。','','| 变体 / 责任文件 | 分支 | 选择与保护 | 验证 |','| --- | --- | --- | --- |']
    for v in manifest['abi_variants']:
        lines.append('| `'+v['id']+'` · `'+v['owner']+'` | '+' / '.join('`'+x+'`' for x in v['alternatives'])+' | '+v['rule']+' | ['+Path(v['test']).name+'](../../'+v['test']+') |')
    lines+=['','## 原生协议与运行分支','','| 变体 / 实现 | 分支 | 选择与保护 | 验证 |','| --- | --- | --- | --- |']
    for v in manifest['native_variants']:
        lines.append('| `'+v['id']+'` · `'+v['owner']+'` | '+' / '.join('`'+x+'`' for x in v['alternatives'])+' | '+v['rule']+' | ['+Path(v['test']).name+'](../../'+v['test']+') |')
    lines+=['','## 源码职责索引','','职责详情维护在source-map.json；此处仅按层提供文件导航。','']
    for layer in sorted(LAYERS):
        files=[e for e in source_map['files'] if e['layer']==layer]
        lines+=['### '+layer,'','、'.join('['+e['file']+'](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/'+e['file']+')' for e in files)+'。','']
    lines+=['### native','','、'.join('`'+e['file']+'`' for e in source_map['native_files'])+'。','']
    return '\n'.join(lines).rstrip()+'\n'


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
    generated=feature_documents(manifest,catalogue,feature_requirements())
    extra={p.relative_to(ROOT).as_posix() for p in (ROOT/'docs/features').glob('*.md')}-set(generated)
    if extra:raise SystemExit('Unregistered generated feature pages: '+str(sorted(extra)))
    for name,content in generated.items():
        path=ROOT/name
        if args.write_doc:path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content,encoding='utf-8')
        elif not path.is_file() or path.read_text(encoding='utf-8')!=content:raise SystemExit('Feature document is stale: '+name+'; run --write-doc')
    print(f'Compatibility maintenance: {len(catalogue)} features, {len(manifest["families"])} families, {len(mapping["files"])} Java files PASS; static contract only')

if __name__=='__main__':main()
