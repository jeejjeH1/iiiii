// Themed club history card (shared by match and team pages).
import { esc, fa, num, teamImgL } from './core.js';
import { patternStyle } from './clubs.js';

export function storyHtml(id, c, trophies) {
  const dark = ['#111', '#000', '#0b1b3a'].includes(c.ink);
  const age = new Date().getFullYear() - c.founded;
  return `<section class="story rise ${dark ? 'inkdark' : ''}" style="--c1:${c.c[0]};--c2:${c.accent || c.c[1]};color:${dark ? c.ink : '#fff'}">
    <div class="bg"></div><div class="pat" style="${patternStyle(c.pattern)}"></div>
    <div class="story-h"><img src="${teamImgL(id)}" alt="" onerror="this.style.visibility='hidden'"><div><h2>${esc(c.fa)}</h2><div class="nick">${esc(c.nick)}</div></div></div>
    <div class="motto">«${esc(c.motto)}»</div>
    <div class="facts"><div><b data-count="${c.founded}" data-plain>${fa(c.founded)}</b><small>سال تأسیس</small></div><div><b data-count="${age}">${num(age)}</b><small>سال تاریخ</small></div><div><b style="font-size:14px;line-height:1.6">${esc(c.city)}</b><small>شهر</small></div></div>
    <p>${esc(c.story)}</p>
    <div class="kv-h" style="color:inherit;opacity:.8">ورزشگاه</div><p style="margin-top:0">🏟️ ${esc(c.stadium)}</p>
    ${trophies?.length ? `<div class="kv-h" style="color:inherit;opacity:.8">افتخارات</div><div class="trophies">${trophies.map((t) => `<div><b data-count="${t.won}">${num(t.won)}</b><small>${esc(t.name)}</small></div>`).join('')}</div>` : ''}
    <div class="kv-h" style="color:inherit;opacity:.8">لحظه‌های تاریخی</div>
    <div class="timeline">${c.years.map(([y, t]) => `<div><b>${fa(y)}</b>${esc(t)}</div>`).join('')}</div>
    <div class="kv-h" style="color:inherit;opacity:.8">اسطوره‌ها</div>
    <div class="legends">${c.legends.map((l) => `<span>${esc(l)}</span>`).join('')}</div><div style="height:18px"></div>
  </section>`;
}
