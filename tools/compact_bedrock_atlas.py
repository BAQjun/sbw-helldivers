"""Losslessly repack Bedrock face UV patches; preserve geometry and animation rigs.

Usage: python tools/compact_bedrock_atlas.py --assets src/main/resources/assets/sbw_helldiver --models MODEL ...
Requires Pillow. No resampling is performed. One-pixel gutters preserve edge sampling.
"""
from pathlib import Path
import argparse, json, math, hashlib
from PIL import Image

def compact(assets, name):
    gp=assets/f'models/bedrock/vehicle/{name}.geo.json'
    tp=assets/f'textures/bedrock/vehicle/{name}.png'
    d=json.loads(gp.read_bytes());g=d['minecraft:geometry'][0]
    im=Image.open(tp).convert('RGBA');width,height=im.size
    detached=[]
    if name=='hmg_hellpod_v1':
        feed=assets/'helldivers/hmg_feed.json'
        saved={m['bone']:m['poly_mesh'] for m in json.loads(feed.read_bytes())['meshes']} if feed.exists() else {}
        for b in g['bones']:
            if b['name'] in saved and not b.get('poly_mesh'):
                b['poly_mesh']=saved[b['name']];detached.append(b)
    ep=tp.with_stem(tp.stem+'_emissive');em=Image.open(ep).convert('RGBA') if ep.exists() else None
    patches=[];unique={};bounds={};bindings=[]
    def patch(coords):
        box=(math.floor(min(u for u,v in coords))-1,math.floor(min(v for u,v in coords))-1,
             math.ceil(max(u for u,v in coords))+1,math.ceil(max(v for u,v in coords))+1)
        if box in bounds:return bounds[box]
        crop=im.crop(box);ec=em.crop(box) if em else None
        # Constant colour faces need only one texel, regardless of their authored UV span.
        uniform=all(lo==hi for lo,hi in crop.getextrema()) and (ec is None or all(lo==hi for lo,hi in ec.getextrema()))
        if uniform:
            crop=Image.new('RGBA',(3,3),crop.getpixel((0,0)))
            if ec:ec=Image.new('RGBA',(3,3),ec.getpixel((0,0)))
        key=(crop.size,hashlib.sha256(crop.tobytes()+(ec.tobytes() if ec else b'')).digest())
        i=unique.get(key)
        if i is None:i=len(patches);unique[key]=i;patches.append((crop,ec))
        result=(i,box,uniform);bounds[box]=result;return result
    for b in g['bones']:
        pm=b.get('poly_mesh')
        if pm:
            old=pm['uvs'];norm=pm.get('normalized_uvs',False)
            for face in pm['polys']:
                xy=[(old[v[2]][0]*(width if norm else 1),height-old[v[2]][1]*(height if norm else 1))for v in face]
                bindings.append(('poly',pm,face,xy,patch(xy)))
        for c in b.get('cubes',[]):
            if not isinstance(c.get('uv'),dict):raise ValueError('Atlas packing requires explicit cube face UVs')
            for f in c['uv'].values():
                x,y=f['uv'];w,h=f['uv_size'];xy=[(x,y),(x+w,y+h)]
                bindings.append(('cube',f,None,xy,patch(xy)))
    def pack(w):
        positions={};x=y=rh=0
        for i in sorted(range(len(patches)),key=lambda i:patches[i][0].height,reverse=True):
            p=patches[i][0]
            if p.width>w:return None
            if x+p.width>w:x=0;y+=rh;rh=0
            positions[i]=(x,y);x+=p.width;rh=max(rh,p.height)
        return positions,y+rh
    choices=[]
    for w in (512,1024,2048,4096,8192):
        packed=pack(w)
        if packed and packed[1]<=8192:choices.append((w*packed[1],w,packed))
    _,nw,(positions,nh)=min(choices)
    if nw*nh>=width*height and max(width,height)<=8192:return {'model':name,'before':im.size,'after':im.size,'skipped':True}
    atlas=Image.new('RGBA',(nw,nh));ea=Image.new('RGBA',(nw,nh))if em else None
    for i,(p,e)in enumerate(patches):
        atlas.paste(p,positions[i])
        if ea:ea.paste(e,positions[i])
    uv_lists={}
    def mapped(xy,pa):
        i,box,uniform=pa;ox,oy=positions[i]
        return [(ox+1.5,oy+1.5) if uniform else (u-box[0]+ox,v-box[1]+oy)for u,v in xy]
    for kind,obj,face,xy,pa in bindings:
        uv=mapped(xy,pa)
        # Verify texel identity at vertices and the face centre before changing the UVs.
        for q,r in list(zip(xy,uv))+[(tuple(sum(v[k]for v in xy)/len(xy)for k in(0,1)),tuple(sum(v[k]for v in uv)/len(uv)for k in(0,1)))]:
            sample=lambda image,p:image.getpixel((max(0,min(image.width-1,math.floor(p[0]+1e-7))),max(0,min(image.height-1,math.floor(p[1]+1e-7)))))
            assert sample(im,q)==sample(atlas,r),(name,q,r)
        if kind=='poly':
            key=id(obj)
            if key not in uv_lists:uv_lists[key]=([],{});obj['normalized_uvs']=False
            dest,cache=uv_lists[key]
            for v,(u,y)in zip(face,uv):
                v[2]=cache.setdefault((u,nh-y),len(dest))
                if v[2]==len(dest):dest.append([u,nh-y])
        else:
            obj['uv']=list(uv[0]);obj['uv_size']=[uv[1][0]-uv[0][0],uv[1][1]-uv[0][1]]
    for b in g['bones']:
        pm=b.get('poly_mesh')
        if pm:pm['uvs']=uv_lists[id(pm)][0]
    g['description']['texture_width']=nw;g['description']['texture_height']=nh
    if detached:
        feed.write_text(json.dumps(dict(texture_width=nw,texture_height=nh,meshes=[dict(bone=b['name'],poly_mesh=b.pop('poly_mesh')) for b in detached]),separators=(',',':'))+'\n','utf8')
    gp.write_text(json.dumps(d,separators=(',',':')),'utf8')
    atlas.save(tp)
    if ea:ea.save(ep)
    return {'model':name,'before':[width,height],'after':[nw,nh],'patches':len(patches)}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--assets',type=Path,required=True);p.add_argument('--models',nargs='+',required=True);a=p.parse_args()
    for name in a.models:print(json.dumps(compact(a.assets,name)),flush=True)
