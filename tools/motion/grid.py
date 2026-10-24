import sys
from PIL import Image, ImageDraw
# python3 grid.py out.jpg name1 name2 ... : each screenshot with a 100px grid labelled in real pixels, half size, side by side
out = sys.argv[1]; names = sys.argv[2:]
W, H = 400, 860
im = Image.new('RGB', (W * len(names), H), 'white')
for i, n in enumerate(names):
    a = Image.open(f'shots/{n}.png').convert('RGB')
    d = ImageDraw.Draw(a)
    for y in range(100, 1720, 100): d.line([(0, y), (800, y)], fill=(255, 0, 0), width=1); d.text((4, y + 2), str(y), fill=(255, 60, 60))
    for x in range(100, 800, 100): d.line([(x, 0), (x, 1720)], fill=(0, 200, 255), width=1); d.text((x + 2, 4), str(x), fill=(0, 200, 255))
    im.paste(a.resize((W, H)), (i * W, 0))
im.save(out, quality=85)
