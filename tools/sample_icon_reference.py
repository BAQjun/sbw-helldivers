import bpy, numpy as np
from collections import Counter
path='C:/Users/USER/AppData/Local/Temp/codex-clipboard-9b5b4819-c086-411f-a2d1-222ceb76eb95.png'
image=bpy.data.images.load(path);image.colorspace_settings.name='Non-Color'
w,h=image.size
px=np.rint(np.array(image.pixels[:]).reshape(h,w,4)[::-1]*255).astype(np.uint8)
print('REFERENCE_COLORS',Counter(map(tuple,px[17:73,14:72,:3].reshape(-1,3))).most_common(10))
for x,y in [(45,42),(14,18),(18,23),(25,34),(45,62)]: print(x,y,px[y,x].tolist())
