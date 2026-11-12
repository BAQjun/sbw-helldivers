"""Repair the shared cradle's pixel V outlines, with exact bilateral symmetry."""
import base64, io

# Two body UV islands and their larger companion islands in the shared atlas.
V_REGIONS = ((1214,700,1290,766),(1519,700,1595,766),
             (412,4335,512,4422),(1075,4335,1175,4422))

def repair(image):
    from PIL import ImageDraw
    image=image.convert("RGBA");px=image.load()
    for left,top,right,bottom in V_REGIONS:
        seen=set();parts=[]
        def gold(x,y):
            r,g,b,a=px[x,y];return r>80 and g>50 and b<g*.7 and a>0
        for y in range(top,bottom):
            for x in range(left,right):
                if (x,y) in seen or not gold(x,y):continue
                todo=[(x,y)];seen.add((x,y));part=[]
                while todo:
                    q=todo.pop();part.append(q)
                    for nx,ny in ((q[0]-1,q[1]),(q[0]+1,q[1]),(q[0],q[1]-1),(q[0],q[1]+1)):
                        if left<=nx<right and top<=ny<bottom and (nx,ny) not in seen and gold(nx,ny):
                            todo.append((nx,ny));seen.add((nx,ny))
                parts.append(part)
        # The outer V is the widest connected gold component; leave skull and inner badge untouched.
        part=max(parts,key=lambda ps:max(x for x,y in ps)-min(x for x,y in ps))
        x0=min(x for x,y in part);x1=max(x for x,y in part);y0=min(y for x,y in part);y1=max(y for x,y in part)
        bg=px[left,top];colour=px[part[len(part)//2]]
        for q in part:px[q]=bg
        # Construct one leg only; mirror its raster exactly, including the shared two-pixel tip.
        width=3 if x1-x0<80 else 4
        mask=image.copy();draw=ImageDraw.Draw(mask)
        middle=(x0+x1)//2
        draw.polygon([(x0,y0),(x0+width-1,y0),(middle,y1-width),(middle,y1)],fill=colour)
        for y in range(y0,y1+1):
            for x in range(x0,middle+1):
                if mask.getpixel((x,y))==colour and image.getpixel((x,y))!=colour:
                    px[x,y]=colour;px[x0+x1-x,y]=colour
        # Both mirrored legs are guaranteed equal at every scanline.
        for y in range(y0,y1+1):
            for x in range(x0,middle+1):
                if gold(x,y):px[x0+x1-x,y]=px[x,y]
    return image

def repair_embedded(model):
    from PIL import Image
    t=model['textures'][0];im=Image.open(io.BytesIO(base64.b64decode(t['source'].split(',',1)[1])))
    im=repair(im);out=io.BytesIO();im.save(out,format='PNG')
    t['source']='data:image/png;base64,'+base64.b64encode(out.getvalue()).decode()
