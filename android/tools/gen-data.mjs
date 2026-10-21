// Regenerates data/Clubs.kt and data/Tr.kt from the web app's assets/clubs.js, fa.js and extras.js.
// Usage (from the repo root): node android/tools/gen-data.mjs
import fs from 'fs';
import os from 'os';
import path from 'path';
const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../..');
const out = root + '/android/app/src/main/java/app/goallive/live/data';
fs.mkdirSync(out, { recursive: true });
const ks = (s) => JSON.stringify(String(s ?? '')).replace(/\$/g, '\\$');
// clubs.js is a plain ES module without DOM use
const src = fs.readFileSync(root + '/assets/clubs.js', 'utf8');
const tmp = path.join(os.tmpdir(), 'goallive-clubs.mjs');
fs.writeFileSync(tmp, src);
const { CLUBS } = await import(tmp);
const derbies = [...src.matchAll(/\[\[(\d+),\s*(\d+)\],\s*'([^']+)'\]/g)].map((m) => [m[1], m[2], m[3]]);
let k = `// Generated from assets/clubs.js — themes and short histories of big clubs, keyed by FotMob team id.
package app.goallive.live.data

class Club(
    val fa: String, val nick: String, val founded: Int, val city: String, val stadium: String,
    val c1: Long, val c2: Long, val ink: Long, val accent: Long?, val motto: String, val story: String,
    val legends: List<String>, val years: List<Pair<Int, String>>,
)

object Clubs {
    fun get(id: Any?): Club? = id?.toString()?.toIntOrNull()?.let { MAP[it] }

    fun derby(a: Any?, b: Any?): String? {
        val x = a?.toString()?.toIntOrNull() ?: return null
        val y = b?.toString()?.toIntOrNull() ?: return null
        return DERBIES.firstOrNull { (p, q, _) -> (p == x && q == y) || (p == y && q == x) }?.third
    }

    private val DERBIES = listOf(
${derbies.map(([a, b, n]) => `        Triple(${a}, ${b}, ${ks(n)}),`).join('\n')}
    )

    private val MAP: Map<Int, Club> = mapOf(
`;
const col = (h) => { let x = h.replace('#', ''); if (x.length === 3) x = x.split('').map((c) => c + c).join(''); return '0xFF' + x.toUpperCase(); };
for (const [id, c] of Object.entries(CLUBS)) {
  k += `        ${id} to Club(
            ${ks(c.fa)}, ${ks(c.nick)}, ${c.founded || 0}, ${ks(c.city)}, ${ks(c.stadium)},
            ${col(c.c[0])}, ${col(c.c[1])}, ${col(c.ink || '#fff')}, ${c.accent ? col(c.accent) : 'null'}, ${ks(c.motto)},
            ${ks(c.story)},
            listOf(${(c.legends || []).map(ks).join(', ')}),
            listOf(${(c.years || []).map(([y, t]) => `${y} to ${ks(t)}`).join(', ')}),
        ),
`;
}
k += '    )\n}\n';
fs.writeFileSync(out + '/Clubs.kt', k);
// fa.js: the T object literal
const fa = fs.readFileSync(root + '/assets/fa.js', 'utf8');
const T = eval('(' + fa.slice(fa.indexOf('{'), fa.indexOf('};') + 1) + ')');
let t = `// Generated from assets/fa.js — English FotMob titles to Persian.
package app.goallive.live.data

fun tr(s: String?): String = if (s == null) "" else T[s] ?: T[s.trim()] ?: s

private val T: Map<String, String> = hashMapOf(
${Object.entries(T).map(([a, b]) => `    ${ks(a)} to ${ks(b)},`).join('\n')}
)
`;
// extras.js: Persian names of teams that are not in CLUBS (used to search Aparat for highlights)
const ex = fs.readFileSync(root + '/assets/extras.js', 'utf8');
const fs0 = ex.indexOf('const FA_NAMES = {');
const N = eval('(' + ex.slice(ex.indexOf('{', fs0), ex.indexOf('};', fs0) + 1) + ')');
t += `
/** Persian team names for the Aparat highlight search. */
fun faTeamName(id: Int, name: String): String? = Clubs.get(id)?.fa ?: NAMES[name]

private val NAMES: Map<String, String> = hashMapOf(
${Object.entries(N).map(([a, b]) => `    ${ks(a)} to ${ks(b)},`).join('\n')}
)
`;
fs.writeFileSync(out + '/Tr.kt', t);
console.log(Object.keys(CLUBS).length, derbies.length, Object.keys(T).length);
