from mathutils import Matrix,Quaternion,Vector
import math,json

def recover_cycle(A,bones,arm,rot,pivot,lift):
    names=['root','skeleton','boss','traverse','base_elevation_arm','base_elevation_offseter','elevator_slider','elevator_rotator','barrel_01','barrel_02','feed_shells','reload_cradle','reload_arm','reload_slider','reload_crucible','reload_shell','sensor','laser','muzzle']
    parents=[None,0,1,2,3,4,3,6,7,8,7,7,11,12,13,14,3,3,7]
    folder=WORK/'extracted/content/fac_helldivers/hellpod/mortar_turret/animation';idle=read(folder/'idle.animation.json');clip=read(folder/'reload.animation.json');initial=idle['header']['initialTransforms']
    def matrix(state):
        q=state['rotation'];return Matrix.Translation(Vector(state['position']))@Quaternion((q[3],q[0],q[1],q[2])).to_matrix().to_4x4()
    rest=[]
    for i,state in enumerate(initial):rest.append((rest[parents[i]] if parents[i] is not None else Matrix.Identity(4))@matrix(state))
    source_to_model=Matrix(((20,0,0,0),(0,0,20,lift),(0,-20,0,0),(0,0,0,1)));neutral=Matrix.Translation(pivot)@rot.to_4x4()@Matrix.Translation(-pivot);C=neutral@source_to_model;CI=C.inverted()
    allbones={b['name']:b for b in bones};mapped={'reload_cradle':'feed_cradle','reload_arm':'feed_arm','reload_slider':'feed_slider','reload_crucible':'feed_cup','reload_shell':'feed_round','feed_shells':'ammunition_column'}
    channels={}
    for i,name in enumerate(names):
        for kind in ['position','rotation']:
            keys=[(0.,clip['header']['initialTransforms'][i][kind])]
            for e in clip['entries']:
                if e['header']['bone']==i and kind in e:keys.append((float(e['header']['time']),e[kind]))
            channels[i,kind]=sorted(dict(keys).items())
    def sample(i,kind,t):
        keys=channels[i,kind];lo=keys[0];hi=keys[-1]
        for k in keys:
            if k[0]<=t:lo=k
            if k[0]>=t:hi=k;break
        factor=(t-lo[0])/(hi[0]-lo[0]) if hi[0]>lo[0] else 0
        if kind=='rotation':
            qa=Quaternion((lo[1][3],*lo[1][:3]));qb=Quaternion((hi[1][3],*hi[1][:3]));q=qa.slerp(qb,factor);return [q.x,q.y,q.z,q.w]
        return list(Vector(lo[1]).lerp(Vector(hi[1]),factor))
    fire=A['animation.Main.fire'];fire['animation_length']=1.0
    for name in mapped.values():fire['bones'][name]={'position':{},'rotation':{}}
    for frame in range(21):
        t=min(frame*.05,float(clip['header']['length']));posed=[];deltas={}
        for i,name in enumerate(names):
            m=matrix({k:sample(i,k,t) for k in ['position','rotation']});posed.append((posed[parents[i]] if parents[i] is not None else Matrix.Identity(4))@m);deltas[name]=C@posed[i]@rest[i].inverted()@CI
        deltas['barrel']=deltas['elevator_rotator']
        for name,bname in mapped.items():
            b=allbones[bname];parent=b['parent'];pd=deltas.get(next((n for n,bn in mapped.items() if bn==parent),'barrel'),Matrix.Identity(4));d=deltas[name]
            origin=Vector(b['pivot']);position=(pd.inverted()@d@origin)-origin;rotation=(pd.inverted().to_quaternion()@d.to_quaternion()).to_euler('XYZ');ch=fire['bones'][bname]
            stamp=str(round(frame*.05,3));ch['position'][stamp]=[round(x,5) for x in position];ch['rotation'][stamp]=[round(math.degrees(x),4) for x in rotation]
    # The source's feed carriage finishes one slot forward. Its hidden ammunition column
    # is reset before the next shot; no magazine replacement state is introduced.
    for name in mapped.values():
        fire['bones'][name]['position']['1.0']=[0,0,0];fire['bones'][name]['rotation']['1.0']=[0,0,0]
    save(WORK/'recovered_feed_animation.json',fire)
