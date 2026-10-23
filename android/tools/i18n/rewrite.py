# Rewrites Persian string literals into L("key {0}", args...) calls. Dry run prints keys; --apply edits files.
import re, glob, json, sys
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from extract import parse_string, FA
ROOT = __import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)), '../../app/src/main/java/app/goallive/live/')
SKIP_FILES = {'data/Tr.kt', 'data/TeamsFa.kt', 'I18n.kt'}
# declarations in AssistantBrain whose strings are things to look for in a question, not text to show
ASSIST_SKIP_DECLS = ['private val PLAYERS', 'private val LEAGUES', 'private val NICKS', 'private fun dayOffset', 'private fun numberIn', 'internal fun norm',
                     'private fun returnFa']

def decl_ranges(s, names):
    out = []
    for n in names:
        i = s.find(n)
        if i < 0: continue
        # until the next top-level declaration
        m = re.search(r'\n(private |internal |fun |val |class |object |/\*\*|@)', s[i + len(n):])
        out.append((i, i + len(n) + (m.start() if m else len(s))))
    return out

def in_call(s, i, names):
    """Is position i inside the argument list of one of the calls names(...) on the same statement?"""
    depth = 0; j = i - 1
    while j >= 0 and j > i - 4000:
        c = s[j]
        if c == ')': depth += 1
        elif c == '(':
            if depth == 0:
                k = j - 1
                while k >= 0 and (s[k].isalnum() or s[k] == '_'): k -= 1
                if s[k + 1:j] in names: return True
            else: depth -= 1
        elif c == '\n' and depth == 0 and s[j-1] not in ',(': pass
        j -= 1
    return False

def process(path, apply):
    rel = path[len(ROOT):]
    s = open(path).read(); java = path.endswith('.java')
    skips = decl_ranges(s, ASSIST_SKIP_DECLS) if rel == 'ui/AssistantBrain.kt' else []
    edits = []; keys = []
    i = 0
    while i < len(s):
        c = s[i]
        if s.startswith('//', i): i = s.find('\n', i); i = len(s) if i < 0 else i; continue
        if s.startswith('/*', i): i = s.index('*/', i) + 2; continue
        if c == "'":
            m = re.match(r"'(\\.|[^'\\])'", s[i:])
            if m: i += m.end(); continue
        if c == '"':
            parts, j = parse_string(s, i)
            if parts is None: i += 1; continue
            text = ''.join(p[1] for p in parts if p[0] == 'lit')
            skip = any(a <= i < b for a, b in skips) or (rel == 'ui/AssistantBrain.kt' and in_call(s, i, {'has', 'word', 'norm', 'contains', 'startsWith', 'endsWith'}))
            already = s[max(0, i - 2):i] == 'L('
            if FA.search(text) and not skip and not already:
                raw = s.startswith('"""', i)
                key = ''; args = []
                for k, v in parts:
                    if k == 'lit': key += v
                    else:
                        key += '{%d}' % len(args)
                        args.append(v[2:-1] if v.startswith('${') else v[1:])
                lit = ('"""' + key + '"""') if raw else ('"' + key + '"')
                call = ('I18n.L(' if java else 'L(') + lit + ''.join(', ' + a for a in args) + ')'
                edits.append((i, j, call)); keys.append((key, raw))
            i = j; continue
        i += 1
    if apply and edits:
        for a, b, t in reversed(edits): s = s[:a] + t + s[b:]
        s = re.sub(r'\bconst val\b(?=[^\n]*L\()', 'val', s)
        if not java and 'import app.goallive.live.L\n' not in s and not rel.startswith('I18n'):
            s = re.sub(r'(^package [^\n]+\n)', r'\1\nimport app.goallive.live.L\n', s, count=1, flags=re.M)
        if java and 'I18n.L(' in s and 'import app.goallive.live.I18n;' not in s and not s.startswith('package app.goallive.live;'):
            s = re.sub(r'(package [^\n]+;\n)', r'\1\nimport app.goallive.live.I18n;\n', s, count=1)
        open(path, 'w').write(s)
    return rel, keys

def unescape(k, raw):
    if raw: return k
    out = ''; i = 0
    while i < len(k):
        if k[i] == '\\':
            n = k[i+1]
            if n == 'n': out += '\n'
            elif n == 't': out += '\t'
            elif n == 'u': out += chr(int(k[i+2:i+6], 16)); i += 4
            else: out += n
            i += 2
        else: out += k[i]; i += 1
    return out

if __name__ == '__main__':
    apply = '--apply' in sys.argv
    allk = {}
    for f in sorted(glob.glob(ROOT + '**/*.kt', recursive=True) + glob.glob(ROOT + '**/*.java', recursive=True)):
        if f[len(ROOT):] in SKIP_FILES: continue
        rel, keys = process(f, apply)
        for k, raw in keys: allk.setdefault(unescape(k, raw), rel)
    json.dump(allk, open(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)), 'keys.json'), 'w'), ensure_ascii=False, indent=0)
    import collections; print(len(allk), collections.Counter(allk.values()).most_common(8))
