"""
줄임말 뒤 마침표에서는 끊지 않는다 (앱 core `Abbrev` 와 같은 규칙): St. Peter · Mr. · Mrs. · Dr. · M. · Mme · Hr. · Sig. · heil. ·
No. 5 · 머리글자 J. Roulin · 날짜 den 3. Juni.

    from abbrev import split_keep
    split_keep(r"(?<=[,;:.?!…])\\s+", text)   # re.split 과 같지만 줄임말 뒤 띄어쓰기는 나누지 않는다
"""
import re

TITLES = {"Mr", "Mrs", "Ms", "Messrs", "Dr", "St", "Sts", "Mt", "Rev", "Prof", "Capt", "Col", "Gen", "Lt", "Sgt", "Jr", "Sr", "Esq", "Hon",
          "M", "MM", "Mme", "Mmes", "Mlle", "Mlles", "Mgr", "Ste", "Vve",
          "Hr", "Hrn", "Herrn", "Fr", "Frl", "Mad", "Mons", "Sig", "Sigr", "Sgra", "Ew", "Se", "Ihro", "heil", "hl", "Hl"}
BEFORE_NUMBER = {"No", "no", "Nos", "Nr", "nr", "n", "p", "pp", "S", "vol", "Vol", "op", "Op", "fig", "Fig", "art", "Art",
                 "Jan", "Febr", "Feb", "Mar", "Apr", "Aug", "Sept", "Sep", "Oct", "Okt", "Nov", "Dec", "Dez"}
SOFT = {"etc", "viz", "cf", "vs", "ca", "approx", "fr", "fl", "Fl", "usw", "bzw", "vgl", "resp", "sc", "z", "B", "d", "h", "u", "a", "i", "e",
        "av", "bd", "chap", "env"}
DATE_BEFORE = {"den", "dem", "der", "des", "am", "vom", "zum", "bis", "le", "du", "au", "the"}
GLUE = "⁠"


def keeps(text: str, dot: int) -> bool:
    """text[dot] 의 마침표가 문장 끝이 아니면 True."""
    if not (0 <= dot < len(text)) or text[dot] != ".":
        return False
    a = dot
    while a > 0 and text[a - 1].isalnum():
        a -= 1
    word = text[a:dot]
    if not word:
        return False
    joined = a > 0 and text[a - 1] == "."
    b = dot + 1
    while b < len(text) and (text[b].isspace() or text[b] in "\"“„«‘("):
        b += 1
    if b >= len(text):
        return False
    nxt = text[b]
    if word in TITLES:
        return True
    if len(word) == 1 and word.isupper() and word != "I":
        return nxt.isalpha() and (nxt.isupper() or joined)
    if word in BEFORE_NUMBER and nxt.isdigit():
        return True
    if (word in SOFT or joined) and (nxt.islower() or nxt.isdigit()):
        return True
    if word.isdigit() and len(word) <= 2:
        if nxt.islower():
            return True
        before = text[:a].rstrip()
        if not before or before[-1] in ".!?…:":
            return True
        prev = re.search(r"[^\W\d_]*$", before).group(0)
        if prev in DATE_BEFORE:
            return True
    return False


def split_keep(pattern: str, text: str):
    """re.split(pattern, text) 인데, 줄임말 마침표 바로 뒤의 띄어쓰기에서는 나누지 않는다."""
    glued = "".join(GLUE if ch == " " and i > 0 and text[i - 1] == "." and keeps(text, i - 1) else ch for i, ch in enumerate(text))
    return [p.replace(GLUE, " ") for p in re.split(pattern, glued)]


if __name__ == "__main__":
    assert split_keep(r"(?<=[.?!])\s+", "I sketched St. Peter. And Mr. Roos.") == ["I sketched St. Peter.", "And Mr. Roos."]
    assert len(split_keep(r"(?<=[.?!])\s+", "Heute ist der 3. Juni. Ich bin im Bett.")) == 2
    assert len(split_keep(r"(?<=[.?!])\s+", "So do I. Then we left.")) == 2
    print("ok")
