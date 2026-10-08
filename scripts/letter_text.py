"""받아 온 편지 페이지(html)에서 글자만 뽑아 같은 이름의 .txt 로. 대조용 (fetch-letters.yml).

    python3 scripts/letter_text.py <폴더>
"""
import html, os, re, sys
from html.parser import HTMLParser


class Text(HTMLParser):
    SKIP = {"script", "style", "nav", "header", "footer", "noscript"}

    def __init__(self):
        super().__init__()
        self.out, self.skip = [], 0

    def handle_starttag(self, tag, attrs):
        if tag in self.SKIP:
            self.skip += 1
        if tag in ("p", "br", "div", "tr", "li", "h1", "h2", "h3"):
            self.out.append("\n")

    def handle_endtag(self, tag):
        if tag in self.SKIP and self.skip:
            self.skip -= 1

    def handle_data(self, data):
        if not self.skip:
            self.out.append(data)


folder = sys.argv[1]
for name in sorted(os.listdir(folder)):
    if not name.endswith((".html", ".htm")):
        continue
    raw = open(os.path.join(folder, name), encoding="utf-8", errors="replace").read()
    t = Text()
    t.feed(raw)
    text = html.unescape("".join(t.out))
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n\s*\n+", "\n\n", text).strip()
    open(os.path.join(folder, re.sub(r"\.html?$", ".txt", name)), "w", encoding="utf-8").write(text + "\n")
    print(name, len(text))
