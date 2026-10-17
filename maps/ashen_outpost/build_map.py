from pathlib import Path
import struct,gzip,json,zlib,time,math,shutil
import numpy as np
ROOT=Path(__file__).resolve().parent
WORLD=ROOT/'world'/'HD2_Ashen_Outpost'
WORLD.mkdir(parents=True,exist_ok=True)
# Typed NBT writer; Java Edition 1.20.1, no third-party blocks.
def T(t,v):return (t,v)
def S(v):return T(8,v)
def I(v):return T(3,v)
def B(v):return T(1,v)
def L(v):return T(4,v)
def C(v):return T(10,v)
def LS(t,v):return T(9,(t,v))
def string(s):
 b=s.encode('utf8');return struct.pack('>H',len(b))+b
def payload(t,v):
 if t in (1,2,3,4,5,6):return struct.pack('>'+{1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t],v)
 if t==7:return struct.pack('>i',len(v))+v
 if t==8:return string(v)
 if t==9:return bytes([v[0]])+struct.pack('>i',len(v[1]))+b''.join(payload(v[0],a) for a in v[1])
 if t==10:return b''.join(bytes([a[0]])+string(k)+payload(*a) for k,a in v.items())+b'\0'
 if t in (11,12):return struct.pack('>i',len(v))+b''.join(struct.pack('>I' if t==11 else '>Q',int(a)&((1<<(32 if t==11 else 64))-1)) for a in v)
 raise ValueError(t)
def nbt(v):return b'\x0a\0\0'+payload(10,v)
N=768;O=N//2
blocks=np.zeros((192,N,N),dtype=np.uint8)
palette=[];ids={};bes={}
def mat(s):
 if s not in ids:
  name,_,prop=s.partition('[');d={'Name':S('minecraft:'+name)}
  if prop:d['Properties']=C({k:S(v) for k,v in (p.split('=') for p in prop.rstrip(']').split(','))})
  ids[s]=len(palette);palette.append(d)
 return ids[s]
air=mat('air');stone=mat('stone');sand=mat('red_sand');clay=mat('terracotta');brown=mat('brown_terracotta');orange=mat('orange_terracotta');sandstone=mat('red_sandstone');pale=mat('smooth_sandstone');dark=mat('gray_concrete');black=mat('black_concrete');metal=mat('polished_deepslate');iron=mat('iron_block');yellow=mat('yellow_concrete');white=mat('light_gray_concrete');lamp=mat('sea_lantern');glass=mat('gray_stained_glass');grate=mat('iron_bars');steel=mat('smooth_stone');rubble=mat('cobbled_deepslate')
z,x=np.mgrid[-O:O,-O:O];r=np.hypot(x,z)
# A broad, gently undulating basin with asymmetric escarpments, not a flat test grid.
h=63+2.8*np.sin(x/55)*np.cos(z/71)+1.6*np.sin((x+z)/31)
h+=33*np.exp(-((x+218)/52)**2)*(0.7+0.3*np.sin(z/61)**2)
h+=44*np.exp(-((x-230)/63)**2)*(0.6+0.4*np.cos(z/81)**2)
h+=39*np.exp(-((z-258)/53)**2)*(0.65+0.35*np.cos(x/83)**2)
h+=21*np.exp(-((z+255)/66)**2)*(0.6+0.4*np.sin(x/65)**2)
h+=8*np.sin(x/18+np.sin(z/24))*np.clip((r-145)/120,0,1)**2
# Outer plateau gently meets the continuation generator at y=63.
blend=np.clip((370-np.maximum(abs(x),abs(z)))/45,0,1);h=63+(h-63)*blend
# Central working apron and clear firing corridor.
base=np.maximum(abs(x)/118,abs(z+43)/112);w=np.clip((1.18-base)/.22,0,1);h=h*(1-w)+65*w
lane=np.clip((43-abs(x))/14,0,1)*np.clip((220-z)/25,0,1)*np.clip((z-7)/20,0,1);h=h*(1-lane)+63*lane
height=np.floor(h).astype(int)
for yy in range(192):
 a=blocks[yy];a[yy<=height]=stone
 m=(yy<=height)&(yy>height-12);a[m]=clay if yy%9 not in (0,1) else brown
 a[(yy<=height)&(yy>height-3)]=sandstone
 a[yy==height]=sand
# Large impact bowls away from the pristine presentation apron.
for cx,cz,rad,depth in [(56,99,18,6),(-62,146,24,8),(15,184,14,5),(118,176,21,7),(-130,69,12,4)]:
 for zz in range(cz-rad-3,cz+rad+4):
  for xx in range(cx-rad-3,cx+rad+4):
   d=math.hypot(xx-cx,zz-cz)
   if d<rad:
    top=int(height[zz+O,xx+O]);ny=top-int(depth*(1-(d/rad)**2));blocks[ny+1:top+1,zz+O,xx+O]=air;blocks[ny,zz+O,xx+O]=brown;height[zz+O,xx+O]=ny

def put(x,y,z,b):
 if -O<=x<O and -O<=z<O and 0<=y<192:blocks[y,z+O,x+O]=b if isinstance(b,int) else mat(b)
def box(x1,y1,z1,x2,y2,z2,b):
 blocks[max(0,y1):min(192,y2+1),z1+O:z2+O+1,x1+O:x2+O+1]=b if isinstance(b,int) else mat(b)
def disk(cx,cy,cz,r,b,inner=0):
 for zz in range(cz-r,cz+r+1):
  for xx in range(cx-r,cx+r+1):
   if inner**2<=(xx-cx)**2+(zz-cz)**2<=r*r:put(xx,cy,zz,b)
def octagon(cx,y,cz,r,b,inner=0):
 for zz in range(-r,r+1):
  for xx in range(-r,r+1):
   d=max(abs(xx),abs(zz),(abs(xx)+abs(zz))*.7071)
   if inner<=d<=r:put(cx+xx,y,cz+zz,b)
def line(a,b,block):
 steps=max(abs(b[i]-a[i]) for i in range(3))
 for i in range(steps+1):
  t=i/max(1,steps);put(*(round(a[k]+(b[k]-a[k])*t) for k in range(3)),block)
def sign(x,y,z,lines,facing='north'):
 put(x,y,z,f'oak_wall_sign[facing={facing},waterlogged=false]')
 text=[json.dumps({'text':s,'color':'black'},ensure_ascii=False) for s in lines];text+=['{"text":""}']*(4-len(text))
 bes[(x,y,z)]={'id':S('minecraft:sign'),'x':I(x),'y':I(y),'z':I(z),'is_waxed':B(1),'front_text':C({'messages':LS(8,text),'color':S('black'),'has_glowing_text':B(0)}),'back_text':C({'messages':LS(8,['{"text":""}']*4),'color':S('black'),'has_glowing_text':B(0)})}
# Graded outpost foundation and service roads.
box(-108,64,-140,108,65,8,clay)
for zz in range(-145,217):
 for xx in range(-6,7):
  yy=65 if zz<9 else int(height[zz+O,xx+O]);put(xx,yy,zz,metal if abs(xx)==6 else dark)
  if abs(xx)==4 and zz%12<6:put(xx,yy,zz,yellow)
# Main octagonal showcase stage, with two smaller side platforms.
for cx,cz,rr,yy in [(0,-25,18,69),(-40,-20,11,67),(40,-20,11,67),(0,-108,25,67)]:
 for y in range(64,yy+1):octagon(cx,y,cz,rr,metal if y<yy else dark)
 octagon(cx,yy,cz,rr,yellow,rr-1)
 octagon(cx,yy,cz,rr-3,steel,rr-3.9)
 for dx,dz in [(-1,-1),(-1,1),(1,-1),(1,1)]:
  put(cx+dx*(rr-4),yy,cz+dz*(rr-4),lamp)
 for zz in range(cz-rr+4,cz+rr-3):
  if (zz-cz)%5==0:
   put(cx-rr+3,yy,zz,black);put(cx+rr-3,yy,zz,black)
# Large clean H for Eagle landing apron.
box(-7,67,-117,-5,67,-99,white);box(5,67,-117,7,67,-99,white);box(-5,67,-109,5,67,-107,white)
# Wide functional steps up to main platform, rear and sides.
for i in range(4):
 box(-5,65+i,-47+i,5,65+i,-47+i,steel)
 box(-23+i,65+i,-29,-23+i,65+i,-21,steel)
 box(23-i,65+i,-29,23-i,65+i,-21,steel)
# Perimeter retaining wall, intentionally open to the firing range.
for xx in range(-110,111):
 if abs(xx)>10:
  box(xx,64,-143,xx,68,-141,metal);put(xx,69,-142,steel)
for xx in (-110,110):
 box(xx,64,-142,xx,68,-4,metal)
 for zz in range(-135,-3,12):box(xx-1,68,zz,xx+1,71,zz+1,dark);put(xx,71,zz,lamp)
# Floodlights: thin posts and suspended three-lamp bars.
def flood(cx,cz,h=14):
 box(cx-1,65,cz-1,cx+1,66,cz+1,metal);box(cx,67,cz,cx,65+h,cz,iron)
 box(cx-3,65+h,cz,cx+3,65+h,cz,dark)
 for dx in (-2,0,2):put(cx+dx,65+h-1,cz,lamp)
for cx,cz in [(-27,-47),(27,-47),(-58,-108),(58,-108),(-102,-12),(102,-12),(-25,36),(25,36)]:flood(cx,cz)
# Hangar with an actual interior and clear door, side windows and roof ribs.
box(-101,65,-93,-57,66,-46,metal);box(-100,67,-92,-58,83,-47,dark);box(-97,67,-89,-61,80,-49,air)
box(-91,67,-49,-67,78,-47,air)
for xx in (-95,-63):box(xx,67,-93,xx+1,85,-46,metal)
for zz in range(-89,-45,8):box(-100,84,zz,-58,85,zz+1,metal)
box(-98,81,-90,-60,82,-48,steel)
for xx in range(-95,-60,8):box(xx,80,-85,xx+2,80,-83,lamp)
for zz in range(-85,-54,9):box(-101,71,zz,-100,75,zz+4,glass)
box(-91,79,-47,-67,80,-46,yellow)
box(-91,66,-53,-67,66,-48,black)
for xx in range(-90,-66,4):box(xx,66,-53,xx+1,66,-48,yellow)
# Garage side benches and crates.
for xx in range(-94,-64,8):box(xx,67,-85,xx+3,69,-82,'barrel[facing=up,open=false]')
box(-96,67,-73,-94,69,-56,steel)
# Command bunker with viewing strip.
box(58,65,-96,102,67,-49,metal);box(60,68,-94,100,78,-51,dark);box(63,68,-91,97,75,-54,air)
box(59,79,-95,101,80,-50,metal);box(64,72,-52,94,75,-51,glass);box(78,68,-52,83,72,-51,air)
for xx in (63,96):box(xx,68,-95,xx+1,81,-50,steel)
box(64,77,-50,94,77,-50,yellow)
box(66,68,-87,92,69,-84,black)
for xx in range(67,93,6):box(xx,70,-87,xx+2,71,-86,'cyan_stained_glass');put(xx,70,-85,'stone_pressure_plate[powered=false]')
for xx in range(68,95,8):put(xx,75,-72,lamp)
# Black/yellow signal pylon and transmission array behind the landing site.
box(-15,65,-177,15,70,-157,metal);box(-10,71,-174,10,80,-160,dark)
box(-11,78,-175,11,79,-159,yellow)
box(-2,81,-169,2,113,-165,iron)
for yy in (86,96,106):box(-7,yy,-168,7,yy,-166,dark)
for xx in (-7,7):box(xx,106,-168,xx,117,-168,iron);put(xx,118,-168,'redstone_lamp[lit=true]')
# Twin industrial stacks frame the far range; silhouettes readable from the pad.
for cx,cz in [(-66,151),(69,159)]:
 box(cx-11,62,cz-11,cx+11,67,cz+11,metal)
 for yy in range(68,117):octagon(cx,yy,cz,7,dark)
 for yy in (70,82,99,112,116):
  octagon(cx,yy,cz,9,metal);octagon(cx,yy+1,cz,9,steel,7)
 for xx in (-5,5):box(cx+xx,69,cz-7,cx+xx,113,cz-7,black)
 box(cx-3,102,cz-8,cx+3,107,cz-8,yellow)
 for yy in range(68,117):put(cx+8,yy,cz,'ladder[facing=east,waterlogged=false]')
 for dx,dz in [(-6,-6),(-6,6),(6,-6),(6,6)]:put(cx+dx,118,cz+dz,'redstone_lamp[lit=true]')
 box(cx-3,117,cz-3,cx+3,118,cz+3,black)
# Pipe bridge between stacks, behind target line so it does not block aim.
box(-56,76,163,58,77,165,metal);box(-56,78,164,58,78,164,iron)
for xx in (-40,0,40):box(xx,63,164,xx+1,77,165,metal)
# Radar / relay station on eastern ridge.
cx,cz=119,61
box(cx-14,64,cz-14,cx+14,68,cz+14,metal);box(cx-9,69,cz-9,cx+9,76,cz+9,dark)
box(cx-10,77,cz-10,cx+10,78,cz+10,metal);box(cx-2,79,cz-2,cx+2,88,cz+2,iron)
# Parabolic dish faces the stage, with a crisp circular rim and feed arm.
for dx in range(-16,17):
 for dy in range(-16,17):
  rr=math.hypot(dx,dy)
  if rr<=16:
   zz=cz+round((dx*dx+dy*dy)/64)
   put(cx+dx,99+dy,zz,white if rr<14.8 else iron)
   if dx%8==0 or dy%8==0:put(cx+dx,99+dy,zz-1,steel)
line((cx-12,87,cz+4),(cx,99,cz-9),iron);line((cx+12,87,cz+4),(cx,99,cz-9),iron);box(cx-1,98,cz-10,cx+1,100,cz-8,yellow)
# Containers / fuel station opposite main hangar, with regular corrugation.
for cx,cz,col in [(77,-23,gray if False else dark),(94,-18,orange),(-82,-22,mat('green_terracotta')),(-83,-10,dark),(87,1,dark)]:
 box(cx-5,66,cz-4,cx+5,71,cz+4,col)
 for xx in range(cx-5,cx+6,2):box(xx,66,cz-4,xx,71,cz-4,metal);box(xx,66,cz+4,xx,71,cz+4,metal)
 box(cx-5,72,cz-4,cx+5,72,cz+4,steel);box(cx-5,67,cz-5,cx+5,67,cz-5,yellow)
# Fuel canisters and overhead pipe.
for cx,cz in [(88,27),(103,29),(99,44)]:
 for yy in range(65,78):disk(cx,yy,cz,5,steel if yy%6==0 else dark)
 disk(cx,78,cz,5,metal);disk(cx,73,cz,5,yellow,4)
box(88,78,27,103,79,28,iron)
# Low concrete barricades and sandbag line at the open apron edge.
for cx in [-98,-78,-58,58,78,98]:
 box(cx-6,65,9,cx+6,67,11,metal);box(cx-5,68,9,cx+5,68,10,steel)
 for dx in range(-5,6,4):put(cx+dx,67,8,yellow)
# Distant broken fortification, always outside the central 60 m firing corridor.
for cx,cz in [(-107,93),(-120,174),(106,115),(-92,206)]:
 yy=int(height[cz+O,cx+O]);box(cx-11,yy,cz-4,cx+11,yy+6,cz+4,dark);box(cx-9,yy+1,cz-2,cx+9,yy+4,cz+2,air)
 box(cx-4,yy+4,cz-5,cx+4,yy+5,cz-4,air);box(cx+5,yy+6,cz-1,cx+11,yy+8,cz+4,air)
 for dx in range(-12,13,5):put(cx+dx,yy,cz-7,rubble)
# Desert spires: hand-placed groups with stepped strata and tapering silhouettes.
for cx,cz,rad,hh in [(-170,9,13,32),(-178,38,9,23),(181,122,17,45),(204,95,9,28),(-148,224,19,39),(117,245,16,43),(-39,270,15,37),(22,-231,13,28),(164,-153,16,34),(-206,-123,18,44)]:
 basey=int(height[cz+O,cx+O])
 for dy in range(hh):
  rr=max(2,round(rad*(1-dy/(hh*1.12))));off=int(2*math.sin(dy/7))
  disk(cx+off,basey+dy,cz,rr,clay if (dy+basey)%9>2 else brown)
# Scattered stone groups, only outside stage and straight firing lane.
rng=np.random.default_rng(43)
for _ in range(290):
 xx=int(rng.integers(-310,311));zz=int(rng.integers(-300,311))
 if (-127<xx<133 and -195<zz<28) or (abs(xx)<43 and 0<zz<224) or (90<xx<145 and 35<zz<90):continue
 yy=int(height[zz+O,xx+O]);rr=int(rng.integers(1,4))
 for dy in range(rr):disk(xx,yy+dy,zz,max(1,rr-dy),sandstone)
# Range markers / aiming panels: simple silhouettes, not pre-spawned mobs.
digits={'0':['111','101','101','101','111'],'1':['010','110','010','010','111'],'3':['111','001','111','001','111'],'5':['111','100','111','001','111'],'6':['111','100','111','101','111']}
def ground_number(cx,cz,text):
 for j,ch in enumerate(text):
  for zz,row in enumerate(digits[ch]):
   for xx,v in enumerate(row):
    if v=='1':put(cx+j*4+xx,63,cz+zz,white)
for dist,zz in [(30,5),(60,35),(100,75),(150,125)]:
 for cx in (-31,31):
  yy=int(height[zz+O,cx+O])+1;box(cx-1,yy,zz,cx+1,yy+2,zz,metal);put(cx,yy+3,zz,yellow);sign(cx,yy+2,zz-1,[str(dist)+' M','测距标线'])
 if dist>=60:ground_number(-5,zz-3,str(dist))
# Main static targets at 60 / 100 / 150 m from the presentation pad.
for zz in (35,75,125):
 for cx in (-14,0,14):
  yy=int(height[zz+O,cx+O])+1
  box(cx-2,yy,zz,cx+2,yy,zz+1,metal);box(cx,yy+1,zz,cx,yy+3,zz,iron)
  box(cx-2,yy+3,zz,cx+2,yy+7,zz,black);box(cx-1,yy+4,zz-1,cx+1,yy+6,zz-1,white);put(cx,yy+5,zz-2,'red_concrete')
# Symmetric winged skull emblem built into the command bunker's rear wall.
skull=['001111100','011111110','111111111','110101011','110101011','011111110','001010100','001111100']
for yy,row in enumerate(skull):
 for xx,ch in enumerate(row):
  if ch=='1':put(76+xx,77-yy,-96,white)
for side in (-1,1):
 for i in range(5,17):
  for j in range(2 if i<13 else 1):put(80+side*i,75-j-(i//8),-96,white)
# Control kiosk (commands run only on a player press; no repeat command blocks).
box(-9,65,-65,9,66,-59,metal)
buttons=[(-7,'targets/near',['近距靶群','30 米']),(-3,'targets/far',['远距靶群','100 米']),(1,'targets/clear',['清除靶群','仅本图靶标']),(5,'light/day',['日间拍摄','固定晴天'])]
for xx,fn,txt in buttons:
 put(xx,67,-62,'command_block[facing=up,conditional=false]')
 bes[(xx,67,-62)]={'id':S('minecraft:command_block'),'x':I(xx),'y':I(67),'z':I(-62),'Command':S('function outpost:'+fn),'TrackOutput':B(0),'auto':B(0),'powered':B(0),'conditionMet':B(1),'SuccessCount':I(0)}
 put(xx,68,-62,'stone_button[face=floor,facing=north,powered=false]');put(xx,67,-63,metal);sign(xx,68,-63,txt)
# Guide book in a barrel by the kiosk (also install in the initial inventory).
pages=[{'text':'灰烬前哨 / ASHEN-43\n\n异星荒漠录制地图\nMinecraft Java 1.20.1\n\n前方：主炮台展示区\n左右：副展示台\n后方：飞鹰停机坪\n远方：靶区 / 工业双塔\n\n点击后续页按钮切换机位。','color':'black'},
 {'text':'拍摄机位\n\n','extra':[{'text':label+'\n\n','color':'dark_blue','clickEvent':{'action':'run_command','value':'/function outpost:camera/'+fn}} for label,fn in [('主平台 · 玩家视角','main'),('主平台 · 侧面','side'),('前哨 · 全景','wide'),('飞鹰 · 地面仰拍','eagle'),('返回控制台','spawn')]]},
 {'text':'靶群与灯光\n\n','extra':[{'text':label+'\n\n','color':'dark_red','clickEvent':{'action':'run_command','value':'/function outpost:'+fn}} for label,fn in [('30米固定靶群','targets/near'),('100米固定靶群','targets/far'),('清除本图靶群','targets/clear'),('日间','light/day'),('日落','light/sunset'),('夜景','light/night')]]},
 {'text':'录制建议\n\nF1 隐藏界面。\n地面标线从主展示台中心测量。\n\n靶群为不移动的尸壳，不会自动生成；重复点击会先清理旧靶群。\n\n爆炸可能损坏建筑，请每次用原始 ZIP 解压新的录制副本。\n\n建筑全部为原版方块，无额外材质包要求。'}]
book={'id':S('minecraft:written_book'),'Count':B(1),'tag':C({'title':S('灰烬前哨 · 拍摄手册'),'author':S('ASHEN-43'),'resolved':B(1),'pages':LS(8,[json.dumps(p,ensure_ascii=False) for p in pages])})}
put(10,67,-62,'barrel[facing=up,open=false]');bes[(10,67,-62)]={'id':S('minecraft:barrel'),'x':I(10),'y':I(67),'z':I(-62),'Items':LS(10,[dict(book,Slot=B(0))])}
sign(10,67,-63,['拍摄手册','打开右侧木桶'])
exec((ROOT/'details.py').read_text(encoding='utf8'))
# Datapack, manually activated controls and repeat-safe targets.
p= WORLD/'datapacks'/'ashen_outpost';(p/'data/outpost/functions').mkdir(parents=True,exist_ok=True)
(p/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':15,'description':'ASHEN-43 filming controls | 1.20.1'}},ensure_ascii=False),encoding='utf8')
def function(name,lines):
 f=p/'data/outpost/functions'/f'{name}.mcfunction';f.parent.mkdir(parents=True,exist_ok=True);f.write_text('\n'.join(lines)+'\n',encoding='utf8')
function('targets/clear',['tag @e[tag=ashen_target] add ashen_cleanup','tag @e[tag=ashen_target] remove ashen_target','execute as @e[tag=ashen_cleanup] at @s run tp @s ~ -128 ~','kill @e[tag=ashen_cleanup]'])
for mode,zz in [('near',5),('far',75)]:
 commands=['function outpost:targets/clear']
 for xx in (-12,-6,0,6,12):
  yy=int(height[zz+O,xx+O])+1
  commands.append(f'summon minecraft:husk {xx+.5} {yy} {zz+(.5 if mode=="near" else -.5)} '+ '{Tags:["ashen_target"],NoAI:1b,PersistenceRequired:1b,Silent:1b,DeathLootTable:"minecraft:empty",Health:100.0f,Attributes:[{Name:"minecraft:generic.max_health",Base:100.0}],Rotation:[180.0f,0.0f]}')
 function('targets/'+mode,commands)
for name,t in [('day',5500),('sunset',12500),('night',18000)]:function('light/'+name,[f'time set {t}','weather clear','gamerule doDaylightCycle false','gamerule doWeatherCycle false'])
cameras={'spawn':(0.5,67,-69.5,0,8),'main':(0.5,70,-32.5,0,0),'side':(-34,76,-34,-75,9),'wide':(-121,119,-174,-36,20),'eagle':(28,70,-8,0,-24)}
for name,pose in cameras.items():function('camera/'+name,['tp @s '+' '.join(map(str,pose))])
function('setup',['gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doFireTick false','gamerule mobGriefing false','gamerule commandBlockOutput false','gamerule sendCommandFeedback false','weather clear','time set 5500','setworldspawn 0 67 -70 0'])
(p/'data/minecraft/tags/functions').mkdir(parents=True,exist_ok=True)
(p/'data/minecraft/tags/functions/load.json').write_text('{"values":["outpost:setup"]}',encoding='utf8')
# Pack sections with Minecraft's non-spanning long-array layout.
def pack_indices(a,bits):
 per=64//bits;n=(len(a)+per-1)//per;pad=np.zeros(n*per,dtype=np.uint64);pad[:len(a)]=a;pad=pad.reshape(n,per)
 return np.bitwise_or.reduce(pad << (np.arange(per,dtype=np.uint64)*bits),axis=1)
biome=C({'palette':LS(8,['minecraft:desert'])})
def section(sy,data):
 vals,inv=np.unique(data,return_inverse=True);bs={'palette':LS(10,[palette[int(i)] for i in vals])}
 if len(vals)>1:bs['data']=T(12,pack_indices(inv,max(4,(len(vals)-1).bit_length())))
 return {'Y':B(sy),'block_states':C(bs),'biomes':biome}
regions={};start=time.time();bedrock=mat('bedrock')
under=np.full((16,16,16),stone,dtype=np.uint8);bottom=under.copy();bottom[0]=bedrock
constant_under={sy:section(sy,bottom if sy==-4 else under) for sy in range(-4,0)}
for cz in range(-24,24):
 for cx in range(-24,24):
  secs=list(constant_under.values())
  for sy in range(12):
   a=blocks[sy*16:sy*16+16,cz*16+O:cz*16+O+16,cx*16+O:cx*16+O+16]
   if np.any(a):secs.append(section(sy,a))
  # Empty upper sections still include a biome palette.
  for sy in range(12,20):secs.append({'Y':B(sy),'block_states':C({'palette':LS(10,[palette[0]])}),'biomes':biome})
  ents=[e for (xx,yy,zz),e in bes.items() if xx//16==cx and zz//16==cz]
  chunk={'DataVersion':I(3465),'xPos':I(cx),'zPos':I(cz),'yPos':I(-4),'Status':S('minecraft:full'),'LastUpdate':L(0),'InhabitedTime':L(0),'isLightOn':B(0),'sections':LS(10,secs),'block_entities':LS(10,ents),'block_ticks':LS(10,[]),'fluid_ticks':LS(10,[]),'PostProcessing':LS(9,[(2,[]) for _ in range(24)]),'structures':C({'starts':C({}),'References':C({})})}
  raw=zlib.compress(nbt(chunk),4);regions.setdefault((cx//32,cz//32),{})[(cx%32)+(cz%32)*32]=raw
 if cz%8==0:print('chunks row',cz,'seconds',round(time.time()-start),flush=True)
(WORLD/'region').mkdir(exist_ok=True)
for (rx,rz),chunks in regions.items():
 header=bytearray(8192);body=bytearray();offset=2
 for idx,raw in sorted(chunks.items()):
  record=struct.pack('>I',len(raw)+1)+b'\x02'+raw;n=(len(record)+4095)//4096
  header[idx*4:idx*4+4]=((offset<<8)|n).to_bytes(4,'big');header[4096+idx*4:4100+idx*4]=int(time.time()).to_bytes(4,'big');body.extend(record);body.extend(b'\0'*(n*4096-len(record)));offset+=n
 (WORLD/'region'/f'r.{rx}.{rz}.mca').write_bytes(header+body)
# Vanilla world settings; flat desert continuation outside the handcrafted 768 m basin.
flat={'type':S('minecraft:flat'),'settings':C({'biome':S('minecraft:desert'),'features':B(0),'lakes':B(0),'structure_overrides':LS(8,[]),'layers':LS(10,[{'height':I(1),'block':S('minecraft:bedrock')},{'height':I(123),'block':S('minecraft:stone')},{'height':I(3),'block':S('minecraft:red_sandstone')},{'height':I(1),'block':S('minecraft:red_sand')}])})}
dims={'minecraft:overworld':C({'type':S('minecraft:overworld'),'generator':C(flat)}),'minecraft:the_nether':C({'type':S('minecraft:the_nether'),'generator':C({'type':S('minecraft:noise'),'settings':S('minecraft:nether'),'biome_source':C({'type':S('minecraft:multi_noise'),'preset':S('minecraft:nether')})})}),'minecraft:the_end':C({'type':S('minecraft:the_end'),'generator':C({'type':S('minecraft:noise'),'settings':S('minecraft:end'),'biome_source':C({'type':S('minecraft:the_end')})})})}
rules={k:S(v) for k,v in {'doMobSpawning':'false','doDaylightCycle':'false','doWeatherCycle':'false','doFireTick':'false','mobGriefing':'false','commandBlockOutput':'false','sendCommandFeedback':'false','keepInventory':'true','announceAdvancements':'false'}.items()}
player={'Pos':LS(6,[0.5,67.,-69.5]),'Motion':LS(6,[0.,0.,0.]),'Rotation':LS(5,[0.,8.]),'Dimension':S('minecraft:overworld'),'playerGameType':I(1),'Health':T(5,20.),'foodLevel':I(20),'Inventory':LS(10,[dict(book,Slot=B(0))]),'abilities':C({'invulnerable':B(1),'flying':B(0),'mayfly':B(1),'instabuild':B(1),'mayBuild':B(1),'flySpeed':T(5,.05),'walkSpeed':T(5,.1)}),'OnGround':B(1),'SelectedItemSlot':I(1)}
data={'DataVersion':I(3465),'version':I(19133),'Version':C({'Id':I(3465),'Name':S('1.20.1'),'Series':S('main'),'Snapshot':B(0)}),'DragonFight':C({'DragonKilled':B(0),'PreviouslyKilled':B(0)}),'LevelName':S('灰烬前哨 ASHEN-43 · 炮台摄影场'),'GameType':I(1),'Difficulty':B(2),'DifficultyLocked':B(0),'allowCommands':B(1),'hardcore':B(0),'initialized':B(1),'SpawnX':I(0),'SpawnY':I(67),'SpawnZ':I(-70),'SpawnAngle':T(5,0.),'Time':L(0),'DayTime':L(5500),'LastPlayed':L(int(time.time()*1000)),'clearWeatherTime':I(1000000),'rainTime':I(0),'raining':B(0),'thunderTime':I(0),'thundering':B(0),'GameRules':C(rules),'WorldGenSettings':C({'seed':L(43122026),'generate_features':B(0),'bonus_chest':B(0),'dimensions':C(dims)}),'DataPacks':C({'Enabled':LS(8,['vanilla','file/ashen_outpost']),'Disabled':LS(8,[])}),'Player':C(player),'BorderCenterX':T(6,0.),'BorderCenterZ':T(6,0.),'BorderSize':T(6,59999968.),'BorderSizeLerpTarget':T(6,59999968.),'BorderSizeLerpTime':L(0)}
(WORLD/'level.dat').write_bytes(gzip.compress(nbt({'Data':C(data)})))
report={'world':str(WORLD),'size_blocks':[768,768],'chunks':2304,'block_palette':len(palette),'block_entities':len(bes),'preplaced_entities':0,'cameras':cameras,'bounds':[-384,383],'generation_seconds':round(time.time()-start,1),'vanilla_blocks_only':True}
(ROOT/'generation_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
np.save(ROOT/'height.npy',height)
print(json.dumps(report,ensure_ascii=True),flush=True)
