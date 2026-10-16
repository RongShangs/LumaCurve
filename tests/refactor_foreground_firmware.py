"""Check event identity against private user-supplied system framework bytecode."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1];cases=0
def check(value):
    global cases
    assert value
    cases+=1
for label in ['myron-custom25','myron33']:
    data=json.loads((ROOT/'build/foreground-research'/(label+'.json')).read_text(encoding='utf-8'))['classes']
    service=data['Lcom/android/server/wm/ActivityTaskManagerService;'];record=data['Lcom/android/server/wm/ActivityRecord;']
    check(service['fields']['mLastResumedActivity']=='Lcom/android/server/wm/ActivityRecord;')
    callback=service['methods']['setLastResumedActivityUncheckLocked(Lcom/android/server/wm/ActivityRecord; Ljava/lang/String;)V']
    check('static' not in callback['flags']);check(bool(callback['instructions']))
    check(record['fields']['packageName']=='Ljava/lang/String;');check(record['fields'].get('mUserId',record['fields'].get('userId'))=='I')
    display=record['methods']['getDisplayId()I'];check('static' not in display['flags'])
    body='\n'.join(display['instructions']);check('Settings;' not in body and 'ContentResolver;' not in body and '->getService(' not in body)
    check('getFocusedRootTaskInfo()Landroid/app/ActivityTaskManager$RootTaskInfo;' in service['methods'])
    multi=json.loads((ROOT/'build/foreground-multiwindow-research'/(label+'.json')).read_text(encoding='utf-8'))['classes']
    tasks=multi['Lcom/android/server/wm/ActivityTaskManagerService;']['methods']['getTasks(I Z Z I)Ljava/util/List;']
    check('static' not in tasks['flags']);check(bool(tasks['instructions']))
    changed=multi['Lcom/android/server/wm/ActivityRecord;']['methods']['setVisibleRequested(Z)Z']
    check('static' not in changed['flags']);check(bool(changed['instructions']))
info=json.loads((ROOT/'build/foreground-multiwindow-research/task-info.json').read_text(encoding='utf-8'))['classes']
fields=info['Landroid/app/TaskInfo;']['fields']
for name,kind in [('userId','I'),('displayId','I'),('topActivity','Landroid/content/ComponentName;'),('isVisibleRequested','Z')]:check(fields[name]==kind)
check(info['Landroid/app/ActivityManager$RunningTaskInfo;']['super']=='Landroid/app/TaskInfo;')
print(f'Foreground application firmware: {cases} checks PASS on 2 private user frameworks; Android execution not tested')
