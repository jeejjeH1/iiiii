#!/usr/bin/env python3
"""Builds worker/src/fa.json, the Persian names the API's ?lang=fa uses, from the Android app's tables (one source of truth).
Run from the repo root: python3 worker/tools/fa.py"""
import json, re

KT = 'android/app/src/main/java/app/goallive/live/data/'
pair = re.compile(r'"((?:[^"\\]|\\.)+)"\s+to\s+"((?:[^"\\]|\\.)+)"')

def pairs(text):
    return {a: b for a, b in pair.findall(text)}

tr = open(KT + 'Tr.kt', encoding='utf-8').read()
cut = tr.index('fun faTeamName')
words = pairs(tr[:cut])
teams = pairs(open(KT + 'TeamsFa.kt', encoding='utf-8').read())
teams.update(pairs(tr[cut:]))
ids = {k: v for k, v in re.findall(r"^\s*(\d+): \{ fa: '([^']+)'", open('assets/clubs.js', encoding='utf-8').read(), re.M)}

json.dump({'ids': ids, 'teams': teams, 'words': words}, open('worker/src/fa.json', 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print(len(ids), 'clubs,', len(teams), 'team names,', len(words), 'words')
