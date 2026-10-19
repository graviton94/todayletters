#!/usr/bin/env python3
"""시리즈 규격 v1 검사 (시안 depth.html 3): 시리즈마다 빠진 칸 · 비율 · 세 언어를 센다. 새 시리즈는 이걸 통과해야 들어간다.

    python3 scripts/spec_check.py                 # 모든 시리즈 (data/*/series.json)
    python3 scripts/spec_check.py vincent -v      # 한 시리즈, 어긋난 곳을 모두 적기
    python3 scripts/spec_check.py --drafts        # 챕터마다 .draft.json 이 있으면 그것을 (검토 중인 데이터)

시리즈마다 규칙별 결과 (통과 · 실패 수 · 처음 몇 곳) 를 찍고, 하나라도 실패하면 0 이 아닌 값으로 끝난다.

규격 v1 (편지는 챕터 + 맺음 편지, 프롤로그는 messages · 낱말 카드만):
  messages   메시지마다 원어 · en · ko 가 비지 않음
  words      편지마다 오늘의 낱말 5–8개 (daily.json · daily_en · daily_ko, 있는 것마다), 사전에 있는 열쇠,
             시리즈 전체에서 A1–A2 가 60% 이상 (원어 자료 기준; 영어 · 한국어 자료는 따로 줄)
             (앱은 daily.json 의 "words" 를 그대로 카드로 쓴다 → 쉬운 낱말 비중은 데이터에서 맞춘다. 이 검사가 그 규칙)
  pron       카드 발음 칸: 원어 사전 ipa · 영어 사전 ipa (또는 ipa_en) · 한국어 사전 pron (소리 나는 대로) + rom (로마자)
  phrases    편지마다 표현 2개, 표현마다 t · ko · en · note_ko · note_en
  check      편지마다 1–2문제, q 세 언어 · 보기 4개 (세 언어) · answer 0–3 · evidence (번호 하나 또는 이어진 번호 1–2개, 메시지 안)
  talk       scene (ko · en) · 줄 3–4개 (who = them | me, 세 언어, me 줄 하나 이상) · words · grammar (장의 문법 id)
  replies    편지마다 턴 1–3 (세 언어 · quote · grammar · focus · koLow · koHigh)
  grammar    장마다 3개, 문법마다 원어 · en · ko 칸 (name · hint 의 ko · en)
  basics     기초 낱말 덱 basics[_en|_ko].json (있는 것만, core Basics): lang · top (겹치지 않는 표제어) · items
             (id = sha1("<작품>[_말]/<낱말>") 앞 8자, 겹치지 않음, 낱말 (여러 낱말이면 "k") 은 top 안, pos, 뜻은 덱의 말을 뺀 ko · en,
             ex 는 t · 같은 언어 번역 · from = 그 작품의 편지 id 또는 null)
  talk_audio (알림만) talk.audio 가 있는데 소리 파일이 assets · remote 에 없음
             ("audio" 는 "t" 또는 {"*": "t", "ko": "tk"}: 언어마다 앞부분, 없는 언어는 "*" — core Talk.audioName)
"""
import glob, hashlib, json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EASY = {"A1", "A2"}
EASY_SHARE = 0.60
WORDS_MIN, WORDS_MAX = 5, 8
PHRASES = 2
CHECK_MIN, CHECK_MAX = 1, 2
OPTIONS = 4
TALK_MIN, TALK_MAX = 3, 4
TURNS_MIN, TURNS_MAX = 1, 3
GRAMMAR_PER_CHAPTER = 3
SHOW = 6
BASICS_TOP = 300


def basic_id(name, w):
    """기초 낱말 고정 이름: sha1("<작품>[_말]/<낱말>") 앞 8자 (core Basics.id)."""
    return hashlib.sha1(f"{name}/{w}".encode("utf-8")).hexdigest()[:8]


class Report:
    def __init__(self):
        self.rules = {}   # name → [checked, failures[]]
        self.notes = []

    def rule(self, name):
        return self.rules.setdefault(name, [0, []])

    def ok(self, name):
        self.rule(name)[0] += 1

    def bad(self, name, where):
        r = self.rule(name); r[0] += 1; r[1].append(where)

    def check(self, name, cond, where):
        self.ok(name) if cond else self.bad(name, where)

    @property
    def failed(self):
        return any(f for _, f in self.rules.values())


def load(path, drafts):
    draft = path.replace(".json", ".draft.json")
    if drafts and os.path.exists(draft):
        return json.load(open(draft, encoding="utf-8"))
    if os.path.exists(path):
        return json.load(open(path, encoding="utf-8"))
    if os.path.exists(draft):
        return json.load(open(draft, encoding="utf-8"))
    return None


def has(o, langs):
    return isinstance(o, dict) and all(isinstance(o.get(l), str) and o[l].strip() for l in langs)


def lexicon(folder, name):
    p = os.path.join(folder, name)
    if not os.path.exists(p):
        return None
    lx = json.load(open(p, encoding="utf-8"))
    f = lx["fields"]
    out = {}
    for row in lx["entries"]:
        e = dict(zip(f, row))
        out[e.get("id") or e["lemma"]] = e
    return out


def evidence(ev, n):
    """근거: 번호 하나, 또는 이어진 번호 1–2개. 모두 메시지 안."""
    if isinstance(ev, bool):
        return False
    if isinstance(ev, int):
        ev = [ev]
    if not isinstance(ev, list) or not 1 <= len(ev) <= 2 or not all(isinstance(i, int) and not isinstance(i, bool) for i in ev):
        return False
    if any(i < 0 or i >= n for i in ev):
        return False
    return len(ev) == 1 or ev[1] == ev[0] + 1


def check_series(sid, drafts, verbose):
    folder = os.path.join(ROOT, "data", sid)
    meta = json.load(open(os.path.join(folder, "series.json"), encoding="utf-8"))
    orig = meta["original"].lower()
    L3 = [orig, "en", "ko"]
    rep = Report()

    # 편지 모으기: (장 id, 편지, 장 데이터)
    chapters = []
    for ch in meta["chapters"]:
        data = load(os.path.join(folder, ch["file"]), drafts)
        if data is None:
            rep.bad("files", f"{ch['file']} 없음"); continue
        rep.ok("files")
        chapters.append((ch["id"], data))
    epi = load(os.path.join(folder, meta["outro"]["file"]), drafts) if "outro" in meta and "file" in meta["outro"] else None
    pro = load(os.path.join(folder, meta["prologue"]["file"]), drafts) if "prologue" in meta and "file" in meta["prologue"] else None
    grammar_ids = {g.get("id") for _, d in chapters for g in d.get("grammar", [])} | {g.get("id") for g in (epi or {}).get("grammar", [])}

    lessons = [(cid, l) for cid, d in chapters for l in d["letters"]] + [("E", l) for l in (epi or {}).get("letters", [])]
    every = lessons + [("P", l) for l in (pro or {}).get("letters", [])]

    # messages
    for cid, l in every:
        for i, m in enumerate(l["messages"]):
            rep.check("messages", has(m, L3), f"{l['id']} 메시지 {i}: 세 언어 중 빈 칸")

    # grammar: 장마다 3개, 세 언어 칸
    for cid, d in chapters:
        gs = d.get("grammar", [])
        rep.check("grammar", len(gs) == GRAMMAR_PER_CHAPTER, f"장 {cid}: 문법 {len(gs)}개 (규격 {GRAMMAR_PER_CHAPTER})")
        for g in gs:
            for lang in L3:
                o = g.get(lang) or {}
                rep.check("grammar", has(o.get("name"), ["ko", "en"]) and has(o.get("hint"), ["ko", "en"]),
                          f"장 {cid} 문법 {g.get('id')}: {lang} 이름 · 힌트 (ko · en)")

    # 낱말 카드 (daily) · 표현 · 발음
    decks = [("", orig, "lexicon.json", "daily.json"), ("en", "en", "lexicon_en.json", "daily_en.json"), ("ko", "ko", "lexicon_ko.json", "daily_ko.json")]
    if orig == "en":
        decks = [d for d in decks if d[0] != "en"]
    for deck, lang, lexname, dailyname in decks:
        lex = lexicon(folder, lexname)
        dp = os.path.join(folder, dailyname)
        if lex is None or not os.path.exists(dp):
            rep.bad("words", f"{dailyname} · {lexname} 없음"); continue
        daily = json.load(open(dp, encoding="utf-8"))["letters"]
        tag = f"[{lang}]"
        easy = total = 0
        for cid, l in every:
            d = daily.get(l["id"])
            if d is None:
                rep.bad("words", f"{tag} {l['id']}: daily 에 없음"); continue
            ws = d.get("words", [])
            if cid != "P":   # 프롤로그 (도슨트 한 줄) 는 낱말 수를 세지 않는다
                rep.check("words", WORDS_MIN <= len(ws) <= WORDS_MAX, f"{tag} {l['id']}: 낱말 {len(ws)}개 (규격 {WORDS_MIN}–{WORDS_MAX})")
            for k in ws:
                e = lex.get(k)
                if e is None:
                    rep.bad("words", f"{tag} {l['id']}: '{k}' 가 사전에 없음"); continue
                total += 1
                easy += e.get("level") in EASY
                if lang == "ko":
                    rep.check("pron", bool(e.get("pron") or e.get("say_ko")) and bool(e.get("rom") or e.get("rom_ko")),
                              f"{tag} '{k}': pron (소리 나는 대로) · rom (로마자) 없음")
                else:
                    rep.check("pron", bool(e.get("ipa") or e.get("ipa_en")), f"{tag} '{k}': ipa 없음")
            if cid != "P":
                ps = d.get("phrases", [])
                rep.check("phrases", len(ps) == PHRASES, f"{tag} {l['id']}: 표현 {len(ps)}개 (규격 {PHRASES})")
                for p in ps:
                    rep.check("phrases", all(isinstance(p.get(x), str) and p[x].strip() for x in ("t", "ko", "en", "note_ko", "note_en")),
                              f"{tag} {l['id']}: 표현 '{p.get('t', '')}' 의 t · ko · en · note_ko · note_en 중 빈 칸")
        share = easy / total if total else 0.0
        rep.check("words_easy", share >= EASY_SHARE, f"{tag} A1–A2 {easy}/{total} = {share:.0%} (규격 {EASY_SHARE:.0%} 이상)")
        rep.notes.append(f"{tag} 카드 낱말 {total}개 · A1–A2 {share:.0%}")

    # 이해 확인 · 오늘의 회화 · 답장 (챕터 + 맺음 편지)
    talk_audio_missing = 0
    for cid, l in lessons:
        lid = l["id"]; n = len(l["messages"])
        cs = l.get("check")
        if not isinstance(cs, list) or not CHECK_MIN <= len(cs) <= CHECK_MAX:
            rep.bad("check", f"{lid}: 문제 {len(cs) if isinstance(cs, list) else 0}개 (규격 {CHECK_MIN}–{CHECK_MAX})")
        else:
            rep.ok("check")
            for qi, c in enumerate(cs):
                w = f"{lid} 문제 {qi + 1}"
                rep.check("check", has(c.get("q"), L3), f"{w}: 질문 세 언어")
                opts = c.get("options")
                rep.check("check", isinstance(opts, list) and len(opts) == OPTIONS and all(has(o, L3) for o in opts), f"{w}: 보기 {OPTIONS}개 · 세 언어")
                a = c.get("answer")
                rep.check("check", isinstance(a, int) and not isinstance(a, bool) and 0 <= a < OPTIONS, f"{w}: answer {a!r} (0–{OPTIONS - 1})")
                rep.check("check", evidence(c.get("evidence"), n), f"{w}: evidence {c.get('evidence')!r} (메시지 {n}개, 번호 하나 또는 이어진 둘)")
                if "mark" in c:
                    ev = c.get("evidence"); ev = [ev] if isinstance(ev, int) else (ev or [])
                    for lang in L3:
                        mk = (c["mark"] or {}).get(lang)
                        ok = not mk or any(0 <= i < n and mk.lower() in l["messages"][i].get(lang, "").lower() for i in ev)
                        rep.check("check", ok, f"{w}: mark[{lang}] '{mk}' 가 지문에 없음")

        t = l.get("talk")
        if not isinstance(t, dict):
            rep.bad("talk", f"{lid}: talk 없음")
        else:
            lines = t.get("lines") or []
            rep.check("talk", has(t.get("scene"), ["ko", "en"]), f"{lid}: scene (ko · en)")
            rep.check("talk", TALK_MIN <= len(lines) <= TALK_MAX, f"{lid}: 줄 {len(lines)}개 (규격 {TALK_MIN}–{TALK_MAX})")
            rep.check("talk", any(x.get("who") == "me" for x in lines), f"{lid}: 내 줄 (who = me) 없음")
            for li, x in enumerate(lines):
                rep.check("talk", x.get("who") in ("them", "me") and has(x, L3), f"{lid} 줄 {li + 1}: who · 세 언어")
                if x.get("who") == "me" and "focus" in x:
                    for lang, chunks in (x.get("focus") or {}).items():
                        for ck in chunks:
                            rep.check("talk", ck in x.get(lang, ""), f"{lid} 줄 {li + 1}: focus[{lang}] '{ck}' 가 줄에 없음")
            ws = t.get("words")
            rep.check("talk", bool(ws), f"{lid}: words (쓰는 오늘 낱말) 없음")
            rep.check("talk", t.get("grammar") in grammar_ids, f"{lid}: grammar {t.get('grammar')!r} 가 장 문법에 없음")
            a = t.get("audio")
            if isinstance(a, dict):
                rep.check("talk", bool(a.get("*") or a.get("default")) and all(isinstance(v, str) and v for v in a.values()),
                          f"{lid}: audio 가 언어별이면 \"*\" (나머지 언어) 와 비지 않은 이름")
            if a:
                for li in range(len(lines)):
                    for lang in L3:
                        stem = (a.get(lang) or a.get("*") or a.get("default")) if isinstance(a, dict) else a
                        rel = f"audio/{sid}/{cid}/{lid}/{stem}{li + 1}_{lang}.m4a"
                        if not (os.path.exists(os.path.join(ROOT, "android", "app", "src", "main", "assets", rel)) or os.path.exists(os.path.join(ROOT, "remote", rel))):
                            talk_audio_missing += 1

        rs = l.get("replies")
        if not isinstance(rs, list) or not TURNS_MIN <= len(rs) <= TURNS_MAX:
            rep.bad("replies", f"{lid}: 턴 {len(rs) if isinstance(rs, list) else 0}개 (규격 {TURNS_MIN}–{TURNS_MAX})")
        else:
            rep.ok("replies")
            for ti, r in enumerate(rs):
                w = f"{lid} 턴 {ti + 1}"
                rep.check("replies", has(r, L3) and isinstance(r.get("quote"), int) and r.get("grammar") in grammar_ids
                          and isinstance(r.get("focus"), dict) and bool(r.get("koLow")) and bool(r.get("koHigh")),
                          f"{w}: 세 언어 · quote · grammar · focus · koLow · koHigh")
    if talk_audio_missing:
        rep.notes.append(f"talk 소리 파일 {talk_audio_missing}개 없음 (알림만)")

    # basics: 기초 낱말 덱 (있는 것만)
    letter_ids = {l["id"] for _, l in every}
    for lang in L3:
        deck = "" if lang == orig else lang
        f = os.path.join(folder, f"basics{'_' + deck if deck else ''}.json")
        if not os.path.exists(f):
            continue
        name = os.path.basename(f)
        try:
            b = json.load(open(f, encoding="utf-8"))
        except ValueError as e:
            rep.bad("basics", f"{name}: JSON ({e})"); continue
        top = b.get("top")
        rep.check("basics", b.get("lang") == lang, f"{name}: lang {b.get('lang')!r} (덱의 말 {lang})")
        top_ok = isinstance(top, list) and top and all(isinstance(x, str) and x.strip() for x in top) and len(set(top)) == len(top)
        rep.check("basics", top_ok, f"{name}: top 은 겹치지 않는 표제어 목록")
        if top_ok and len(top) != BASICS_TOP:
            rep.notes.append(f"{name}: top {len(top)}개 (보통 {BASICS_TOP})")
        tops = {x.lower() for x in top} if top_ok else set()
        need = [x for x in ("ko", "en") if x != lang]
        seen = set()
        for i, it in enumerate(b.get("items") or []):
            w = it.get("w") if isinstance(it, dict) else None
            where = f"{name} #{i} {w!r}"
            if not isinstance(w, str) or not w.strip():
                rep.bad("basics", f"{where}: w 없음"); continue
            want = basic_id(sid + (f"_{deck}" if deck else ""), w)
            rep.check("basics", it.get("id") == want, f"{where}: id {it.get('id')!r} ≠ {want}")
            rep.check("basics", it.get("id") not in seen, f"{where}: id 겹침")
            seen.add(it.get("id"))
            # 여러 낱말 표제어 (est-ce que) 는 "k" 가 top 의 표제어 (est-ce), 없으면 k = w
            k = it.get("k") or w
            rep.check("basics", isinstance(k, str) and k.lower() in tops, f"{where}: top 에 없는 낱말 (k {k!r})")
            rep.check("basics", isinstance(it.get("pos"), str) and it["pos"].strip(), f"{where}: pos 없음")
            rep.check("basics", has(it.get("mean"), need) and lang not in it["mean"], f"{where}: mean 은 {' · '.join(need)} (덱의 말 {lang} 은 빼기)")
            ex = it.get("ex")
            if ex is not None:
                ok = has(ex, ["t"] + need) and lang not in ex and (ex.get("from") is None or ex.get("from") in letter_ids)
                rep.check("basics", ok, f"{where}: ex 는 t · {' · '.join(need)} · from (편지 id 또는 null)")

    # 보고
    print(f"== {sid} ({orig}) · 편지 {len(lessons)}통 (+ 프롤로그 {len(every) - len(lessons)})")
    for name in ("files", "messages", "grammar", "words", "words_easy", "pron", "phrases", "check", "talk", "replies", "basics"):
        if name not in rep.rules:
            continue
        n, fails = rep.rules[name]
        mark = "ok  " if not fails else "FAIL"
        print(f"  {mark} {name:<10} {n - len(fails)}/{n}")
        for f in (fails if verbose else fails[:SHOW]):
            print(f"         - {f}")
        if fails and not verbose and len(fails) > SHOW:
            print(f"         … {len(fails) - SHOW}곳 더 (-v)")
    for note in rep.notes:
        print(f"  ·    {note}")
    return not rep.failed


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    verbose = "-v" in sys.argv or "--verbose" in sys.argv
    drafts = "--drafts" in sys.argv
    ids = args or sorted(os.path.basename(os.path.dirname(p)) for p in glob.glob(os.path.join(ROOT, "data", "*", "series.json")))
    ok = True
    for sid in ids:
        ok &= check_series(sid, drafts, verbose)
    print("규격 v1: 통과" if ok else "규격 v1: 실패")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
