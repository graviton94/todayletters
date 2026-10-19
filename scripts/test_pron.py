#!/usr/bin/env python3
"""scripts/pron.py 시험: python3 -m unittest scripts/test_pron.py (espeak-ng 가 없으면 한 줄 시험은 건너뛴다)."""
import os, shutil, sys, unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pron  # noqa: E402

ESPEAK = shutil.which("espeak-ng") is not None


class IpaToHangul(unittest.TestCase):
    def test_words(self):
        # espeak-ng 의 IPA 그대로 (악센트 · 길이 표시 포함)
        cases = [("fr", "wˈi", "위"), ("fr", "sa", "싸"), ("fr", "twˈa", "뚜아"), ("fr", "bɔ̃ʒˈuʁ", "봉주르"), ("fr", "blˈɑ̃", "블랑"),
                 ("fr", "vˈɛ̃", "뱅"), ("fr", "lyˌi", "뤼"), ("fr", "bokˈu", "보꾸"),
                 ("de", "ɪç", "이히"), ("de", "ˈaɾbaɪt", "아르바이트"), ("de", "ɡəmˈaxt", "게마흐트"), ("de", "ʃˈøːn", "쇤"), ("de", "ʃvˈɛstɜ", "슈베스터"),
                 ("en", "θˈɪn", "씬"), ("en", "ðə", "더"), ("en", "vˈɛɹi", "베리"), ("en", "dˈɪɹ", "디어"), ("en", "kˈɑːɹz", "카즈"), ("en", "lˈɜːnɪŋ", "러닝")]
        for lang, ipa, want in cases:
            self.assertEqual(want, pron.hangul_word(ipa, lang), f"{lang} {ipa}")

    @unittest.skipUnless(ESPEAK, "espeak-ng 없음")
    def test_lines(self):
        self.assertEqual("위, 싸 바. 에 뚜아?", pron.hint("Oui, ça va. Et toi ?", "fr"))
        self.assertEqual("무아 오씨, 자프랑 보꾸!", pron.hint("Moi aussi, j'apprends beaucoup !", "fr"))
        self.assertEqual("레 자미 쏭 따리베.", pron.hint("les amis sont arrivés.", "fr"))
        self.assertEqual("이히 하베 디 아르바이트 게마흐트, 바일 에스 쇤 이스트.", pron.hint("Ich habe die Arbeit gemacht, weil es schön ist.", "de"))
        self.assertEqual("미 투, 아임 러닝 라츠!", pron.hint("Me too, I'm learning lots!", "en"))


class KoreanRomanization(unittest.TestCase):
    def test_lines(self):
        cases = [("나도 많이 배워!", "nado mani baewo!"), ("감사합니다.", "gamsahamnida."), ("좋아요", "joayo"), ("학교", "hakkyo"),
                 ("같이", "gachi"), ("신라", "silla"), ("축하해요", "chukahaeyo"), ("읽어요", "ilgeoyo"), ("국물", "gungmul"),
                 ("형은 멀리 있지만 늘 내 곁에 있어.", "hyeongeun meolli ijjiman neul nae gyeote isseo.")]
        for ko, want in cases:
            self.assertEqual(want, pron.romanize(ko), ko)

    def test_joiner_ignored(self):
        self.assertEqual("nado", pron.romanize("나⁠도"))


if __name__ == "__main__":
    unittest.main()
