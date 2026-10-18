# Side-by-side + overlay of reference crops vs. renders: python3 tools/compare.py <ref.png> <renderdir> <out.png>
import sys
from PIL import Image, ImageChops
ref = Image.open(sys.argv[1]).convert('RGBA')
bg = Image.new('RGBA', ref.size, (40, 40, 40, 255)); bg.alpha_composite(ref); ref = bg.convert('RGB')
crops = {'front': (0, 480, 1300, 2100), 'side': (1560, 480, 2560, 2100), 'back': (2800, 480, 3900, 2100)}
rows = []
for k, box in crops.items():
    a = ref.crop(box)
    b = Image.open(f'{sys.argv[2]}/{k}.png').convert('RGB').resize(a.size)
    o = Image.blend(a, b, 0.5)
    row = Image.new('RGB', (a.width * 3, a.height))
    row.paste(a, (0, 0)); row.paste(b, (a.width, 0)); row.paste(o, (a.width * 2, 0))
    rows.append(row)
W = max(r.width for r in rows); Ht = sum(r.height for r in rows)
out = Image.new('RGB', (W, Ht), (20, 20, 20)); y = 0
for r in rows: out.paste(r, (0, y)); y += r.height
scale = float(sys.argv[4]) if len(sys.argv) > 4 else 0.4
out.resize((int(W * scale), int(Ht * scale))).save(sys.argv[3])
