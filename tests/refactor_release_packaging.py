"""Exercise release guards and real website scripts in an isolated fixture."""
from pathlib import Path
from workspace_scratch import scratch_directory
import ast, hashlib, json, shutil, subprocess, sys, types, zipfile

ROOT=Path(__file__).resolve().parents[1]
cases=0
def check(value):
    global cases
    assert value
    cases+=1

tree=ast.parse((ROOT/'tools/build_refactor_hook_test.py').read_text(encoding='utf-8'))
guard=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='validate_release_signing')
with scratch_directory(prefix='hyperlux-release-') as temporary:
    base=Path(temporary)
    class_input=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='class_archive')
    archive_scope={'Path':Path,'zipfile':zipfile}
    exec(compile(ast.Module(body=[class_input],type_ignores=[]),'<production class input archive>','exec'),archive_scope)
    compiled=base/'classes';first=compiled/'top/example/One.class';second=compiled/'top/example/Two$Inner.class';first.parent.mkdir(parents=True)
    first.write_bytes(b'class one');second.write_bytes(b'class two')
    output=base/'d8-inputs.jar';archive_scope['class_archive'](output,[second,first],compiled)
    with zipfile.ZipFile(output) as z:
        check(z.namelist()==['top/example/One.class','top/example/Two$Inner.class'])
        check(z.read('top/example/One.class')==first.read_bytes() and z.read('top/example/Two$Inner.class')==second.read_bytes())
    unchanged=output.read_bytes()
    outside=base/'outside.class';outside.write_bytes(b'outside')
    for files in [[],[first,first],[outside],[base/'missing.class']]:
        try:archive_scope['class_archive'](output,files,compiled)
        except ValueError:check(output.read_bytes()==unchanged)
        else:raise AssertionError('invalid class input accepted')
    try:archive_scope['class_archive'](compiled/'wrong.jar',[first],compiled)
    except ValueError:check(not (compiled/'wrong.jar').exists())
    else:raise AssertionError('class archive escaped build directory')

    certificate=b'public certificate fixture, not a private signing key'
    calls=[]
    def export(args):
        calls.append(args)
        return certificate
    scope={'hashlib':hashlib,'subprocess':types.SimpleNamespace(check_output=export),
           'EXPECTED_RELEASE_CERT':hashlib.sha256(certificate).hexdigest()}
    exec(compile(ast.Module(body=[guard],type_ignores=[]),'<production signing guard>','exec'),scope)
    key=base/'fixture.p12'
    try:scope['validate_release_signing'](key)
    except FileNotFoundError:pass
    else:raise AssertionError('missing release key accepted')
    check(not key.exists() and not calls)
    key.write_bytes(b'fixture')
    scope['validate_release_signing'](key)
    check(calls[-1][0:2]==['keytool','-exportcert'])
    check(str(key) in calls[-1])
    scope['EXPECTED_RELEASE_CERT']='0'*64
    try:scope['validate_release_signing'](key)
    except ValueError:pass
    else:raise AssertionError('replacement release certificate accepted')
    check(key.read_bytes()==b'fixture')
    def unavailable(args):raise subprocess.CalledProcessError(1,args)
    scope['subprocess']=types.SimpleNamespace(check_output=unavailable)
    try:scope['validate_release_signing'](key)
    except subprocess.CalledProcessError:pass
    else:raise AssertionError('keytool failure ignored')
    check(key.read_bytes()==b'fixture')

    output_guard=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='validate_output_paths')
    target=base/'deliverables';target.mkdir();safe={'__builtins__':__builtins__}
    exec(compile(ast.Module(body=[output_guard],type_ignores=[]),'<production output guard>','exec'),safe)
    safe['validate_output_paths'](target,['new.apk','new.zip']);check(not any(target.iterdir()))
    for name in ['existing.apk','existing.zip','release.md']:
        item=target/name;item.write_bytes(b'old immutable artifact')
        try:safe['validate_output_paths'](target,[name])
        except FileExistsError:pass
        else:raise AssertionError('existing deliverable accepted')
        check(item.read_bytes()==b'old immutable artifact')
    # Read-only verification keeps its explicit bypass; packaging must fail before SDK work.
    check('if not args.verify_only:' in (ROOT/'tools/build_refactor_hook_test.py').read_text(encoding='utf-8'))

    root=base/'project';site=root/'website';out=base/'web'
    def write(path,data):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes(data if isinstance(data,bytes) else data.encode('utf-8'))
    for name in ['sync_website.py','package_website.py','build_refactor_hook_test.py']:
        write(root/'tools'/name,(ROOT/'tools'/name).read_bytes())
    manifest=root/'experimental/refactor_hook/AndroidManifest.xml'
    write(manifest,'<manifest xmlns:android="http://schemas.android.com/apk/res/android" android:versionName="2.4.0" android:versionCode="24001"/>')
    write(root/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppBuild.java',
          'BUILD="release-2.4.0-r1",VERSION="2.4.0",ARTIFACT_VERSION="2.4.0";TEST=false;')
    # The real build entry point must stop before SDK/dependencies/files are created.
    build=subprocess.run([sys.executable,str(root/'tools/build_refactor_hook_test.py')],capture_output=True)
    check(build.returncode!=0 and b'existing signing key' in build.stderr)
    check(not (root/'build').exists())
    downloads=['HyperLux-2.4.0.apk','LumaCurve-2.4.0-source.zip','LumaCurve-2.4.0.zip']
    index='<span id="latest-version">2.4.0</span> 2.4.0 / 24001\n'+'\n'.join('downloads/'+n for n in downloads)
    write(site/'index.html',index)
    write(site/'thanks.json',json.dumps({'schema':1,'entries':[{'name':'Contributor','message':'Thanks'}]}))
    for name in ['site.js','site.css']:write(site/'assets'/name,name)
    for name in ['avatar.jpg','donate-wechat.jpg','donate-alipay.jpg']:
        write(root/'experimental/refactor_hook/assets'/name,name)
    write(root/'LICENSE','license')
    write(root/'docs/releases/2.4.0.md','release notes')
    for name in downloads:write(root/'dist'/name,name.encode('utf-8'))
    write(site/'thanks.js','old thanks');write(out/'index.html','old deployed site')
    def snapshot(folder):return {p.relative_to(folder).as_posix():p.read_bytes() for p in folder.rglob('*') if p.is_file()}
    def run(name):return subprocess.run([sys.executable,str(root/'tools'/name)],capture_output=True)
    def rejected_sync():
        before=snapshot(site),snapshot(out)
        result=run('sync_website.py')
        check(result.returncode!=0)
        check((snapshot(site),snapshot(out))==before)
    write(site/'index.html',index.replace('24001','24000'));rejected_sync();write(site/'index.html',index)
    missing=root/'dist'/downloads[1];saved=missing.read_bytes();missing.unlink();rejected_sync();write(missing,saved)
    missing=root/'experimental/refactor_hook/assets/donate-alipay.jpg';saved=missing.read_bytes();missing.unlink();rejected_sync();write(missing,saved)
    result=run('sync_website.py');check(result.returncode==0)
    check(snapshot(site)==snapshot(out))
    result=run('package_website.py');check(result.returncode==0)
    archive=root/'dist/LumaCurve-website-2.4.0.zip'
    original_archive=archive.read_bytes()
    with zipfile.ZipFile(archive) as z:
        check(z.testzip() is None)
        check(z.read('downloads/'+downloads[0])==(root/'dist'/downloads[0]).read_bytes())
        check(z.read('update.json')==(out/'update.json').read_bytes())
    # Local website previews can be packaged without replacing the saved web directory.
    preview=base/'private-website-preview';shutil.copytree(out,preview)
    result=subprocess.run([sys.executable,str(root/'tools/package_website.py'),'--site',str(preview)],capture_output=True)
    check(result.returncode==0);check(snapshot(out)==snapshot(preview))
    check(json.loads((preview/'update.json').read_text())['requires']==['Root','LSPosed','HyperOS'])
    original_archive=archive.read_bytes()
    result=subprocess.run([sys.executable,str(root/'tools/package_website.py'),'--site',str(base/'missing-preview')],capture_output=True)
    check(result.returncode!=0)
    check(archive.read_bytes()==original_archive)
    good=snapshot(out)
    def changed_metadata(key,value):
        data=json.loads((out/'update.json').read_text());data[key]=value
        write(out/'update.json',json.dumps(data,ensure_ascii=False,indent=2)+'\n')
        write(out/'update.js','window.HyperLuxRelease='+json.dumps(data,ensure_ascii=False)+';\n')
    mutations=[lambda:changed_metadata('versionCode',24000),
               lambda:changed_metadata('version','2.3.1'),
               lambda:changed_metadata('sha256','0'*64),
               lambda:changed_metadata('apkUrl','https://example.invalid/wrong.apk'),
               lambda:write(out/'update.js','stale script'),
               lambda:write(out/'index.html',index.replace(downloads[1],'missing.zip')),
               lambda:write(out/'index.html',index.replace('24001','24000')),
               lambda:write(out/'downloads/SHA256SUMS.txt','stale checksums'),
               lambda:write(out/'downloads'/downloads[0],b'corrupted APK'),
               lambda:write(out/'downloads'/downloads[1],b'older source'),
               lambda:write(out/'downloads'/downloads[2],b'older recovery bundle')]
    for mutate in mutations:
        for name,data in good.items():write(out/name,data)
        mutate();result=run('package_website.py')
        check(result.returncode!=0)
        check(archive.read_bytes()==original_archive)
print(f'Release packaging guards: {cases} cases PASS; isolated fixtures, no upload')
