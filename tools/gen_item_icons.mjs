/**
 * 生成三个战备物品的**占位**图标（16×16 PNG）。
 *
 * 资产包里只有模型贴图，没有物品图标，所以先出一版能看的像素图顶着用，
 * 美术做好之后直接替换 `src/main/resources/assets/sbw_helldiver/textures/item/`
 * 下的同名 PNG 即可，不需要改这个脚本、也不用动任何代码。
 *
 * 用法：`node tools/gen_item_icons.mjs`
 *
 * 尺寸与配色照卓越前线原版物品的规格：16×16、深灰底 + 暗金图案。
 */
import zlib from 'node:zlib'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const SIZE = 16

/** 像素字符 → RGBA。点号是透明。 */
const PALETTE = {
  '.': [0, 0, 0, 0],
  '+': [22, 27, 24, 255], // 底边缘，比底色再深一点
  '#': [38, 46, 41, 255], // 底色：深灰绿，取自空投仓舱体
  G: [198, 162, 74, 255], // 亮金：图案主色
  g: [140, 110, 45, 255], // 暗金：图案描边
}

/**
 * 三张图的像素稿。
 *
 * 都用同一套深色底牌，图案区分靠形状：
 * - 机枪：向下箭头 + 单根枪管（暗示「投下来一门炮」）
 * - 加特林：向下箭头 + 三根枪管
 * - 补给：箱子
 */
const ICONS = {
  rocket_sentry_stratagem: [
    '................', '.++++++++++++++.', '.+############+.', '.+############+.',
    '.+###GG##GG###+.', '.+##GGGGGGGG##+.', '.+##G##GG##G##+.', '.+##GGGGGGGG##+.',
    '.+##G##GG##G##+.', '.+##GGGGGGGG##+.', '.+#####GG#####+.', '.+#####GG#####+.',
    '.+###GGGGGG###+.', '.+############+.', '.++++++++++++++.', '................',
  ],
  autocannon_sentry_stratagem: [
    '................', '.++++++++++++++.', '.+############+.', '.+#####GG#####+.',
    '.+#####GG#####+.', '.+#####GG#####+.', '.+#####GG#####+.', '.+#####GG#####+.',
    '.+####GGGG####+.', '.+####GggG####+.', '.+###GGGGGG###+.', '.+#####GG#####+.',
    '.+###GGGGGG###+.', '.+############+.', '.++++++++++++++.', '................',
  ],
  machine_gun_sentry_stratagem: [
    '................',
    '.++++++++++++++.',
    '.+############+.',
    '.+############+.',
    '.+#####GG#####+.',
    '.+#####GG#####+.',
    '.+#####GG#####+.',
    '.+####GGGG####+.',
    '.+#####GG#####+.',
    '.+#####GG#####+.',
    '.+###GGGGGG###+.',
    '.+#####GG#####+.',
    '.+#####GG#####+.',
    '.+############+.',
    '.++++++++++++++.',
    '................',
  ],
  gatling_sentry_stratagem: [
    '................',
    '.++++++++++++++.',
    '.+############+.',
    '.+####GGGG####+.',
    '.+#####GG#####+.',
    '.+#####GG#####+.',
    '.+#####GG#####+.',
    '.+#####GG#####+.',
    '.+#####GG#####+.',
    '.+#####GG#####+.',
    '.+GG##GG##GG##+.',
    '.+GG##GG##GG##+.',
    '.+GG##GG##GG##+.',
    '.+############+.',
    '.++++++++++++++.',
    '................',
  ],
}

// ==================== PNG 编码 ====================
// Node 没有内置图像库，但 PNG 的容器格式很简单，直接手写：
// 签名 + IHDR + IDAT(zlib 压缩的扫描线) + IEND，每段带 CRC32。

const CRC_TABLE = (() => {
  const table = new Int32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    table[n] = c
  }
  return table
})()

function crc32(buf) {
  let c = -1
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8)
  return (c ^ -1) >>> 0
}

function chunk(type, data) {
  const length = Buffer.alloc(4)
  length.writeUInt32BE(data.length, 0)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body), 0)
  return Buffer.concat([length, body, crc])
}

function encodePng(width, height, rgba) {
  const stride = width * 4
  // 每条扫描线前面加一个 filter 字节（0 = 不过滤）。
  const raw = Buffer.alloc((stride + 1) * height)
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride)
  }

  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0)
  ihdr.writeUInt32BE(height, 4)
  ihdr[8] = 8 // 位深
  ihdr[9] = 6 // 颜色类型 6 = RGBA
  // 10..12 保持 0：压缩 / 过滤 / 隔行方式都用默认

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

// ==================== 主流程 ====================

const outDir = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  'src',
  'main',
  'resources',
  'assets',
  'sbw_helldiver',
  'textures',
  'item',
)
fs.mkdirSync(outDir, { recursive: true })

for (const [name, rows] of Object.entries(ICONS)) {
  if (rows.length !== SIZE) throw new Error(`${name}: 需要 ${SIZE} 行，实际 ${rows.length} 行`)
  rows.forEach((row, y) => {
    if (row.length !== SIZE) throw new Error(`${name} 第 ${y} 行: 需要 ${SIZE} 列，实际 ${row.length} 列`)
  })

  const rgba = Buffer.alloc(SIZE * SIZE * 4)
  for (let y = 0; y < SIZE; y++) {
    for (let x = 0; x < SIZE; x++) {
      const color = PALETTE[rows[y][x]]
      if (!color) throw new Error(`${name}: 未知像素字符 '${rows[y][x]}'`)
      rgba.set(color, (y * SIZE + x) * 4)
    }
  }

  const file = path.join(outDir, `${name}.png`)
  fs.writeFileSync(file, encodePng(SIZE, SIZE, rgba))
  console.log(`已生成 ${path.relative(process.cwd(), file)}`)
}
