import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/sbw_helldiver/lang'
entries={
'item.sbw_helldiver.machine_deployment_beacon_stratagem':('设备部署信标','Equipment Deployment Beacon'),
'entity.sbw_helldiver.machine_deployment_beacon':('设备部署信标','Equipment Deployment Beacon'),
'machine.sbw_helldiver.bioprocessor':('生物处理器','Bioprocessor'),
'machine.sbw_helldiver.refinery':('精炼厂','Refinery'),
'message.sbw_helldiver.beacon_no_energy':('电能不足：每次投掷需要 1,000 RF','Insufficient energy: each throw requires 1,000 RF'),
'tooltip.sbw_helldiver.beacon_energy':('储能：%s / %s RF · 每次投掷 1,000 RF','Energy: %s / %s RF · 1,000 RF per throw'),
'terminal.sbw_helldiver.title':('设备部署终端','EQUIPMENT DEPLOYMENT'),
'terminal.sbw_helldiver.busy':('其他玩家正在操作这个终端','Another player is using this terminal'),
'terminal.sbw_helldiver.cost':('铁块 × 1','Iron Block × 1'),
'terminal.sbw_helldiver.submit':('输入指令提交材料','Enter code to submit materials'),
'terminal.sbw_helldiver.back':('← 返回','← Back'),
'terminal.sbw_helldiver.controls':('方向键 / WASD 输入 · Esc 退出','Arrows / WASD · Esc to exit'),
'terminal.sbw_helldiver.no_space':('部署空间不足，请清空设备区域','Insufficient deployment space'),
'terminal.sbw_helldiver.no_material':('材料不足：请补齐红色项目','Missing materials: complete the red entries'),
'terminal.sbw_helldiver.material_line':('%s %s/%s','%s %s/%s'),
'terminal.sbw_helldiver.waiting':('材料已提交，等待设备抵达','Materials submitted · awaiting equipment'),
'terminal.sbw_helldiver.deployment_busy':('已有设备正在部署，请稍后再试','Another equipment deployment is in progress'),
'terminal.sbw_helldiver.countdown':('设备部署倒计时：%s 秒','Equipment deploying in %s s'),
'terminal.sbw_helldiver.prototype_ready':('%s：部署倒计时结束（集装箱部署尚未接入）','%s: deployment countdown complete (container deployment not yet implemented)'),
'subtitles.sbw_helldiver.terminal_input':('终端按键','Terminal key pressed'),
'subtitles.sbw_helldiver.terminal_error':('终端输入错误','Terminal input rejected'),
}
for file,language in [('zh_cn.json',0),('en_us.json',1)]:
    p=root/file;data=json.loads(p.read_text(encoding='utf-8'));data.update({k:v[language]for k,v in entries.items()});p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
