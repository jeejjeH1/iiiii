# Lists L("...") keys in the app that have no English line yet.
import json, glob, re, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract import ROOT
from rewrite import unescape
en = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../app/src/main/assets/en.json')))
seen = set()
for f in glob.glob(ROOT + '**/*.kt', recursive=True) + glob.glob(ROOT + '**/*.java', recursive=True):
    for m in re.finditer(r'L\("((?:[^"\\\n]|\\.)*)"', open(f).read()):
        k = unescape(m.group(1), False)
        if k not in en and k not in seen and re.search('[؀-ۿ]', k):
            seen.add(k); print(f[len(ROOT):], '|', k.replace('\n', '\\n'))
