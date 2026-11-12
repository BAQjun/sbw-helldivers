"""Build an optional near-model pod: shallow badge relief and continuous nozzle rings.

Keep the authored rig, locators, texture atlas and every animation unchanged.
No baseline files are overwritten.
"""
import argparse, copy, json, math, pathlib, uuid

ap=argparse.ArgumentParser();ap.add_argument('--workspace',type=pathlib.Path,required=True);ap.add_argument('--output',type=pathlib.Path,required=True)
a=ap.parse_args();a.output.mkdir(parents=True,exist_ok=True)
src=a.workspace/'outputs/当前交付/模型/空投仓/空投仓.bbmodel'
d=json.loads(src.read_text(encoding='utf8'));old=copy.deepcopy(d)
elements={e['uuid']:e for e in d['elements']};groups={g['uuid']:g for g in d['groups']}
removed=set();added=[];count=0

def nozzle(rows,name):
    global count
    es=[elements[x] for x in rows if isinstance(x,str)]
    shell=[e for e in es if e['name'].startswith('pod_nozzle_shell_')]
    if not shell:return
    # Six identical 16-sided stepped rings replace 48 overlapping little boxes.
    first=shell[0];cx=(first['from'][0]+first['to'][0])/2-1.56;cz=(first['from'][2]+first['to'][2])/2
    top=first['to'][1];bottom=first['from'][1]
    profile=[(1.91,top,5),(1.91,bottom+.165,5),(2.0,bottom+.165,1089),(2.0,bottom-.44,1089),(1.50,bottom-.7095,5),(1.10,bottom-.7095,5),(1.10,top,5)]
    vertices={};faces={};n=16
    for j,(radius,y,_) in enumerate(profile):
        for i in range(n):
            theta=i*math.tau/n
            vertices[f'v{j}_{i}']=[cx+radius*math.cos(theta),y,cz+radius*math.sin(theta)]
    for j,(_,_,palette) in enumerate(profile):
        for i in range(n):
            keys=[f'v{j}_{i}',f'v{j}_{(i+1)%n}',f'v{(j+1)%len(profile)}_{(i+1)%n}',f'v{(j+1)%len(profile)}_{i}']
            faces[f'f{j}_{i}']={'vertices':keys,'uv':{k:[palette+1,6] for k in keys},'texture':0}
    ident=str(uuid.uuid5(uuid.NAMESPACE_URL, "sbw-helldiver/structural-nozzle/"+name));added.append({'name':name+'_continuous_ring','type':'mesh','uuid':ident,'origin':[0,0,0],'rotation':[0,0,0],'vertices':vertices,'faces':faces,'export':True,'visibility':True})
    ids={e['uuid'] for e in es if e['name'].startswith(('pod_nozzle_shell_','pod_nozzle_rim_','pod_nozzle_inner_lip_'))}
    removed.update(ids);rows[:]=[x for x in rows if not isinstance(x,str) or x not in ids]+[ident];count+=1

def walk(rows):
    for row in rows:
        if isinstance(row,str):continue
        name=groups[row['uuid']]['name']
        if name.startswith('pod_retro_nozzle_'):nozzle(row['children'],name)
        walk(row.get('children',[]))
walk(d['outliner'])
relief=0
for e in d['elements']:
    if e['name'].startswith('pod_badge_cap_'):
        for face,f in e['faces'].items():
            if face!='north':f['texture']=None
        relief+=1
d['elements']=[e for e in d['elements'] if e['uuid'] not in removed]+added
d['name']='空投仓_结构优化测试'
assert d['groups']==old['groups'] and d['animations']==old['animations']
(a.output/'pod.bbmodel').write_text(json.dumps(d,ensure_ascii=False,separators=(',',':')),encoding='utf8')
(a.output/'pod-structure-report.json').write_text(json.dumps({'continuous_nozzles':count,'shallow_badge_boxes_to_faces':relief,'rig_animations_unchanged':True},indent=2),encoding='utf8')
print('Continuous nozzles:',count,'shallow relief:',relief)
