#!/usr/bin/env python3
"""답장 턴 · 오늘의 문법 넣기: 장마다 쓴 JSON 을 data/<작품>/chapterN.json (맺음 epilogue.json · 프롤로그 prologue.json) 에 넣는다.

    python3 scripts/apply_replies.py <폴더>            # 검사하고 넣기
    python3 scripts/apply_replies.py <폴더> --check    # 검사만 (아무것도 쓰지 않음)
    python3 scripts/apply_replies.py <폴더> --root <data 폴더>   # 다른 data 폴더에 (시험용)

입력 파일 하나 = 한 작품의 한 장 (docs/next.md 2, 형식은 이 앱의 문법 사다리 SPEC):
    {"series": "vincent", "chapter": 3,            # 숫자 · 로마 숫자 "III" · 맺음 "E" · 프롤로그 "P"
     "grammar": [{"id": "III.0", "fr": {"name": {"ko", "en"}, "hint": {"ko", "en"}}, "en": {...}, "ko": {...}}, ...],
     "letters": {"c3-l1": [{"quote": 0, "grammar": "III.0", "fr": "...", "en": "...", "ko": "...(해요체)",
                            "koLow": "...(반말)", "koHigh": "...(높임)", "focus": {"fr": ["ai lu"], "en": [...], "ko": [...]}}, ...]}}

넣을 때: 장 파일에 "grammar" 를 두고 (letters 앞), 편지마다 예전 "reply" (+ "quote") 를 지우고 "replies" 를 둔다.
검사 (하나라도 어긋나면 아무 파일도 쓰지 않고 0 이 아닌 값으로 끝난다):
  - 작품 · 장 파일 · 편지 id 가 있는가
  - 배우는 말이 다 있는가 (원어 · en · ko: 빈센트 fr · en · ko, 모차르트 de · en · ko), koLow · koHigh 가 있는가
  - quote 가 그 편지 메시지 번호 안인가
  - grammar id 가 이 장 또는 앞 장의 문법에 있는가 (맺음은 모든 장, 같은 실행의 다른 입력 파일도 센다)
  - focus 덩어리 (언어마다 1~2개) 가 그 언어 줄에 글자 그대로 있는가
  - 문법 셋: id 가 겹치지 않고, 언어마다 name · hint 의 ko · en 이 있는가
턴 수가 장의 기준 (I~II 1 · III~V 2 · VI~ 3) 과 다르면 알리기만 한다.
"""
import argparse, glob, json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROMAN = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"]


def chapter_key(ch):
    """입력의 장 → (차례, 장 id, 파일 이름). 차례: 프롤로그 0, 장 1~, 맺음 99."""
    if isinstance(ch, str):
        c = ch.strip()
        if c.upper() == "P":
            return 0, "P", "prologue.json"
        if c.upper() == "E":
            return 99, "E", "epilogue.json"
        if c.isdigit():
            ch = int(c)
        elif c.upper() in ROMAN:
            ch = ROMAN.index(c.upper()) + 1
        else:
            raise ValueError(f"장을 모름: {ch!r} (숫자 · 로마 숫자 · \"E\" · \"P\")")
    if isinstance(ch, int) and 1 <= ch <= len(ROMAN):
        return ch, ROMAN[ch - 1], f"chapter{ch}.json"
    raise ValueError(f"장을 모름: {ch!r}")


def turn_target(order):
    if order == 0:
        return None
    if order <= 2:
        return 1
    if order <= 5:
        return 2
    return 3


def load(path):
    text = open(path, encoding="utf-8").read()
    return json.loads(text), text.endswith("\n")


def dump(data, newline):
    return json.dumps(data, ensure_ascii=False, indent=1) + ("\n" if newline else "")


def main():
    ap = argparse.ArgumentParser(description="답장 턴 · 오늘의 문법 넣기")
    ap.add_argument("dir", help="입력 JSON 폴더")
    ap.add_argument("--check", action="store_true", help="검사만")
    ap.add_argument("--root", default=os.path.join(ROOT, "data"), help="data 폴더 (기본: 저장소의 data)")
    a = ap.parse_args()

    errors, notes = [], []
    inputs = sorted(glob.glob(os.path.join(a.dir, "*.json")))
    if not inputs:
        sys.exit(f"입력 파일이 없어요: {a.dir}/*.json")

    jobs = []   # (입력 이름, 작품, 차례, 장 id, 파일 경로, 입력)
    for f in inputs:
        name = os.path.basename(f)
        try:
            doc = json.load(open(f, encoding="utf-8"))
        except Exception as e:
            errors.append(f"{name}: JSON 을 읽을 수 없음 ({e})")
            continue
        series = doc.get("series")
        if not series or not os.path.exists(os.path.join(a.root, str(series), "series.json")):
            errors.append(f"{name}: 작품을 모름 ({series!r})")
            continue
        try:
            order, cid, fname = chapter_key(doc.get("chapter"))
        except ValueError as e:
            errors.append(f"{name}: {e}")
            continue
        path = os.path.join(a.root, series, fname)
        if not os.path.exists(path):
            errors.append(f"{name}: 장 파일이 없음 ({os.path.relpath(path, a.root)})")
            continue
        jobs.append((name, series, order, cid, path, doc))

    # 작품마다 장 차례 → 문법 id (지금 data 의 것, 이번 입력이 있으면 그것으로 바꿔서)
    known = {}
    for series in {j[1] for j in jobs}:
        meta = json.load(open(os.path.join(a.root, series, "series.json"), encoding="utf-8"))
        files = [(0, meta.get("prologue", {}).get("file"))] + [(chapter_key(c["id"])[0], c["file"]) for c in meta.get("chapters", [])] \
            + [(99, meta.get("outro", {}).get("file"))]
        table = {}
        for order, fn in files:
            p = fn and os.path.join(a.root, series, fn)
            if p and os.path.exists(p):
                table[order] = {g.get("id") for g in json.load(open(p, encoding="utf-8")).get("grammar", [])}
        known[series] = table
    for name, series, order, cid, path, doc in jobs:
        known[series][order] = {g.get("id") for g in doc.get("grammar", []) if isinstance(g, dict)}

    seen = set()
    for name, series, order, cid, path, doc in jobs:
        if (series, order) in seen:
            errors.append(f"{name}: {series} {cid} 장이 두 입력 파일에 있음")
        seen.add((series, order))
        meta = json.load(open(os.path.join(a.root, series, "series.json"), encoding="utf-8"))
        langs = [meta["original"], "en", "ko"]
        data, _ = load(path)
        where = f"{name} ({series} {cid})"

        grammar = doc.get("grammar", [])
        if not isinstance(grammar, list):
            errors.append(f"{where}: grammar 는 목록이어야 함")
            grammar = []
        ids = []
        for k, g in enumerate(grammar):
            gid = g.get("id") if isinstance(g, dict) else None
            if not gid:
                errors.append(f"{where}: 문법 {k} 에 id 가 없음")
                continue
            if gid in ids:
                errors.append(f"{where}: 문법 id {gid} 가 겹침")
            ids.append(gid)
            for lang in langs:
                line = g.get(lang)
                if not isinstance(line, dict):
                    errors.append(f"{where}: 문법 {gid} 에 {lang} 칸이 없음")
                    continue
                for part in ("name", "hint"):
                    for ui in ("ko", "en"):
                        if not str((line.get(part) or {}).get(ui, "")).strip():
                            errors.append(f"{where}: 문법 {gid} · {lang} · {part}.{ui} 가 비었음")
        usable = set().union(*[v for o, v in known[series].items() if o <= order or order == 99])

        letters = {l["id"]: l for l in data.get("letters", [])}
        target = turn_target(order)
        replies = doc.get("letters", {})
        if not isinstance(replies, dict) or not replies:
            errors.append(f"{where}: letters 가 비었음")
            replies = {}
        for lid, turns in replies.items():
            at = f"{where} 편지 {lid}"
            letter = letters.get(lid)
            if letter is None:
                errors.append(f"{at}: 장 파일에 이 편지가 없음")
                continue
            if not isinstance(turns, list) or not turns:
                errors.append(f"{at}: 턴이 없음")
                continue
            if target and len(turns) != target:
                notes.append(f"{at}: 턴 {len(turns)}개 (이 장의 기준은 {target}개)")
            n = len(letter.get("messages", []))
            for t, turn in enumerate(turns):
                tt = f"{at} 턴 {t + 1}"
                if not isinstance(turn, dict):
                    errors.append(f"{tt}: 턴은 객체여야 함")
                    continue
                q = turn.get("quote")
                if not isinstance(q, int) or isinstance(q, bool) or not 0 <= q < n:
                    errors.append(f"{tt}: quote {q!r} 가 메시지 번호 0~{n - 1} 밖")
                gid = turn.get("grammar")
                if not gid:
                    errors.append(f"{tt}: grammar 가 없음")
                elif gid not in usable:
                    errors.append(f"{tt}: 문법 {gid} 가 이 장이나 앞 장에 없음")
                for lang in langs:
                    if not str(turn.get(lang, "")).strip():
                        errors.append(f"{tt}: {lang} 줄이 없음")
                for k in ("koLow", "koHigh"):
                    if not str(turn.get(k, "")).strip():
                        errors.append(f"{tt}: {k} 가 없음")
                focus = turn.get("focus")
                if not isinstance(focus, dict):
                    errors.append(f"{tt}: focus 가 없음")
                    continue
                for lang in langs:
                    chunks = focus.get(lang)
                    if not isinstance(chunks, list) or not 1 <= len(chunks) <= 2:
                        errors.append(f"{tt}: focus.{lang} 는 덩어리 1~2개 목록이어야 함")
                        continue
                    line = str(turn.get(lang, ""))
                    for c in chunks:
                        if not isinstance(c, str) or not c.strip() or c not in line:
                            errors.append(f"{tt}: focus.{lang} {c!r} 가 {lang} 줄에 그대로 없음")
        missing = [lid for lid in letters if lid not in replies]
        if missing and replies:
            notes.append(f"{where}: 이 입력에 없는 편지 {len(missing)}통 (그대로 둠): {', '.join(missing)}")

    for n in notes:
        print("알림:", n)
    if errors:
        print(f"\n어긋남 {len(errors)}건 (아무 파일도 바꾸지 않았어요):", file=sys.stderr)
        for e in errors:
            print("  " + e, file=sys.stderr)
        sys.exit(1)

    changed = []
    for name, series, order, cid, path, doc in jobs:
        data, newline = load(path)
        before = dump(data, newline)
        out = {}
        for k, v in data.items():
            if k == "grammar":
                continue
            if k == "letters":
                out["grammar"] = doc.get("grammar", [])
            out[k] = v
        for letter in out.get("letters", []):
            turns = doc.get("letters", {}).get(letter["id"])
            if turns is None:
                continue
            rebuilt = {}
            for k, v in letter.items():
                if k in ("reply", "quote", "replies"):
                    if "replies" not in rebuilt:
                        rebuilt["replies"] = turns
                    continue
                rebuilt[k] = v
            rebuilt.setdefault("replies", turns)
            letter.clear()
            letter.update(rebuilt)
        after = dump(out, newline)
        if after != before:
            changed.append(path)
            if not a.check:
                open(path, "w", encoding="utf-8").write(after)
    verb = "바뀔" if a.check else "바꾼"
    print(f"검사 통과 · 입력 {len(jobs)}개 · {verb} 파일 {len(changed)}개")
    for p in changed:
        print("  " + os.path.relpath(p, a.root))
    if changed and not a.check:
        print("다음: python3 scripts/generate.py && python3 scripts/build_fonts.py --check")


if __name__ == "__main__":
    main()
