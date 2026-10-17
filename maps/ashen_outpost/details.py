# Industrial dressing uses vanilla functional blocks; keep firing/aircraft corridors clear.
# HANGAR: ribbed blast-door surround, open shutter, inspection lamps and protected jambs.
for xx in (-93,-65):
 box(xx,67,-48,xx+1,79,-45,metal)
 for yy in range(68,79,3):put(xx,yy,-44,'polished_blackstone_button[face=wall,facing=south,powered=false]')
 for yy in range(67,73):put(xx+1,yy,-44,yellow if yy%2==0 else black)
box(-92,80,-45,-66,81,-45,iron)
for xx in range(-91,-66,2):
 put(xx,79,-45,'iron_trapdoor[facing=south,half=top,open=true,powered=false,waterlogged=false]')
 put(xx,82,-45,'iron_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
for xx in (-89,-79,-69):put(xx,78,-46,'end_rod[facing=down]')
# Long side vents framed with stone cutters and open iron trapdoors.
for zz in range(-86,-51,9):
 box(-102,70,zz,-102,75,zz+4,black)
 for yy in range(71,75):
  for dz in (1,2,3):put(-103,yy,zz+dz,'iron_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]')
 put(-103,76,zz+2,'lightning_rod[facing=up,powered=false,waterlogged=false]')
# Roof HVAC housings, horizontal grilles, ducts and roof access.
for cx,cz in [(-89,-79),(-71,-79),(-89,-60),(-71,-60)]:
 box(cx-3,83,cz-3,cx+3,86,cz+3,metal);box(cx-2,86,cz-2,cx+2,86,cz+2,black)
 for dx in range(-2,3):
  for dz in range(-2,3):put(cx+dx,87,cz+dz,'iron_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
 for yy in (84,85):
  for dx in (-1,0,1):put(cx+dx,yy,cz-4,'iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]')
box(-79,83,-87,-77,84,-53,iron)
for zz in range(-87,-52,5):box(-80,83,zz,-76,85,zz,metal)
for yy in range(67,87):put(-58,yy,-58,'ladder[facing=east,waterlogged=false]')
box(-60,86,-61,-56,86,-55,steel)
for zz in range(-60,-54):put(-56,87,zz,'iron_bars[east=false,north=true,south=true,west=false,waterlogged=false]')
# Skylights between ribs.
box(-87,81,-73,-72,82,-69,glass)
# Interior workbenches, real tool stations, racks and hanging fixtures.
for cx,b in [(-94,'smithing_table'),(-92,'crafting_table'),(-90,'stonecutter[facing=south]'),(-88,'grindstone[face=floor,facing=south]'),(-86,'anvil[facing=east]')]:put(cx,67,-60,b)
for xx in (-94,-92,-90):
 put(xx,67,-88,'blast_furnace[facing=south,lit=true]');put(xx,68,-88,'smoker[facing=south,lit=true]');put(xx,69,-88,'hopper[facing=down,enabled=true]')
 box(xx,70,-88,xx,80,-88,metal)
for xx in (-94,-93,-92,-91,-90):put(xx,70,-61,'tripwire_hook[facing=south,attached=false,powered=false]')
box(-95,70,-62,-85,70,-62,steel)
for xx in (-93,-87):
 for yy in range(74,80):put(xx,yy,-64,'chain[axis=y,waterlogged=false]')
 put(xx,73,-64,'lantern[hanging=true,waterlogged=false]')
# A lift pit with service rails, no decorative entities.
box(-84,66,-77,-72,66,-64,black)
for xx in (-84,-72):
 for zz in range(-77,-63):put(xx,67,zz,'rail[shape=north_south,waterlogged=false]')
for xx in (-84,-72):put(xx,67,-63,'piston[facing=up,extended=false]')
for xx in (-93,-88,-68):
 for yy in (67,68,69):put(xx,yy,-83,'barrel[facing=south,open=false]')
# Command bunker: armored window mullions, instrument panels and usable entry doors.
for xx in range(65,95,5):box(xx,72,-50,xx,75,-50,metal)
for xx in (64,70,88,94):put(xx,76,-50,'stone_button[face=wall,facing=south,powered=false]')
box(77,68,-53,84,74,-50,metal);box(79,68,-53,82,72,-50,air)
for xx,hinge in [(80,'left'),(81,'right')]:
 put(xx,68,-51,f'iron_door[facing=south,half=lower,hinge={hinge},open=false,powered=false]')
 put(xx,69,-51,f'iron_door[facing=south,half=upper,hinge={hinge},open=false,powered=false]')
 for zz in (-50,-52):put(xx,68,zz,'stone_pressure_plate[powered=false]')
box(77,75,-53,84,75,-48,steel)
put(78,74,-49,'lantern[hanging=true,waterlogged=false]');put(83,74,-49,'lantern[hanging=true,waterlogged=false]')
# Status lights / switching bank on the outer east wall.
box(101,69,-83,102,75,-64,metal)
for zz in (-80,-75,-70):
 put(103,73,zz,'redstone_lamp[lit=true]');put(102,73,zz,'redstone_block')
 put(103,71,zz,'lever[face=wall,facing=east,powered=true]')
 box(103,69,zz-1,103,69,zz+1,'copper_block')
for zz in range(-87,-58):put(103,77,zz,'lightning_rod[facing=south,powered=false,waterlogged=false]')
for zz in (-86,-78,-70,-62):put(103,76,zz,'iron_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]')
# Roof access and paired sensor / cooling units.
for xx,zz in [(69,-81),(91,-81)]:
 box(xx-3,81,zz-4,xx+3,83,zz+4,metal)
 for dx in range(-2,3):
  for dz in range(-3,4):put(xx+dx,84,zz+dz,'iron_trapdoor[facing=east,half=bottom,open=false,powered=false,waterlogged=false]')
 box(xx,85,zz,xx,89,zz,'wall[up=true]' if False else iron)
 put(xx,90,zz,'lightning_rod[facing=up,powered=false,waterlogged=false]')
for yy in range(68,82):put(100,yy,-61,'ladder[facing=east,waterlogged=false]')
# Table seats, map table, weapon prep and equipment cabinets inside.
for xx in range(66,95,5):
 put(xx,70,-85,'comparator[facing=north,mode=compare,powered=false]')
 put(xx+1,70,-85,'lever[face=floor,facing=north,powered=false]')
 put(xx,68,-82,'polished_blackstone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
box(75,68,-73,84,69,-68,metal);box(76,70,-72,83,70,-69,'cyan_stained_glass')
for xx in (76,83):put(xx,68,-66,'polished_blackstone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
for zz in (-86,-83,-80):
 put(96,68,zz,'barrel[facing=west,open=false]');put(96,69,zz,'barrel[facing=west,open=false]');put(96,70,zz,'iron_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]')
put(65,68,-57,'cartography_table');put(66,68,-57,'crafting_table');put(67,68,-57,'water_cauldron[level=3]')
# Lower rear wall ribs below the emblem and narrow white identification plates.
for xx in range(64,98,6):box(xx,68,-96,xx,69,-96,metal)
for xx in (65,95):put(xx,78,-96,'lightning_rod[facing=north,powered=false,waterlogged=false]')
# Shipping containers: corner castings, locking bars, hinges and door handles.
for cx,cz in [(77,-23),(94,-18),(-82,-22),(-83,-10),(87,1)]:
 for dx in (-5,5):
  for dz in (-4,4):
   for yy in (66,71):put(cx+dx,yy,cz+dz,iron)
 for dx in (-2,2):
  for yy in range(67,71):put(cx+dx,yy,cz-5,'chain[axis=y,waterlogged=false]')
  put(cx+dx,69,cz-6,'tripwire_hook[facing=north,attached=false,powered=false]')
 put(cx,71,cz-5,white)
# Fuel pump control and maintenance platform.
box(79,65,21,110,66,51,metal)
for cx,cz in [(88,27),(103,29),(99,44)]:
 for yy in range(66,80):put(cx+5,yy,cz,'ladder[facing=east,waterlogged=false]')
 put(cx,79,cz,'hopper[facing=down,enabled=true]');put(cx,80,cz,'lightning_rod[facing=up,powered=false,waterlogged=false]')
 for zz in (cz-2,cz+2):put(cx+5,68,zz,'lever[face=wall,facing=east,powered=false]')
 box(cx-2,67,cz-6,cx+2,70,cz-6,metal)
 put(cx,69,cz-7,'furnace[facing=north,lit=false]')
# Turbine stacks: shell ribs, maintenance ledges, exhaust collars, working signal lights.
for cx,cz in [(-66,151),(69,159)]:
 for dx in (-7,7):
  box(cx+dx,71,cz-4,cx+dx,111,cz-4,metal)
  for yy in range(74,112,6):put(cx+dx,yy,cz-5,'stone_button[face=wall,facing=north,powered=false]')
 for dx in range(-7,8):
  put(cx+dx,84,cz-9,'iron_bars[east=true,north=false,south=false,west=true,waterlogged=false]')
 for yy in range(73,80):
  for dx in (-2,0,2):put(cx+dx,yy,cz-8,'iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]')
 for dx,dz in [(-6,-6),(-6,6),(6,-6),(6,6)]:put(cx+dx,117,cz+dz,'redstone_block')
 # Four signal-smoke points per stack, recessed into the exhaust.
 for dx,dz in [(-1,-1),(-1,1),(1,-1),(1,1)]:
  put(cx+dx,117,cz+dz,'hay_block[axis=y]')
  put(cx+dx,118,cz+dz,'campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]')
 for dx in range(-4,5):
  for dz in range(-4,5):put(cx+dx,119,cz+dz,'iron_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
# Radar hut panels and rooftop guard rail.
for xx in range(108,131):
 put(xx,79,51,'iron_bars[east=true,north=false,south=false,west=true,waterlogged=false]')
for xx in (114,119,124):
 put(xx,73,51,'redstone_lamp[lit=true]');put(xx,73,52,'redstone_block')
 put(xx,71,51,'lever[face=wall,facing=north,powered=false]')
for yy in range(69,80):put(130,yy,67,'ladder[facing=east,waterlogged=false]')
# Pipe bridge fittings, suspended insulators and pump housings.
for xx in range(-54,57,7):
 box(xx,75,162,xx+1,79,166,metal);box(xx,77,163,xx+1,78,165,iron)
 for yy in (74,73):put(xx,yy,164,'chain[axis=y,waterlogged=false]')
 put(xx,72,164,'lantern[hanging=true,waterlogged=false]')
# Utility boxes, drainage grates and perimeter floodlight wiring.
for cx,cz in [(-27,-47),(27,-47),(-58,-108),(58,-108),(-102,-12),(102,-12),(-25,36),(25,36)]:
 box(cx+1,66,cz,cx+2,68,cz+1,metal);put(cx+2,67,cz-1,'lever[face=wall,facing=north,powered=false]')
 for yy in range(68,78):put(cx,yy,cz+1,'lightning_rod[facing=up,powered=false,waterlogged=false]')
for zz in range(-137,-4):
 for xx in (-12,12):
  put(xx,65,zz,'iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]')
# Landing apron light housings, blast deflectors and symmetrical approach beacons.
for xx in (-31,31):
 for zz in range(-124,-90,6):
  put(xx,66,zz,metal);put(xx,67,zz,lamp);put(xx,68,zz,'iron_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
for xx in range(-16,17):
 box(xx,66,-138,xx,68,-138,metal);put(xx,69,-138,'polished_deepslate_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
for xx in (-22,22):
 box(xx,66,-135,xx+1,67,-129,'barrel[facing=up,open=false]')
# Low equipment crates around, not on, the clear turret pads.
for cx,cz in [(-25,-18),(25,-18),(-52,-24),(52,-24)]:
 put(cx,66,cz,'barrel[facing=south,open=false]');put(cx+1,66,cz,'barrel[facing=south,open=false]');put(cx,67,cz,'heavy_weighted_pressure_plate[power=0]')
# Embed kiosk command blocks one block lower so only a dark instrument desk is visible.
for xx,fn,txt in buttons:
 old=bes.pop((xx,67,-62));old['y']=I(66);bes[(xx,66,-62)]=old
 put(xx,66,-62,'command_block[facing=up,conditional=false]');put(xx,67,-62,metal)
# Reinstate smoke-block / beacon-lamp power that survives block updates.
for xx in (-7,7):put(xx,117,-168,'redstone_block')
# Put the kiosk labels onto the actual front face of the desk (solid support behind).
for xx,fn,txt in buttons:
 bes.pop((xx,68,-63),None);put(xx,68,-63,air)
 sign(xx,67,-63,txt)
# Final visual QA refinements, recorded as a small runtime patch for the warmed test copy.
before_final=blocks.copy()
# Both sides of the hangar need readable equipment at ground level.
for zz in (-85,-73,-61):
 box(-57,70,zz,-57,74,zz+5,black)
 for yy in (71,72,73):
  for dz in range(1,5):put(-56,yy,zz+dz,'iron_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]')
 box(-57,76,zz,-57,77,zz+5,metal)
 for dz in (0,5):put(-56,76,zz+dz,'stone_button[face=wall,facing=east,powered=false]')
for zz in range(-88,-51):put(-56,79,zz,'lightning_rod[facing=south,powered=false,waterlogged=false]')
# Rear service panels, wall banding and an exterior utility station.
box(-98,69,-94,-60,69,-94,metal)
for xx in (-92,-80,-68):
 box(xx-3,72,-94,xx+3,76,-94,metal)
 for dx in range(-2,3):
  for yy in (73,74,75):put(xx+dx,yy,-95,'iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]')
 put(xx,71,-95,'lever[face=wall,facing=north,powered=false]')
# Hangar workbench accessible at ordinary player height.
box(-96,68,-73,-94,69,-56,air)
for cx,b in [(-94,'smithing_table'),(-92,'crafting_table'),(-90,'stonecutter[facing=south]'),(-88,'grindstone[face=floor,facing=south]'),(-86,'anvil[facing=east]')]:put(cx,67,-60,b)
for xx in (-91,-78,-65):
 for zz in (-82,-69,-55):
  for yy in range(75,80):put(xx,yy,zz,'chain[axis=y,waterlogged=false]')
  box(xx-1,74,zz,xx+1,74,zz,lamp)
  for dx in (-1,0,1):put(xx+dx,73,zz,'iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]')
for xx in (-94,-88):put(xx,71,-61,lamp)
# Screen backlights and evenly spaced ceiling lights make the console usable for close-ups.
for xx in range(67,94,6):box(xx,70,-88,xx+2,71,-88,lamp)
for zz in (-86,-76,-63):
 for xx in (67,76,85,94):box(xx,75,zz,xx+1,75,zz,lamp)
for xx in (75,84):
 for zz in (-73,-68):put(xx,69,zz,lamp)
for zz in (-86,-78,-70,-62):
 box(59,70,zz,59,74,zz+3,metal)
 for yy in (71,72,73):
  for dz in (1,2):put(58,yy,zz+dz,'iron_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]')
# Export only changes since the validated scene for fast in-game preview, preserving lit chunks.
yy,zz,xx=np.nonzero(blocks!=before_final);reverse={v:k for k,v in ids.items()}
patch=[[int(a-O),int(c),int(b-O),'minecraft:'+reverse[int(blocks[c,b,a])]] for c,b,a in zip(yy,zz,xx)]
(ROOT/'visual_patch.json').write_text(json.dumps(patch),encoding='utf8')
del before_final
