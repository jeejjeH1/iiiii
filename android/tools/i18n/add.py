# Adds new strings: python3 add.py 'persian key' 'English text' [...pairs]; then rebuilds en.json.
import json, os, sys, subprocess
D = os.path.dirname(os.path.abspath(__file__))
ks = json.load(open(os.path.join(D, 'ui_keys.json')))
lines = []
for k, e in zip(sys.argv[1::2], sys.argv[2::2]):
    if k in ks: continue
    ks.append(k); lines.append('%d|%s' % (len(ks) - 1, e.replace('\n', '\\n')))
json.dump(ks, open(os.path.join(D, 'ui_keys.json'), 'w'), ensure_ascii=False)
p = os.path.join(D, 'en_ui.txt'); t = open(p).read().rstrip('\n')
open(p, 'w').write(t + ('\n' + '\n'.join(lines) if lines else '') + '\n')
subprocess.run([sys.executable, os.path.join(D, 'make_en.py')], check=True)
