"""Share the standalone mine meshes instead of embedding every dispenser payload."""
from pathlib import Path
import argparse,json,math

parser=argparse.ArgumentParser()
parser.add_argument('--delivery',type=Path,required=True)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
assets=root/'src/main/resources/assets/sbw_helldiver'

def solve(rows,values):
    a=[list(r)+[v] for r,v in zip(rows,values)]
    for k in range(4):
        pivot=max(range(k,4),key=lambda i:abs(a[i][k]))
        a[k],a[pivot]=a[pivot],a[k]
        assert abs(a[k][k])>1e-9,'Degenerate mount fit'
        divisor=a[k][k];a[k]=[v/divisor for v in a[k]]
        for i in range(4):
            if i!=k:
                factor=a[i][k];a[i]=[u-factor*v for u,v in zip(a[i],a[k])]
    return [r[4] for r in a]

def sub(a,b):return [x-y for x,y in zip(a,b)]
def cross(a,b):return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
def dot(a,b):return sum(x*y for x,y in zip(a,b))
def affine(pairs):
    p0=pairs[0]
    p1=max(pairs,key=lambda p:dot(sub(p[0],p0[0]),sub(p[0],p0[0])))
    axis=sub(p1[0],p0[0])
    p2=max(pairs,key=lambda p:dot(cross(axis,sub(p[0],p0[0])),cross(axis,sub(p[0],p0[0]))))
    normal=cross(axis,sub(p2[0],p0[0]))
    p3=max(pairs,key=lambda p:abs(dot(normal,sub(p[0],p0[0]))))
    basis=[p0,p1,p2,p3]
    rows=[p+[1] for p,q in basis]
    m=[solve(rows,[q[k] for p,q in basis]) for k in range(3)]
    error=max(abs(sum(m[k][j]*p[j] for j in range(3))+m[k][3]-q[k]) for p,q in pairs for k in range(3))
    assert error<.00003,('Mount is not a rigid copy',error)
    # The exporter mirrors X in Bedrock JSON, and the Bedrock baker mirrors it
    # back. Cached meshes and animated panels therefore use Blockbench's X.
    s=[1/16,1/16,1/16]
    return [[round(m[k][j]*s[k]/s[j],9) for j in range(3)]+[round(m[k][3]*s[k],9)] for k in range(3)]

def launch_socket(bone,matrix,animation,index):
    # Match Bedrock's Z/Y/X Euler order and mirrored animation X/Y axes.
    x,y,z=animation['bones'][bone['name']]['rotation']
    x,y,z=map(math.radians,[-x,-y,z])
    sx,cx,sy,cy,sz,cz=math.sin(x),math.cos(x),math.sin(y),math.cos(y),math.sin(z),math.cos(z)
    rotation=[[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],
              [sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]]
    pivot=[-bone['pivot'][0]/16,bone['pivot'][1]/16,bone['pivot'][2]/16]
    offset=[pivot[k]+sum(rotation[k][j]*(matrix[j][3]-pivot[j]) for j in range(3)) for k in range(3)]
    direction=[sum(rotation[k][j]*matrix[j][1] for j in range(3)) for k in range(3)]
    length=math.sqrt(dot(direction,direction))
    sign=1 if direction[1]>=0 else -1
    direction=[v*sign/length for v in direction]
    # Native vehicle yaw at zero faces 180 degrees from model space.
    for k in [0,2]:offset[k]=-offset[k];direction[k]=-direction[k]
    return {'index':index,'panel':int(bone['name'].split('_')[1]),'offset':offset,'launch_direction':direction}

data={};launch_data={}
for solo,title,runtime in [('MD6反人员地雷','反人员地雷','ap_mine_hellpod_v1'),('燃烧地雷','燃烧地雷','incendiary_mine_hellpod_v1'),('毒气地雷','毒气地雷','gas_mine_hellpod_v1'),('反坦克地雷','反坦克地雷','anti_tank_mine_hellpod_v1')]:
    source=json.loads((args.delivery/'模型/地雷'/f'{solo}.bbmodel').read_text(encoding='utf8'))
    combo=json.loads((args.delivery/'模型/炮台与空投仓'/f'{title}掷弹器与空投仓.bbmodel').read_text(encoding='utf8'))
    elements={e['name']:e for e in source['elements'] if e.get('vertices')}
    mounted={}
    for e in combo['elements']:
        if e['name'].startswith('loaded_mine_'):
            slot=e['name'][:14];name=e['name'][15:]
            if name not in elements:continue
            original=elements[name]
            assert all(abs(v)<1e-8 for v in e.get('rotation',[0,0,0])+original.get('rotation',[0,0,0]))
            mounted.setdefault(slot,[]).extend((original['vertices'][key],value) for key,value in e['vertices'].items())
    file=assets/'models/bedrock/vehicle'/f'{runtime}.geo.json'
    geo=json.loads(file.read_text(encoding='utf8'))
    bones=geo['minecraft:geometry'][0]['bones']
    by_name={b['name']:b for b in bones}
    animation=json.loads((assets/'animations/bedrock/vehicle'/f'{runtime}.animation.json').read_text(encoding='utf8'))['animations']['animation.helldiver.deployed']
    expected=18 if 'tank' in runtime else 48
    assert len(mounted)==expected,(runtime,len(mounted))
    slots=[];removed=0
    for name,pairs in sorted(mounted.items()):
        bone=by_name[name];matrix=affine(pairs)
        pivot=bone['pivot']
        anchor=[-pivot[0]/16,pivot[1]/16,pivot[2]/16]
        assert max(abs(matrix[k][3]-anchor[k]) for k in range(3))<.00001,('Mount missed authored socket',runtime,name)
        slots.append({'parent':bone['parent'],'matrix':matrix})
        socket=launch_socket(by_name[bone['parent']],matrix,animation,len(slots)-1)
        socket_file='anti_tank_minefield_slots.json' if 'tank' in runtime else 'minefield_slots.json'
        launch_data.setdefault(runtime,[]).append(socket)
        mesh=bone.pop('poly_mesh',None)
        if mesh:removed+=len(mesh['polys'])
        assert not bone.get('cubes'),'Unexpected mounted cubes'
    data[runtime]=slots
    encoded=json.dumps(geo,separators=(',',':'))
    file.write_text(encoded,encoding='utf8')
    for target in (args.delivery/'assets').rglob(f'{runtime}.geo.json'):
        target.write_text(encoded,encoding='utf8')
    print(runtime,len(slots),'shared slots;',removed,'embedded triangles removed')
    if runtime in ['ap_mine_hellpod_v1','anti_tank_mine_hellpod_v1']:
        socket_text=json.dumps(launch_data[runtime],indent=2)+'\n'
        (assets/'helldivers'/socket_file).write_text(socket_text,encoding='utf8')
        for target in (args.delivery/'assets').rglob(socket_file):target.write_text(socket_text,encoding='utf8')
    else:
        for actual,expected in zip(launch_data[runtime],launch_data['ap_mine_hellpod_v1']):
            assert max(abs(a-b) for a,b in zip(actual['offset'],expected['offset']))<.00001,'Elemental sockets differ'
encoded=json.dumps(data,separators=(',',':'))
(assets/'helldivers/mine_mounts.json').write_text(encoded,encoding='utf8')
for model_dir in (args.delivery/'assets').rglob('models/bedrock/vehicle'):
    target=model_dir.parents[2]/'helldivers/mine_mounts.json'
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(encoded,encoding='utf8')
