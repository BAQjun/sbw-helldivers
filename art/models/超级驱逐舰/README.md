# 超级驱逐舰远景模型 / Distant Super Destroyer

按已核对的原版完整舰体整理低面数网格，保留细长舰首、机舱比例、舰尾侧板以及中央六个与两侧各两个引擎的布局。薄装甲补有背面与实体封边，舰体设有封闭内壳以覆盖远景网格接缝。采用干净的灰黄配色，附独立的引擎自发光遮罩。

- `超级驱逐舰_远景.bbmodel`：Blockbench 可编辑网格，包含内嵌贴图。
- `贴图/舰体图集.png`：256 × 256 舰体图集。
- `贴图/舰体图集_自发光.png`：引擎发光遮罩。
- `模型信息.json`：三角面数、尺寸、坐标约定和引擎挂点。

共 9,546 个三角面，14 个可编辑网格分组。舰首朝 -Z，顶部朝 +Y，每米 16 模型单位。按原版尺寸约长 244 米、宽 96.5 米、高 45.4 米；实际游戏中的显示尺度应由后续天空渲染器决定。此文件是远景美术资源，尚未接入游戏天空或轨道战备逻辑。

The low-detail mesh follows the verified complete original hull, retaining its long bow, machinery sections, aft side panels and ten-engine layout. Thin armor has physical backs and capped rims; closed inset hull sections cover gaps in the distant mesh. It uses a clean grey/yellow palette with a separate engine emissive mask.

The editable Blockbench source contains 9,546 triangles in fourteen mesh groups. Forward is -Z, up is +Y, with 16 model units per metre. The source dimensions are approximately 244 × 96.5 × 45.4 metres; a future sky renderer should determine the display scale. This is a distant art asset, not an implemented sky renderer or orbital stratagem.

The preparation tool requires the complete extracted normal hull LOD4 as an external reference. Raw extraction files and shadow proxy meshes are not included.
