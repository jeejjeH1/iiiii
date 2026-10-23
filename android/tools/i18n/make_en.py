# Builds app/src/main/assets/en.json from the English lines (en_ui.txt, en_club.txt: "index|text", index into *_keys.json).
import json, os, re
D = os.path.dirname(os.path.abspath(__file__))
def load(txt, keys):
    keys = json.load(open(os.path.join(D, keys))); out = {}
    for line in open(os.path.join(D, txt), encoding='utf-8').read().split('\n'):
        if line.strip():
            i, t = line.split('|', 1); k = keys[int(i)]; t = t.replace('\\n', '\n')
            # keep the spaces that join this piece to the next one
            if k.endswith(' ') and not t.endswith(' '): t += ' '
            if k.startswith(' ') and not t.startswith(' '): t = ' ' + t
            out[k] = t
    return out
en = load('en_ui.txt', 'ui_keys.json'); en.update(load('en_club.txt', 'club_keys.json'))
bad = [k for k, v in en.items() if sorted(re.findall(r'\{\d\}', k)) != sorted(re.findall(r'\{\d\}', v))]
assert not bad, bad
json.dump(en, open(os.path.join(D, '../../app/src/main/assets/en.json'), 'w'), ensure_ascii=False, indent=0)
print(len(en), 'strings')
