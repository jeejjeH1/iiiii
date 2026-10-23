# Extract Persian string literals (with Kotlin templates) from the app sources.
# Each unit: parts = list of literal text and placeholders; key = text with {0},{1}... for placeholders.
import re, glob, json, sys
ROOT = __import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)), '../../app/src/main/java/app/goallive/live/')
FA = re.compile('[؀-ۿ]')

def parse_string(s, i):
    """s[i] == '"'. Returns (parts, end_index) where parts: list of ('lit', text) / ('ph', code)."""
    assert s[i] == '"'
    if s.startswith('"""', i):
        j = s.index('"""', i + 3)
        return [('lit', s[i+3:j])], j + 3
    i += 1; parts = []; buf = ''
    while True:
        c = s[i]
        if c == '\\':
            buf += s[i:i+2]; i += 2; continue
        if c == '"':
            if buf: parts.append(('lit', buf))
            return parts, i + 1
        if c == '$' and i + 1 < len(s) and s[i+1] == '{':
            if buf: parts.append(('lit', buf)); buf = ''
            depth = 1; j = i + 2
            while depth:
                if s[j] == '"':
                    _, j = parse_string(s, j); continue
                if s[j] == '{': depth += 1
                elif s[j] == '}': depth -= 1
                j += 1
            parts.append(('ph', s[i:j])); i = j; continue
        if c == '$' and i + 1 < len(s) and (s[i+1].isalpha() or s[i+1] == '_'):
            if buf: parts.append(('lit', buf)); buf = ''
            m = re.match(r'\$[A-Za-z_][A-Za-z0-9_]*', s[i:])
            parts.append(('ph', m.group(0))); i += m.end(); continue
        if c == '\n': return None, i  # not a string (char literal context etc.)
        buf += c; i += 1

def units(path):
    s = open(path).read(); i = 0; out = []
    while i < len(s):
        c = s[i]
        if s.startswith('//', i): i = s.index('\n', i) if '\n' in s[i:] else len(s); continue
        if s.startswith('/*', i): i = s.index('*/', i) + 2; continue
        if c == "'" :
            m = re.match(r"'(\\.|[^'\\])'", s[i:])
            if m: i += m.end(); continue
        if c == '"':
            parts, j = parse_string(s, i)
            if parts is None: i += 1; continue
            text = ''.join(p[1] for p in parts if p[0] == 'lit')
            if FA.search(text):
                key = ''; n = 0; phs = []
                for k, v in parts:
                    if k == 'lit': key += v
                    else: key += '{%d}' % n; n += 1; phs.append(v)
                out.append({'key': key, 'phs': phs, 'line': s.count('\n', 0, i) + 1})
            i = j; continue
        i += 1
    return out

if __name__ == '__main__':
    skip = set(sys.argv[1:])
    res = {}
    for f in sorted(glob.glob(ROOT + '**/*.kt', recursive=True) + glob.glob(ROOT + '**/*.java', recursive=True)):
        rel = f[len(ROOT):]
        if rel in skip: continue
        for u in units(f):
            res.setdefault(u['key'], {'file': rel, 'line': u['line'], 'phs': u['phs']})
    json.dump(res, sys.stdout, ensure_ascii=False, indent=0)
