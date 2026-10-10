# 언어 미술관 (Lingua Museum)

고전 속 실제 편지가 메신저처럼 하루에 한 통씩 도착하고, 답장을 쓰면서 외국어를 배우는 안드로이드 앱이에요.
첫 시즌은 **빈센트 반 고흐가 테오에게 보낸 편지**(아를 시기, 프랑스어)예요. 나는 테오가 되어 편지를 받아요.

- 모든 편지는 **원어 · 영어 · 한국어** 세 줄을 갖고 있어요. 말풍선은 "배우는 언어 한 줄 + 작게 읽는 언어 한 줄"로 보여요.
- 배우는 언어는 **작품마다** 따로 정해요. 앱 글자는 영어 · 한국어 두 가지예요.
- 편지마다 이어지는 그림(저작권이 끝난 작품)과 큐레이터 노트로 시대와 장소를 함께 읽어요.
- 답장이 곧 학습이에요: 낱말 맞추기 · 별자리 잇기(어순) · 따라 읽기 · 듣고 쓰기.

## 문서

| 문서 | 내용 |
|---|---|
| [docs/ia.md](docs/ia.md) | 화면 계층 트리, 앱 시작 순서, 처음 소개, 뒤로 가기, 설정 범위, 도움말(?) |
| [design/tokens.json](design/tokens.json) | 디자인 토큰. 라이트 · 다크는 색 값만 다르고 이름은 같아요 |
| [design/strings.json](design/strings.json) | 앱 글자 (영어 · 한국어) |

## 구조

```
design/            토큰 · 글자 (유일한 원본)
scripts/generate.py  → android/app/.../design/Tokens.kt, res/values*/strings.xml, window.xml
android/core       순수 Kotlin: 설정 범위 · 화면 계층(뒤로 가기) · 앱 시작 순서 · 도움말. 어디서나 테스트 가능
android/app        Compose 앱 (SDK 가 있는 곳에서만 포함)
```

```sh
python3 scripts/generate.py           # 토큰 · 글자 다시 만들기 (생성 파일은 손으로 고치지 않아요)
python3 scripts/generate.py --check   # CI 와 같은 확인
cd android && ./gradlew :core:test :app:assembleDebug
```

CI(`.github/workflows/android.yml`)가 생성 파일 확인 → core 테스트 → 디버그 APK 를 만들어 Artifacts 에 올려요.

## 아직 남은 것

- 글꼴 파일 넣기 (Cormorant Garamond · Crimson Pro · Cinzel · Noto Serif KR, 쓰는 글자만 남기기). 지금은 시스템 세리프로 대신해요.
- 화면들: 지금 앱은 토큰 견본 화면(테마 · 글자 크기 확인용)만 보여 줘요.
