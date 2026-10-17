from pathlib import Path
import gzip, struct

root = Path(__file__).resolve().parents[1]
def s(value):
    raw = value.encode('utf-8')
    return struct.pack('>H', len(raw)) + raw
def tag(kind, name, payload):
    return bytes([kind]) + s(name) + payload
def integer(name, value):
    return tag(3, name, struct.pack('>i', value))
def list_tag(name, kind, entries):
    return tag(9, name, bytes([kind]) + struct.pack('>i', len(entries)) + b''.join(entries))
palette = tag(8, 'Name', s('minecraft:air')) + b'\x00'
position = list_tag('pos', 3, [struct.pack('>i', 0)] * 3) + integer('state', 0) + b'\x00'
payload = (b'\x0a\x00\x00' + integer('DataVersion', 3465) +
           list_tag('size', 3, [struct.pack('>i', n) for n in (32, 160, 32)]) +
           list_tag('palette', 10, [palette]) + list_tag('blocks', 10, [position]) +
           list_tag('entities', 10, []) + b'\x00')
out = root / 'src/main/resources/data/sbw_helldiver/structures/sentry_test.nbt'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(gzip.compress(payload, mtime=0))
print(out)
