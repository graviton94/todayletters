#!/usr/bin/env python3
"""
홈페이지 (graviton94.github.io) 의 ‘맛보기 편지’ 페이지를 만든다: 작품마다 I장 첫 편지 3통.
  /todayletters/letters/                      목록 (한국어)     /todayletters/letters/en/  목록 (영어)
  /todayletters/letters/<작품>/<편지>/          편지 (한국어)     .../<편지>/en/              편지 (영어)
페이지마다 원문 · 번역 · 그림 · 첫 문장 낭독 (있으면) · 출처 · ‘앱에서 이어 읽기’.
그림 · 낭독은 앱 저장소의 파일을 그대로 복사한다. 정적 HTML, 바깥 스크립트 없음.

쓰는 법:  python3 scripts/promo/site_previews.py ../graviton94.github.io
"""
import html
import json
import os
import re
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PLAY = "https://play.google.com/store/apps/details?id=io.github.graviton94.todayletters"
SERIES = ["vincent", "mozart", "napoleon"]
WHO = {  # 제목에 쓰는 이름
    "vincent": {"ko": "빈센트가", "ko_seo": "반 고흐의 편지 번역", "en": "Vincent", "en_seo": "Van Gogh's letter to Theo, translated", "artist": "Vincent van Gogh"},
    "mozart": {"ko": "볼프강이", "ko_seo": "모차르트의 편지 번역", "en": "Wolfgang", "en_seo": "Mozart's letter to {to}, translated", "artist": None},
    "napoleon": {"ko": "나폴레옹이", "ko_seo": "나폴레옹이 조제핀에게 쓴 편지 번역", "en": "Napoleon", "en_seo": "Napoleon's letter to Joséphine, translated", "artist": None},
}
LANG_NAME = {"fr": {"ko": "프랑스어", "en": "French"}, "de": {"ko": "독일어", "en": "German"}}
MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
STYLE = """<style>:root{--moss:#8F6A27}@media (prefers-color-scheme: dark){:root{--moss:#D2A955}}
figure{margin:18px 0 8px}figure img{display:block;max-width:100%;max-height:560px;width:auto;height:auto;margin:0 auto;border-radius:4px}figcaption{font-size:.86rem;color:var(--soft);margin-top:6px}
audio{width:100%;margin:10px 0 4px}.o{margin:18px 0 2px;font-size:1.06rem;font-style:italic}.t{margin:0 0 6px;color:var(--soft)}
.play{display:inline-block;margin:10px 0 2px;padding:8px 18px;border:1.5px solid var(--ink);border-radius:999px;color:var(--ink);text-decoration:none;font-weight:600}
.note{border-left:3px solid var(--moss);padding:2px 0 2px 14px;margin:22px 0}.list{list-style:none;padding:0}.list li{border-top:1px solid var(--line);padding:14px 0;margin:0;display:flex;gap:14px}
.list img{width:72px;height:90px;object-fit:cover;border-radius:3px;flex:none}.list a{font-weight:600}.list p{margin:2px 0}li{overflow-wrap:anywhere}</style>"""


def esc(s):
    return html.escape(s or "", quote=True)


def date_text(d, lang):
    p = [int(x) for x in d.split("-")]
    if lang == "ko":
        return f"{p[0]}년 {p[1]}월" + (f" {p[2]}일" if len(p) > 2 else "")
    return (f"{p[2]} " if len(p) > 2 else "") + f"{MONTHS[p[1] - 1]} {p[0]}"


def linkify(s):
    m = re.search(r"(https?://\S+)$", s or "")
    if not m:
        return esc(s)
    return esc(s[:m.start()]) + f'<a href="{esc(m.group(1))}">{esc(re.sub(r"^https?://(www\.)?", "", m.group(1)).split("/")[0])}</a>'


def find(*cands):
    for c in cands:
        if os.path.isfile(c):
            return c
    return None


def letters():
    out = {}
    for s in SERIES:
        sr = json.load(open(os.path.join(ROOT, f"data/{s}/series.json")))
        ch = json.load(open(os.path.join(ROOT, f"data/{s}/chapter1.json")))
        o = sr["original"]
        items = []
        for l in ch["letters"][:3]:
            p = l["plate"]
            img = p.get("image") or l["id"] + ".webp"
            plate = find(os.path.join(ROOT, "android/app/src/main/assets/plates", img), os.path.join(ROOT, "remote/plates", img))
            audio = find(os.path.join(ROOT, f"android/app/src/main/assets/audio/{s}/I/{l['id']}/m1_{o}.m4a"), os.path.join(ROOT, f"remote/audio/{s}/I/{l['id']}/m1_{o}.m4a"))
            to = l.get("to") or sr["recipient"]
            items.append(dict(series=s, sr=sr, o=o, l=l, plate=plate, audio=audio, to=to, chapter=ch))
        out[s] = items
    return out


def head(title, desc, canon, alt_ko, alt_en, lang, up):
    return f"""<!doctype html>
<html lang="{lang}">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{esc(title)}</title>
<meta name="description" content="{esc(desc)}">
<link rel="canonical" href="https://graviton94.github.io{canon}">
<link rel="alternate" hreflang="ko" href="https://graviton94.github.io{alt_ko}"><link rel="alternate" hreflang="en" href="https://graviton94.github.io{alt_en}">
<meta property="og:title" content="{esc(title)}"><meta property="og:description" content="{esc(desc)}">
<link rel="stylesheet" href="{up}style.css">
{STYLE}</head>
<body><main>
<div class="site"><a href="{up}"><b>당신의 하루 · Your Day</b></a><span><a href="mailto:ruahn49@gmail.com">문의 · Contact</a></span></div>
"""


def foot(up):
    return f"""<footer>문의 · Contact <a href="mailto:ruahn49@gmail.com">ruahn49@gmail.com</a> · <a href="{up}about/">운영 원칙 · About</a></footer>
</main></body>
</html>
"""


def letter_page(it, lang, prev, nxt):
    s, sr, o, l = it["series"], it["sr"], it["o"], it["l"]
    p = l["plate"]
    w = WHO[s]
    base = f"/todayletters/letters/{s}/{l['id']}/"
    up = "../../../../" if lang == "ko" else "../../../../../"
    d = date_text(l["date"], lang)
    to = it["to"][lang]
    if lang == "ko":
        h1 = f"{w['ko']} {to}에게 · {d}"
        title = f"{w['ko_seo']} · {d} · 언어 미술관"
        desc = f"{h1}. {LANG_NAME[o]['ko']} 원문과 한국어 번역, 그림, 원어 낭독. " + l["messages"][0]["ko"]
    else:
        h1 = f"{w['en']} to {to} · {d}"
        title = f"{w['en_seo'].format(to=to)} · {d} · Lingua Museum"
        desc = f"{h1}. The original {LANG_NAME[o]['en']} with an English translation, the painting and a narration. " + l["messages"][0]["en"]
    t = head(title, desc, base + ("" if lang == "ko" else "en/"), base, base + "en/", lang, up)
    lst = "../../" if lang == "ko" else "../../../en/"
    if lang == "ko":
        t += f'<nav><a href="../../../">언어 미술관</a> · <a href="{lst}">맛보기 편지</a> · <a href="en/">English</a></nav>\n'
    else:
        t += f'<nav><a href="../../../../#en">Lingua Museum</a> · <a href="{lst}">Sample letters</a> · <a href="../">한국어</a></nav>\n'
    t += f"<h1>{esc(h1)}</h1>\n"
    series_title = sr["title"][lang]
    place = l.get("place") or ""
    chap = it["chapter"].get(f"title_{lang}") or ""
    t += f'<p class="soft">{esc(series_title)} · {"I장" if lang == "ko" else "Chapter I"} {esc(chap)} · {esc(place)}</p>\n'
    ptitle = p.get(f"title_{lang}") or p.get("title_en")
    porig = p.get(f"title_{o}")
    t += f'<figure><img src="{"" if lang == "ko" else "../"}plate.webp" alt="{esc(ptitle)}" loading="lazy"><figcaption>{esc(ptitle)}{" · " + esc(porig) if porig and porig != ptitle else ""} · {esc(p.get("date", ""))}</figcaption></figure>\n'
    if it["audio"]:
        lab = "첫 문장 낭독 듣기 (" + LANG_NAME[o]["ko"] + ")" if lang == "ko" else "Listen to the first sentence (" + LANG_NAME[o]["en"] + ")"
        t += f'<p class="soft" style="margin:14px 0 0">{lab}</p><audio controls preload="none" src="{"" if lang == "ko" else "../"}m1.m4a"></audio>\n'
    t += f'<h2>{"원문과 번역" if lang == "ko" else "The letter and its translation"}</h2>\n'
    for m in l["messages"]:
        t += f'<p class="o" lang="{o}">{esc(m[o])}</p><p class="t">{esc(m[lang])}</p>\n'
    t += f'<p class="soft">{"편지에서 고른 문장들입니다. 번역은 언어 미술관이 새로 썼습니다." if lang == "ko" else "Selected sentences from the letter. The translation is our own."}</p>\n'
    note = (l.get("note") or {}).get(lang)
    if note:
        t += f'<div class="note"><b>{"큐레이터 노트" if lang == "ko" else "Curator’s note"}</b><br>{esc(note)}</div>\n'
    if lang == "ko":
        t += f'<p>앱에서는 이 편지가 메신저처럼 한 줄씩 도착하고, 낱말마다 뜻이 함께 나와요. 편지 속 인물과 짧게 말해 보는 회화도 있어요. I장은 무료예요.</p>\n<p><a class="play" href="{PLAY}">앱에서 이어 읽기 · Google Play</a></p>\n'
    else:
        t += f'<p>In the app the letter arrives line by line like a message, with the meaning under each word, and you can talk with the writer. Chapter I is free.</p>\n<p><a class="play" href="{PLAY}">Keep reading in the app · Google Play</a></p>\n'
    # 출처
    artist = p.get("credit") or w["artist"] or ""
    if artist == "Unknown artist" and lang == "ko":
        artist = "작자 미상"
    lic = p.get("imageLicense") or ""
    if p.get("exception") == "pd-art" and "PD-Art" not in lic:
        lic += " (PD-Art)"
    t += f'<h2>{"출처" if lang == "ko" else "Credits"}</h2>\n<ul>\n'
    t += f'<li>{"그림" if lang == "ko" else "Picture"}: {esc(artist)}{" · " if artist else ""}{esc(porig or ptitle)} · {esc(p.get("date", ""))} · {esc(p.get("collection", ""))} · {esc(lic)}'
    if p.get("imageSource"):
        t += f' · <a href="{esc(p["imageSource"])}">{"원본" if lang == "ko" else "source"}</a>'
    t += "</li>\n"
    t += f'<li>{"편지 원문" if lang == "ko" else "Letter text"}: {linkify(l.get("ref") or l["messages"][0].get("source", ""))}</li>\n'
    if it["audio"]:
        t += f'<li>{"낭독 음성: Supertonic 3 (Supertone Inc.) 로 만들었습니다 (모델 라이선스 OpenRAIL-M)." if lang == "ko" else "Narration: made with Supertonic 3 (Supertone Inc.), model licence OpenRAIL-M."}</li>\n'
    t += f'<li>{"모든 출처" if lang == "ko" else "All sources"}: <a href="{"../../../" if lang == "ko" else "../../../../#en"}">{"언어 미술관 출처와 라이선스" if lang == "ko" else "Lingua Museum sources and licences"}</a></li>\n</ul>\n'
    nav = []
    if prev:
        nav.append(f'<a href="../../{prev["series"]}/{prev["l"]["id"]}/{"" if lang == "ko" else "en/"}">{"← 앞 편지" if lang == "ko" else "← Previous"}</a>'.replace("../../", "../../" if lang == "ko" else "../../../"))
    if nxt:
        nav.append(f'<a href="../../{nxt["series"]}/{nxt["l"]["id"]}/{"" if lang == "ko" else "en/"}">{"다음 편지 →" if lang == "ko" else "Next →"}</a>'.replace("../../", "../../" if lang == "ko" else "../../../"))
    if nav:
        t += f'<p class="soft">{" · ".join(nav)}</p>\n'
    return t + foot(up)


def index_page(all_items, lang):
    up = "../../" if lang == "ko" else "../../../"
    if lang == "ko":
        title = "맛보기 편지 · 반 고흐 · 모차르트 · 나폴레옹의 편지 번역 · 언어 미술관"
        desc = "언어 미술관의 무료 장에서 고른 편지 9통: 반 고흐 · 모차르트 · 나폴레옹의 편지 원문(프랑스어 · 독일어)과 한국어 번역, 그림, 원어 낭독."
    else:
        title = "Sample letters · Van Gogh, Mozart and Napoleon in translation · Lingua Museum"
        desc = "Nine letters from Lingua Museum's free chapters: Van Gogh, Mozart and Napoleon in the original French and German with English translations, paintings and narration."
    t = head(title, desc, "/todayletters/letters/" + ("" if lang == "ko" else "en/"), "/todayletters/letters/", "/todayletters/letters/en/", lang, up)
    if lang == "ko":
        t += '<nav><a href="../">언어 미술관</a> · <a href="en/">English</a></nav>\n<h1>언어 미술관 · 맛보기 편지</h1>\n'
        t += '<p class="soft">작품마다 무료 장(I장)의 편지 3통: 원문 · 번역 · 그림 · 낭독 듣기. 아래 ‘앱에서 이어 읽기’ 로 앱에서 이어서 읽어요.</p>\n'
    else:
        t += '<nav><a href="../../#en">Lingua Museum</a> · <a href="../">한국어</a></nav>\n<h1>Lingua Museum · Sample letters</h1>\n'
        t += '<p class="soft">Three letters from the free chapter (Chapter I) of each series: the original, a translation, the painting and a narration.</p>\n'
    for s, items in all_items.items():
        sr = items[0]["sr"]
        t += f'<h2>{esc(sr["title"][lang])} · {esc(sr["kit"]["fullName"][lang])}</h2>\n<ul class="list">\n'
        for it in items:
            l = it["l"]
            w = WHO[s]
            d = date_text(l["date"], lang)
            h = f"{w['ko']} {it['to']['ko']}에게 · {d}" if lang == "ko" else f"{w['en']} to {it['to']['en']} · {d}"
            href = f"{s}/{l['id']}/" if lang == "ko" else f"../{s}/{l['id']}/en/"
            img = f"{s}/{l['id']}/plate.webp" if lang == "ko" else f"../{s}/{l['id']}/plate.webp"
            m = l["messages"][0]
            t += f'<li><img src="{img}" alt="" loading="lazy"><div><a href="{href}">{esc(h)}</a><p lang="{it["o"]}" style="font-style:italic">{esc(m[it["o"]][:90])}{"…" if len(m[it["o"]]) > 90 else ""}</p><p class="soft">{esc(m[lang][:70])}{"…" if len(m[lang]) > 70 else ""}</p></div></li>\n'
        t += "</ul>\n"
    t += f'<p><a class="play" href="{PLAY}">{"앱에서 이어 읽기 · Google Play" if lang == "ko" else "Keep reading in the app · Google Play"}</a></p>\n'
    return t + foot(up)


def main():
    site = os.path.abspath(sys.argv[1])
    out = os.path.join(site, "todayletters/letters")
    items = letters()
    flat = [it for s in SERIES for it in items[s]]
    for i, it in enumerate(flat):
        d = os.path.join(out, it["series"], it["l"]["id"])
        os.makedirs(os.path.join(d, "en"), exist_ok=True)
        if not it["plate"]:
            sys.exit(f"그림이 없어요: {it['series']} {it['l']['id']}")
        shutil.copyfile(it["plate"], os.path.join(d, "plate.webp"))
        if it["audio"]:
            shutil.copyfile(it["audio"], os.path.join(d, "m1.m4a"))
        prev = flat[i - 1] if i > 0 else None
        nxt = flat[i + 1] if i + 1 < len(flat) else None
        open(os.path.join(d, "index.html"), "w").write(letter_page(it, "ko", prev, nxt))
        open(os.path.join(d, "en", "index.html"), "w").write(letter_page(it, "en", prev, nxt))
        print(it["series"], it["l"]["id"], "audio" if it["audio"] else "no audio")
    os.makedirs(os.path.join(out, "en"), exist_ok=True)
    open(os.path.join(out, "index.html"), "w").write(index_page(items, "ko"))
    open(os.path.join(out, "en", "index.html"), "w").write(index_page(items, "en"))


if __name__ == "__main__":
    main()
