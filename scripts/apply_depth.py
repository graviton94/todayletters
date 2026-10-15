#!/usr/bin/env python3
"""편지 깊이 넣기: 이해 확인 · 오늘의 회화 · 카드 발음 · 카드 낱말 바꾸기 초안을 data/<작품>/ 에 넣는다.

    python3 scripts/apply_depth.py <폴더>            # 검사하고 넣기
    python3 scripts/apply_depth.py <폴더> --check    # 검사만 (아무것도 쓰지 않음)
    python3 scripts/apply_depth.py <폴더> --root <data 폴더>   # 다른 data 폴더에 (시험용)

몇 번을 돌려도 결과가 같다 (같은 초안이면 두 번째부터는 바뀌는 것이 없다).

입력 폴더의 파일 (작품 <s> = vincent · mozart, 있는 것만):
  check_<s>.json   {"<파일 이름>/<편지 id>": [{q, options[4], answer, evidence, mark?}]}   예: "chapter1/c1-l1", "prologue/p1", "epilogue/e1"
                   → 그 편지의 "check" 를 통째로 바꾼다 (Library.checks).
  talk_<s>.json    {"<파일 이름>/<편지 id>": {scene, lines[{who, <원어>, en, ko, focus?, wrong?}], words, grammar}}
                   → 그 편지의 "talk" 를 통째로 바꾼다 (Library.talk).
  pron_<s>.json    {"<사전 열쇠 id||lemma>": {"ipaEn": "..."} | {"pron": "[가치]", "romKo": "...", "pronNote"?: {ko, en} | null}}
                   ipaEn → lexicon_en.json 의 "ipa" 칸을 덮어쓴다.
                   pron → lexicon_ko.json 의 "fields" 끝에 "pron" · "rom" · "pron_note" 칸을 (없으면) 더하고 채운다.
  cards_<s>.json · cards_<s>_en.json · cards_<s>_ko.json
                   {"<편지 id>": {"remove": [...], "add": [...]}} → daily.json · daily_en.json · daily_ko.json 의 "words"
                   (remove 를 빼고, 아직 없는 add 를 뒤에 붙인다).
검사 (하나라도 어긋나면 아무 파일도 쓰지 않고 0 이 아닌 값으로 끝난다):
  - 파일 · 편지 id · 사전 열쇠가 있는가, 더하는 카드 낱말이 그 사전에 있는가
  - check: 질문 · 보기 4개가 세 언어, answer 0–3, evidence 가 메시지 안 (번호 하나 또는 이어진 둘), mark 가 지문에 글자 그대로
  - talk: scene (ko · en), 줄 3–4개 · 세 언어 · who, focus 가 그 줄에 글자 그대로, wrong 이 focus 와 같은 수 · 정답과 다름
"""
import argparse, json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KO_FIELDS = ["pron", "rom", "pron_note"]


def load(path):
    text = open(path, encoding="utf-8").read()
    return json.loads(text), ("compact" if "\n" not in text.rstrip("\n") else "indent"), text.endswith("\n")


def dump(data, style, newline):
    s = json.dumps(data, ensure_ascii=False, separators=(",", ":")) if style == "compact" else json.dumps(data, ensure_ascii=False, indent=1)
    return s + ("\n" if newline else "")


def has(o, langs):
    return isinstance(o, dict) and all(isinstance(o.get(l), str) and o[l].strip() for l in langs)


def evidence_ok(ev, n):
    if isinstance(ev, bool):
        return False
    ev = [ev] if isinstance(ev, int) else ev
    if not isinstance(ev, list) or not 1 <= len(ev) <= 2 or not all(isinstance(i, int) and not isinstance(i, bool) for i in ev):
        return False
    return all(0 <= i < n for i in ev) and (len(ev) == 1 or ev[1] == ev[0] + 1)


class Files:
    """data 파일 읽기 · 바꾼 것만 쓰기 (원래 꼴 그대로: 장 파일 indent=1, 사전 · daily 한 줄)."""

    def __init__(self):
        self.cache = {}

    def get(self, path):
        if path not in self.cache:
            data, style, nl = load(path)
            self.cache[path] = [data, style, nl, dump(data, style, nl)]
        return self.cache[path][0]

    def changed(self):
        return [p for p, (d, st, nl, orig) in self.cache.items() if dump(d, st, nl) != orig]

    def write(self):
        for p in self.changed():
            d, st, nl, _ = self.cache[p]
            open(p, "w", encoding="utf-8").write(dump(d, st, nl))


def lex_index(lx):
    f = lx["fields"]
    iid, ilem = f.index("id"), f.index("lemma")
    out = {}
    for row in lx["entries"]:
        key = (row[iid] if len(row) > iid and row[iid] else None) or row[ilem]
        out[key] = row
    return out


def set_cell(row, i, value):
    while len(row) <= i:
        row.append(None)
    row[i] = value
    while row and row[-1] is None:   # 뒤쪽 null 은 생략
        row.pop()


def main():
    ap = argparse.ArgumentParser(description="이해 확인 · 오늘의 회화 · 카드 발음 · 카드 낱말 넣기")
    ap.add_argument("dir", help="초안 폴더")
    ap.add_argument("--check", action="store_true", help="검사만")
    ap.add_argument("--root", default=os.path.join(ROOT, "data"), help="data 폴더 (기본: 저장소의 data)")
    a = ap.parse_args()

    errors, stats = [], {}
    files = Files()

    def src(name):
        p = os.path.join(a.dir, name)
        return json.load(open(p, encoding="utf-8")) if os.path.exists(p) else None

    series_ids = sorted({n.split("_")[1].split(".")[0] for n in os.listdir(a.dir)
                         if n.split("_")[0] in ("check", "talk", "pron", "cards") and n.endswith(".json") and n != "cards_summary.json"})
    for sid in series_ids:
        folder = os.path.join(a.root, sid)
        if not os.path.exists(os.path.join(folder, "series.json")):
            errors.append(f"{sid}: data/{sid}/series.json 없음"); continue
        orig = json.load(open(os.path.join(folder, "series.json"), encoding="utf-8"))["original"].lower()
        L3 = [orig, "en", "ko"]
        st = stats.setdefault(sid, {})

        def letter(key):
            stem, _, lid = key.partition("/")
            p = os.path.join(folder, stem + ".json")
            if not lid or not os.path.exists(p):
                errors.append(f"{sid} {key}: 파일 {stem}.json 없음"); return None
            for l in files.get(p)["letters"]:
                if l["id"] == lid:
                    return l
            errors.append(f"{sid} {key}: 편지 id 없음"); return None

        # ── 이해 확인 ──
        for key, cs in (src(f"check_{sid}.json") or {}).items():
            l = letter(key)
            if l is None: continue
            n = len(l["messages"])
            for qi, c in enumerate(cs):
                w = f"{sid} {key} 문제 {qi + 1}"
                if not has(c.get("q"), L3): errors.append(f"{w}: 질문 세 언어")
                opts = c.get("options")
                if not (isinstance(opts, list) and len(opts) == 4 and all(has(o, L3) for o in opts)): errors.append(f"{w}: 보기 4개 · 세 언어")
                if not (isinstance(c.get("answer"), int) and 0 <= c["answer"] < 4): errors.append(f"{w}: answer {c.get('answer')!r}")
                if not evidence_ok(c.get("evidence"), n): errors.append(f"{w}: evidence {c.get('evidence')!r} (메시지 {n}개)")
                elif "mark" in c:
                    ev = c["evidence"]; ev = [ev] if isinstance(ev, int) else ev
                    for lang, mk in (c["mark"] or {}).items():
                        if mk and not any(mk in l["messages"][i].get(lang, "") for i in ev):
                            errors.append(f"{w}: mark[{lang}] '{mk}' 가 지문에 없음")
            if l.get("check") != cs:
                st["check"] = st.get("check", 0) + 1
            l["check"] = cs
            st["check_q"] = st.get("check_q", 0) + len(cs)
            st["mark"] = st.get("mark", 0) + sum(1 for c in cs if c.get("mark"))

        # ── 오늘의 회화 ──
        for key, t in (src(f"talk_{sid}.json") or {}).items():
            l = letter(key)
            if l is None: continue
            w = f"{sid} {key} talk"
            if not has(t.get("scene"), ["ko", "en"]): errors.append(f"{w}: scene (ko · en)")
            lines = t.get("lines") or []
            if not 3 <= len(lines) <= 4: errors.append(f"{w}: 줄 {len(lines)}개")
            if not any(x.get("who") == "me" for x in lines): errors.append(f"{w}: 내 줄 없음")
            for li, x in enumerate(lines):
                if x.get("who") not in ("them", "me") or not has(x, L3): errors.append(f"{w} 줄 {li + 1}: who · 세 언어")
                for lang, chunks in (x.get("focus") or {}).items():
                    for ck in chunks:
                        if ck not in x.get(lang, ""): errors.append(f"{w} 줄 {li + 1}: focus[{lang}] '{ck}' 가 줄에 없음")
                    wr = (x.get("wrong") or {}).get(lang)
                    if wr is not None:
                        if len(wr) != len(chunks): errors.append(f"{w} 줄 {li + 1}: wrong[{lang}] 수가 focus 와 다름")
                        for ck, pair in zip(chunks, wr):
                            if any((not p.strip()) or p == ck for p in pair): errors.append(f"{w} 줄 {li + 1}: wrong[{lang}] {pair} (빈 칸이거나 정답과 같음)")
            # 줄이 그대로면 이미 만든 소리(audio)를 잇는다. 줄이 바뀌면 소리 없음: 새 소리는 새 이름으로 (R2 덮어쓰기 금지, docs/release.md 5)
            old = l.get("talk") or {}
            if old.get("audio") and old.get("lines") == t.get("lines") and "audio" not in t:
                t = {**t, "audio": old["audio"]}
            if l.get("talk") != t:
                st["talk"] = st.get("talk", 0) + 1
            l["talk"] = t
            st["talk_lines"] = st.get("talk_lines", 0) + len(lines)

        # ── 발음 ──
        pron = src(f"pron_{sid}.json") or {}
        if pron:
            lx_en = files.get(os.path.join(folder, "lexicon_en.json"))
            lx_ko = files.get(os.path.join(folder, "lexicon_ko.json"))
            en_rows, ko_rows = lex_index(lx_en), lex_index(lx_ko)
            i_ipa = lx_en["fields"].index("ipa")
            for fname in KO_FIELDS:
                if fname not in lx_ko["fields"]:
                    lx_ko["fields"].append(fname)
            ko_i = {f: lx_ko["fields"].index(f) for f in KO_FIELDS}
            for key, v in pron.items():
                if "ipaEn" in v:
                    row = en_rows.get(key)
                    if row is None: errors.append(f"{sid} pron '{key}': lexicon_en 에 없음"); continue
                    if (row[i_ipa] if len(row) > i_ipa else None) != v["ipaEn"]:
                        st["ipa_en"] = st.get("ipa_en", 0) + 1
                    set_cell(row, i_ipa, v["ipaEn"])
                    st["ipa_en_rows"] = st.get("ipa_en_rows", 0) + 1
                if "pron" in v:
                    row = ko_rows.get(key)
                    if row is None: errors.append(f"{sid} pron '{key}': lexicon_ko 에 없음"); continue
                    if not (v["pron"].strip() and v.get("romKo", "").strip()): errors.append(f"{sid} pron '{key}': pron · romKo 빈 칸")
                    before = list(row)
                    note = v.get("pronNote")
                    if note is not None and not has(note, ["ko", "en"]): errors.append(f"{sid} pron '{key}': pronNote ko · en")
                    # 뒤쪽 null 을 지우기 전에 앞 칸부터 채운다 (rom 이 pron_note 보다 앞)
                    set_cell(row, ko_i["pron"], v["pron"]); set_cell(row, ko_i["rom"], v["romKo"]); set_cell(row, ko_i["pron_note"], note)
                    if row != before:
                        st["ko_pron"] = st.get("ko_pron", 0) + 1
                    st["ko_pron_rows"] = st.get("ko_pron_rows", 0) + 1
                    st["ko_pron_note"] = st.get("ko_pron_note", 0) + (note is not None)

        # ── 카드 낱말 ──
        for suffix, dname, lname in (("", "daily.json", "lexicon.json"), ("_en", "daily_en.json", "lexicon_en.json"), ("_ko", "daily_ko.json", "lexicon_ko.json")):
            cards = src(f"cards_{sid}{suffix}.json")
            if not cards: continue
            daily = files.get(os.path.join(folder, dname))["letters"]
            lex = lex_index(files.get(os.path.join(folder, lname)))
            tag = f"cards{suffix or '_' + orig}"
            for lid, ch in cards.items():
                d = daily.get(lid)
                if d is None: errors.append(f"{sid} {tag} {lid}: {dname} 에 없음"); continue
                for wd in ch.get("add", []):
                    if wd not in lex: errors.append(f"{sid} {tag} {lid}: '{wd}' 가 {lname} 에 없음")
                words = d.get("words", [])
                new = [x for x in words if x not in ch.get("remove", [])]
                new += [x for x in ch.get("add", []) if x not in new]
                if new != words:
                    st[tag] = st.get(tag, 0) + 1
                    st[tag + "_rm"] = st.get(tag + "_rm", 0) + sum(1 for x in words if x not in new)
                    st[tag + "_add"] = st.get(tag + "_add", 0) + sum(1 for x in new if x not in words)
                d["words"] = new

    for sid, st in stats.items():
        print(sid, " · ".join(f"{k} {v}" for k, v in st.items()))
    if errors:
        print(f"\n어긋남 {len(errors)}곳 — 아무것도 쓰지 않음:")
        for e in errors:
            print("  -", e)
        sys.exit(1)
    changed = files.changed()
    if a.check:
        print(f"검사 통과 (--check: 쓸 파일 {len(changed)}개, 쓰지 않음)")
        return
    files.write()
    print(f"쓴 파일 {len(changed)}개" + "".join(f"\n  {os.path.relpath(p, ROOT)}" for p in changed))


if __name__ == "__main__":
    main()
