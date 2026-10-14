# Play 등록 자료 · 언어 미술관 (Lingua Museum)

패키지 `io.github.graviton94.todayletters` · 버전 0.9.0 (비공개 테스트) · 2026-10-14 작성.
“확인 필요” 는 사용자가 정하거나 직접 확인할 곳이에요. 글자 수는 Play 가 세는 방식(유니코드 문자 수)으로 셌어요.

## 0. 앱 만들기 (처음 한 번)  ·  Create app


Play Console › 앱 만들기 양식이 묻는 것은 앱 이름 · 기본 언어 · 앱/게임 · 무료/유료 · 선언(정책 · 미국 수출법 동의)뿐이에요. 패키지 이름은 이 양식에 넣지 않아요 — 처음 올리는 AAB 에 든 applicationId 로 정해지고 그 뒤로는 영영 바꿀 수 없어요.

- **앱 이름 (기본 언어 en-US):** Lingua Museum
- **기본 언어:** 영어 (미국) – en-US (기본) · 번역: 한국어 – ko-KR 추가
- **앱 또는 게임:** 앱
- **무료 또는 유료:** 무료 (인앱 상품은 따로 등록). 무료로 낸 앱은 나중에 유료로 바꿀 수 없어요.
- **패키지 이름 (양식에 없음):** io.github.graviton94.todayletters — 앱 만들기 양식에서 입력하지 않아요. 첫 AAB 를 올릴 때 그 안의 applicationId(android/app/build.gradle.kts)로 고정되고, 영구히 바꿀 수 없어요.
- **연락처 이메일:** ruahn49@gmail.com

## 1. 기본 스토어 등록정보  ·  Main store listing


기본 언어는 영어(en-US), 한국어(ko-KR)는 번역으로 추가해요. Play 는 기기 언어가 한국어인 사용자에게 ko 등록정보를, 그 밖의 모든 사용자에게 en 등록정보를 보여 줘요. 앱 UI 도 같은 방식이에요 (values/ = 영어, values-ko/ = 한국어). 아래 칸은 항목마다 en(기본) → ko 순서예요.

### App name · en-US (기본)  (13 / 30자)

```text
Lingua Museum
```

### App name · en-US (descriptive option)  (30 / 30자)

```text
Lingua Museum: Classic Letters
```

### 앱 이름 · ko-KR  (6 / 30자)

```text
언어 미술관
```

### 앱 이름 · ko-KR (설명을 붙인 안)  (23 / 30자)

```text
언어 미술관: 명화와 편지로 배우는 외국어
```
> **확인 필요:** 이름을 짧게(언어 미술관) 둘지, 설명을 붙일지. 앱 아이콘 아래 이름(app_name)은 “언어 미술관”

### Short description · en-US (기본)  (78 / 80자)

```text
Learn French, German, English or Korean from real Van Gogh and Mozart letters.
```

### 간단한 설명 · ko-KR  (62 / 80자)

```text
고흐와 모차르트가 실제로 쓴 편지를 하루 한 통씩 받아 읽으며 프랑스어 · 독일어 · 영어 · 한국어를 익혀요.
```

### Full description · en-US (기본)  (3279 / 4000자)

```text
Learn a language by receiving real letters from the classics, one a day, like messages.

Lingua Museum has two series.
· Letters from Vincent: Vincent van Gogh's letters to his brother Theo (1888–1890, in French)
· Letters from Mozart: Wolfgang Amadeus Mozart's letters to his father, his sister, his wife and friends (1770–1791, in German)

Every letter has three lines: the original, English and Korean. You choose the language to learn for each series and can change it any time (progress is kept per language).

■ Today's journey
One new letter arrives each day for each series. Read today's letter, do today's review, and when the journey is complete the letter's painting goes up in your gallery.

■ The steps of a letter
· Read: messages arrive line by line. The large line is the language you learn, the small line is the translation. Tap a word for its meaning; the curator's note tells the story behind the letter.
· Match the words: join each word of the letter to its meaning.
· Read along: say the sentence phrase by phrase and the app compares your intonation and rhythm with the narration, on your phone, and gives you stars (it does not score pronunciation). Read a whole letter on your own and it is sealed.
· Reply: "Imagine you are Theo. Shall we write back?" Write a reply that quotes the letter. Each chapter has its own Today's grammar, and as you grow the help steps back: join the stars → fill the blanks → write it yourself.
· Painting: receive the letter's painting and take a walk through it, one glowing spot at a time.

■ Translations that step back as you grow
Each letter shows how many of its words you know. As you learn words, translations fold away for sentences you can read, and the original reads on its own. Levels A1–B2 are this app's own measure of the letters you read here, not an official exam grade.

■ Review
The words you collect and the replies you write become review cards: pick the meaning, fill the blank, listen and write, or say it aloud. There is free practice and a collection of your saved quotes too.

■ Gallery and My Record
Letter paintings, collected pieces and this month's exhibition. Fill the medal cabinet with reading streaks, letters read, words learned and read-along stars, and hear your week's readings at the Sunday reading.

■ Comfort
· Letter narration keeps playing with the screen off or in another app.
· Choose when letters arrive, and see today's line on a home-screen widget.
· Light and dark themes, two text sizes. The app's own text is in English and Korean.

■ Free and paid
Each series' prologue and chapters I and II are free. From chapter III, buy the series (one payment) or subscribe (monthly or yearly). Payments are handled by Google Play.

■ Privacy
No accounts, no ads, no analytics. The letters you read, your review record and your recordings stay on your phone. The internet is used only to download a chapter's narration and pictures, and for Google Play payments.

■ Sources
Letter texts and pictures come only from public-domain originals and material whose holders release it for free use. Lingua Museum is not affiliated with, sponsored or endorsed by any museum, library or foundation it names. Sources and licences: graviton94.github.io/todayletters

Contact: ruahn49@gmail.com
```

### 자세한 설명 · ko-KR  (1625 / 4000자)

```text
고전의 진짜 편지를 메신저처럼 받아 읽으며 그 언어를 익혀요.

언어 미술관에는 두 작품이 있어요.
· 빈센트의 편지: 빈센트 반 고흐가 동생 테오에게 쓴 편지 (1888–1890, 프랑스어 원문)
· 모차르트의 편지: 볼프강 아마데우스 모차르트가 아버지 · 누나 · 아내와 친구들에게 쓴 편지 (1770–1791, 독일어 원문)

모든 편지는 원어 · 영어 · 한국어 세 줄을 갖고 있어요. 배우는 말은 작품마다 따로 고르고, 언제든 바꿀 수 있어요 (진도는 언어마다 따로 저장돼요).

■ 오늘의 여정
작품마다 하루에 새 편지가 한 통 도착해요. 오늘의 편지를 읽고, 오늘의 복습을 하고, 여정을 마치면 오늘 편지의 그림이 갤러리에 걸려요.

■ 편지 한 통의 걸음
· 읽기: 메시지가 한 줄씩 도착해요. 큰 줄은 배우는 말, 작은 줄은 번역. 낱말을 누르면 뜻이 나오고, 큐레이터 노트가 편지의 배경을 알려 줘요.
· 낱말 맞추기: 이 편지의 낱말과 뜻을 선으로 이어요.
· 따라 읽기: 편지 문장을 마디째 따라 말하면, 원래 낭독과 억양 · 리듬을 폰 안에서 비교해 별을 줘요 (발음은 채점하지 않아요). 한 통을 혼자 다 읽으면 그 편지가 봉인돼요.
· 답장: “당신이 테오라고 생각하고 답장해 볼까요?” 편지 문장을 인용해 답장을 써요. 장마다 ‘오늘의 문법’이 바뀌고, 실력이 늘면 별자리 잇기 → 빈칸 채우기 → 직접 쓰기로 도움이 줄어요.
· 그림: 이 편지의 그림을 받고, 그림 속 산책으로 빛나는 자리를 하나씩 걸어 봐요.

■ 실력만큼 물러나는 번역
편지마다 내가 아는 낱말의 비율을 보여 줘요. 낱말을 익힐수록 아는 문장의 번역이 접혀, 원문이 그대로 읽혀요. 언어 등급(A1~B2)은 이 앱에서 읽은 편지로 잰 이 앱만의 기준이에요. 공인 시험 등급과 같지 않아요.

■ 복습
편지에서 모은 낱말과 내가 쓴 답장이 복습 카드가 돼요. 뜻 고르기 · 빈칸 · 듣고 쓰기 · 소리 내 말해 보기로 다시 만나요. 자유 연습과 내 구절 모음도 있어요.

■ 갤러리와 나의 기록
편지의 그림, 모은 작품, 이달의 전시. 이어 읽기 · 읽은 편지 · 익힌 낱말 · 따라 읽기 별로 메달 진열장을 채우고, 일요일 낭독회에서 이번 주 내 낭독을 이어 들어요.

■ 편하게
· 편지 낭독은 화면을 끄거나 다른 앱을 써도 이어져요.
· 도착 알림 시각을 고르고, 홈 화면 위젯으로 오늘의 문장을 봐요.
· 라이트 · 다크 테마, 글자 크기 두 단계. 앱 글자는 한국어와 영어.

■ 무료와 유료
각 작품의 프롤로그와 I · II장은 무료예요. III장부터는 작품 소장(한 번 결제) 또는 구독(월 · 연)으로 열려요. 결제는 Google Play가 처리해요.

■ 개인정보
계정이 없고, 광고와 분석 도구가 없어요. 읽은 편지 · 복습 기록 · 녹음한 목소리는 모두 폰 안에만 있어요. 인터넷은 장의 낭독 · 그림 파일을 받을 때와 Google Play 결제에만 써요.

■ 출처
편지 글과 그림은 저작권이 끝난(공공 영역) 원본과, 소장처가 자유 이용을 밝힌 자료만 써요. 언어 미술관은 여기 나오는 미술관 · 도서관 · 재단과 관계가 없으며 후원이나 승인을 받지 않았어요. 출처와 라이선스: graviton94.github.io/todayletters

문의: ruahn49@gmail.com
```


결제 문단(“III장부터는 작품 소장 또는 구독으로 열려요” / “From chapter III, buy the series …”)은 그대로 둬요 (결정됨).


설명의 사실 근거: 편지 수 · 연도 · 원문 판본은 data/*/series.json 과 출처 페이지, 기능 이름은 design/strings.json, 무료 범위는 docs/billing.md, 개인정보 문장은 개인정보처리방침의 “한 줄 요약”. 낭독은 합성 음성(docs/voices.md, Supertonic)이라 설명에 “성우” 같은 말은 쓰지 않았어요. 합성 음성임을 설명에 밝힐지 — 확인 필요.


### 그래픽

| 파일 | 무엇 | 크기 |
|---|---|---|
| `icon_512.png` | 앱 아이콘 — 512 × 512 · 32비트 PNG (알파 채널) · design/store/icon-512.png (런처 적응형 아이콘과 같은 그림: 봉투에서 나오는 〈해바라기〉 · 〈밀짚모자를 쓴 자화상〉, 메트로폴리탄 미술관 CC0). 모서리 둥글리기는 Play 가 해요. en · ko 공통. | 213 KB |
| `feature2/feature2_1_en.png` | Feature graphic · en-US (기본) — 1024 × 500 · 알파 없음 · 시안 1 (편지 말풍선 · 낱말 뜻 · 답장). 그림: Vincent van Gogh, 〈밀짚모자를 쓴 자화상 Self-Portrait with a Straw Hat〉, 1887 — The Metropolitan Museum of Art, New York, CC0 (Open Access), metmuseum.org/art/collection/search/436532 · 앱 plates/c1-l1.webp. 그림은 자르거나 색을 바꾸지 않고 크기만 줄였어요. | 345 KB |
| `feature2/feature2_1_ko.png` | 그래픽 이미지 · ko-KR — 1024 × 500 · 알파 없음 · 시안 1 한국어판. 그림: 위와 같음. | 348 KB |
| `final/en_1.png` | 휴대전화 스크린샷 · en-US (기본) 1: “Talk with history / through real letters” — Letters of Van Gogh and Mozart (앱 화면 `07_room_ko_light`) | 1080×1920 · 337 KB |
| `final/en_2.png` | 휴대전화 스크린샷 · en-US (기본) 2: “Read, review, and / collect masterpieces” — A letter a day, a painting with each (앱 화면 `51_today_focus_light`) | 1080×1920 · 328 KB |
| `final/en_3.png` | 휴대전화 스크린샷 · en-US (기본) 3: “Learn words, / earn medals and levels” — Language levels A1–B2 · medal cabinet (앱 화면 `g07_grade_up_band_dark`) | 1080×1920 · 411 KB |
| `final/en_4.png` | 휴대전화 스크린샷 · en-US (기본) 4: “Read aloud, match words, / learn the language” — Read aloud · word match · dictation (앱 화면 `14_aloud_ko_dark`) | 1080×1920 · 203 KB |
| `final/en_5.png` | 휴대전화 스크린샷 · en-US (기본) 5: “Write back to / history's greats” — From fill-in-the-blank to free writing (앱 화면 `grammarReal01_vincent3_l1_turn1_b1_fr_light`) | 1080×1920 · 242 KB |
| `final/en_6.png` | 휴대전화 스크린샷 · en-US (기본) 6: “Your own gallery / of masterpieces” — Letter paintings · monthly exhibitions (앱 화면 `40_gallery_dark`) | 1080×1920 · 500 KB |
| `final/ko_1.png` | 휴대전화 스크린샷 · ko-KR 1: “역사 속 인물들의 실제 편지들로 / 대화를 나누어요” — 빈센트 반 고흐 · 모차르트의 편지 (앱 화면 `07_room_ko_light`) | 1080×1920 · 350 KB |
| `final/ko_2.png` | 휴대전화 스크린샷 · ko-KR 2: “편지를 읽고 복습하며 / 예술 작품을 모아요” — 하루 한 통 · 편지마다 이어지는 명화 (앱 화면 `51_today_focus_light`) | 1080×1920 · 341 KB |
| `final/ko_3.png` | 휴대전화 스크린샷 · ko-KR 3: “낱말을 익히면서 / 뿌듯함을 쌓아요” — 언어 등급 A1~B2 · 메달 진열장 (앱 화면 `g07_grade_up_band_dark`) | 1080×1920 · 420 KB |
| `final/ko_4.png` | 휴대전화 스크린샷 · ko-KR 4: “따라 읽고, 낱말을 맞추며 / 언어를 익혀요” — 따라 읽기 · 낱말 맞추기 · 듣고 쓰기 (앱 화면 `14_aloud_ko_dark`) | 1080×1920 · 212 KB |
| `final/ko_5.png` | 휴대전화 스크린샷 · ko-KR 5: “역사 속 인물에게 / 직접 답장 해봐요” — 빈칸 채우기부터 직접 쓰기까지 (앱 화면 `grammarReal01_vincent3_l1_turn1_b1_fr_light`) | 1080×1920 · 249 KB |
| `final/ko_6.png` | 휴대전화 스크린샷 · ko-KR 6: “명화를 모으는 / 나만의 갤러리” — 편지의 그림 · 이달의 전시 (앱 화면 `40_gallery_dark`) | 1080×1920 · 498 KB |

그래픽 이미지 그림 출처: Vincent van Gogh, 〈밀짚모자를 쓴 자화상 Self-Portrait with a Straw Hat〉, 1887 — The Metropolitan Museum of Art, New York, CC0 (Open Access), metmuseum.org/art/collection/search/436532 · 앱 plates/c1-l1.webp. 그림은 자르거나 색을 바꾸지 않고 크기만 줄였어요.

en 스크린샷 속 앱 화면은 아직 한국어 UI 예요 — 프로덕션 전에 영어 UI 로 다시 찍어요.

- **앱 카테고리:** 교육 (Education)  
  **확인 필요:** 교육 / 참고자료(Books & Reference) 중 교육 권장
- **태그 (최대 5):** 언어 학습 · 교육 · 예술 · 외국어 · 독서 — Play 가 주는 목록에서 가까운 것을 고르세요  
  **확인 필요:** Play 태그 목록에 실제로 있는 이름으로 고르기
- **이메일:** ruahn49@gmail.com
- **웹사이트:** https://graviton94.github.io/todayletters/
- **전화번호:** (비워 둠 — 선택 항목)

## 2. 앱 콘텐츠 · 개인정보처리방침  ·  App content › Privacy policy

- **개인정보처리방침 URL:** https://graviton94.github.io/todayletters/privacy/  
  **확인 필요:** 사이트 저장소 main 에 todayletters/privacy/index.html 이 있음을 확인했어요. 이 환경에서는 외부 접속이 막혀 브라우저로 열리는지는 직접 확인

## 3. 앱 콘텐츠 · 광고  ·  Ads

- **앱에 광고가 있나요?:** 아니요

근거: android/app/build.gradle.kts 의 의존성은 Compose · AndroidX · androidx.media · Play Billing 8 뿐이고 광고 · 분석 SDK(AdMob, Firebase 등)가 없어요. 개인정보처리방침도 “광고와 분석 도구가 없어요”.

- **광고 ID (Advertising ID) 사용:** 아니요  
  **확인 필요:** 첫 AAB 의 병합된 매니페스트에 com.google.android.gms.permission.AD_ID 가 없는지 Play Console 의 앱 번들 탐색기에서 확인

## 4. 앱 콘텐츠 · 앱 액세스  ·  App access

- **선택:** 모든 기능을 특별한 액세스 권한 없이 사용할 수 있음  
  **확인 필요:** 아래 잠긴 부분 때문에 “일부 기능 제한”으로 내고 안내문을 붙일지

계정 · 로그인 · 초대 코드가 없어요. 처음 열면 입구 → 환영 → 서재 튜토리얼(빈센트, 배울 말 고르기) → 편지 I 로 이어져요.


잠긴 것: 각 작품의 III장부터는 잠겨 있고 누르면 구매 화면이 떠요. Play 상품(sub_all, series_vincent, series_mozart)이 아직 등록되지 않아 결제 단추는 “곧 열려요”(누를 수 없음)예요. 새 편지는 작품마다 하루 한 통 도착해요 (첫날 빈센트만 두 통). 무료 범위: 프롤로그 + I · II장 (빈센트 18통, 모차르트 16통).

### 심사자 안내 (“일부 기능 제한”을 고를 때 붙일 글 · en)  (422 / 500자)

```text
No account or login is needed. On first launch the app guides you through Vincent's first letter. Each series' prologue and chapters I-II are free; chapter III onward is locked and the purchase screen shows 'Coming soon' because in-app products are not active yet. New letters arrive one per day per series (two on the first day for Vincent). The prologue of each series can be opened at any time from the series contents.
```


## 5. 앱 콘텐츠 · 콘텐츠 등급 (IARC 설문)  ·  Content rating

- **이메일:** ruahn49@gmail.com
- **카테고리:** 참고 자료, 뉴스 또는 교육 (Reference, News, or Educational)

| 질문 (요지) | 권장 답 | 근거 |
|---|---|---|
| 폭력 · 유혈 · 무기 묘사 | 아니요 | 역사 편지 글과 명화. 싸움 · 무기 장면이 없음 |
| 성적 콘텐츠 · 노출 | 아니요 | 공공 영역 회화·초상만 씀 |
| 욕설 · 저속한 유머 | 아니요 | 편지 데이터에서 비속어를 찾지 못함 (“Dreck” 한 번은 ‘흙장난’의 비유) |
| 약물 · 술 · 담배 언급 | 아니요 (확인 필요) | 빈센트가 받은 소포 목록에 “tabac(담배)”, 몇몇 편지에 술(vin) 낱말이 지나가듯 나옴. 묘사·권장은 아님 |
| 도박 · 모의 도박 | 아니요 | 보상 순간의 그림 뽑기는 앱 안 화폐(우표)로만, 화폐는 돈으로 팔지 않음 (docs/billing.md) |
| 공포 · 불안감을 주는 내용 | 아니요 (확인 필요) | 편지에 죽음 · 병 · 상실(모차르트 어머니의 죽음, 빈센트의 병과 요양원)이 나오지만 사실을 전하는 글 |
| 사용자끼리 소통 · 콘텐츠 공유 | 아니요 | 채팅 · 게시판 · 다른 사용자 없음. 의견 보내기는 사용자의 메일 앱으로 개발자에게 메일. 구절 엽서 · 목소리 엽서는 안드로이드 공유 창으로 사용자가 고른 앱에 넘김 |
| 사용자 위치 공유 | 아니요 | 위치 카드는 편지 속 장소의 좌표를 지도 앱에 넘길 뿐, 사용자 위치는 쓰지 않음 |
| 디지털 상품 구매 | 예 | 구독 · 시리즈 소장 (Play Billing). 아직 상품 미등록이지만 코드에 있음 |
| 제한 없는 인터넷 접속 (브라우저 등) | 아니요 | 앱 안 브라우저 없음. 출처 페이지 · 지도 · 유튜브 검색은 외부 앱으로 엶 |


예상 등급: 모든 연령 / 3세 이상 수준. 설문 문구는 Play Console 에서 조금 다를 수 있어요 — 각 질문을 위 표의 요지에 맞춰 답하세요 (확인 필요).


## 6. 앱 콘텐츠 · 타겟층 및 콘텐츠  ·  Target audience and content

- **타겟 연령:** 13–15세 · 16–17세 · 18세 이상  
  **확인 필요:** 만 13세 미만을 고르면 가족 정책(Families) 심사가 붙어요. 개인정보처리방침도 “만 13세 미만 어린이를 대상으로 하지 않아요”라 13세 이상 권장
- **아동의 관심을 끌 수 있나요?:** 아니요  
  **확인 필요:** 명화 · 그림 위주의 화면이 아동 대상으로 보일 수 있다고 판단되면 “예”로 바꾸고 이유를 적기

권장 근거: 편지에는 죽음 · 병 · 가족의 상실이 나오고(편지 원문 그대로), 학습 수준(A1~B2)과 하루 한 통 구조가 청소년 · 성인 학습자 기준이에요. 콘텐츠 자체는 폭력 · 성적 내용이 없어 등급은 낮게 나올 거예요.

- **뉴스 앱 / 코로나19 / 정부 앱 / 금융 기능 / 건강 앱:** 모두 아니요 · 해당 없음

## 7. 앱 콘텐츠 · 데이터 보안  ·  Data safety

- **필수 사용자 데이터 유형을 수집하거나 공유하나요?:** 아니요 (권장)  
  **확인 필요:** 아래 표의 판단(특히 말해 보기 음성 인식, Google 백업, Cloudflare 가 받는 IP)을 보고 최종 결정

| 데이터 | 앱이 하는 일 | 양식에서 |
|---|---|---|
| 오디오 (따라 읽기 녹음) | 기기 안에서 녹음 · 비교 · 저장. 밖으로 보내지 않음 | 수집 아님 (기기에서만 처리) |
| 오디오 (복습 ‘소리 내 말해 보기’) | 폰의 음성 인식 서비스(SpeechRecognizer)에 맡기고 EXTRA_PREFER_OFFLINE 으로 기기 안 처리를 요청. 앱은 소리를 저장 · 전송하지 않음 | 수집 아님 (확인 필요 — 인식 서비스가 자체 정책으로 처리할 수 있다고 방침에 적어 둠) |
| 앱 활동 · 진도 · 설정 | 기기 안 (letters.xml). Android 백업 · 기기 이전에만 포함 | 수집 아님 (확인 필요 — Android 백업을 수집으로 보지 않는 것이 일반적) |
| 오류 기록 (비정상 종료) | 기기 안에만(CrashLog). 사용자가 ‘의견 보내기’로 자기 메일 앱에서 직접 보낼 때만 전달 | 수집 아님 (사용자가 다른 앱으로 직접 보내는 것) |
| 기기 정보 (모델 · Android · 앱 버전 · 배우는 말) | 의견 메일 본문에 붙음 (사용자가 끄고 켬, 보내기 전 확인) | 위와 같음 |
| 구매 내역 | Google Play 가 처리. 앱은 이용권(구독 중 · 소장 시리즈)만 기기에 저장 | 수집 아님 |
| 네트워크 (낭독 · 그림 받기) | Cloudflare R2 공개 주소에서 파일을 받기만 함. 앱이 보내는 개인 정보 없음. Cloudflare 가 IP · 요청 시각을 기술적으로 처리 | 수집 아님 (확인 필요) |
| 위치 · 연락처 · 사진 · 계정 · 식별자 | 쓰지 않음 | 해당 없음 |

- **전송 중 암호화 (질문이 나올 경우):** 예 — 파일 받기는 HTTPS (https://pub-4ca8de2e978d484887e1086f68454b0b.r2.dev/), 평문 HTTP 허용 설정 없음
- **데이터 삭제 요청 방법 (질문이 나올 경우):** 계정이 없어 서버에 지울 데이터가 없음. 기기 안 데이터는 앱 데이터 지우기 · 앱 삭제로 모두 지워짐. 받은 낭독 · 그림은 설정 › 저장 공간에서 지움
- **계정 만들기 · 계정 삭제 URL:** 앱에 계정 없음 → 해당 없음

## 8. 앱 콘텐츠 · 포그라운드 서비스 권한  ·  Foreground service permissions


매니페스트: NarrationService = mediaPlayback, DownloadService = dataSync. 두 유형 모두 신고하고 유형마다 시연 영상 링크(YouTube 일부 공개 등)를 넣어요. 한국어 문구는 배포 준비 시안 v6 ⑤ 에서 승인된 그대로예요 (docs/release.md 4).

### Media playback · description (en)  (193자)

```text
Continues playing the narration of a letter while the screen is off or the user is in another app. Playback is always started by the user, who can pause, skip and stop it from the notification.
```

### 미디어 재생 · 설명 (ko, 승인 문구)  (80자)

```text
편지 낭독을 화면이 꺼지거나 다른 앱을 쓰는 중에도 이어서 들려줍니다. 재생은 사용자가 직접 시작하고, 알림에서 멈추고 넘기고 끌 수 있습니다.
```

### Data sync · description (en)  (288자)

```text
Downloads audio files to the device so they can be played without an internet connection. The download is always started by the user, shows its progress in a notification for its whole duration and continues if the app is closed. The user can stop it at any time from the download screen.
```

### 데이터 동기화 · 설명 (ko, 승인 문구)  (113자)

```text
오디오 파일을, 인터넷 없이 들을 수 있도록 기기에 내려받습니다. 받기는 사용자가 직접 시작하고, 진행 상황을 알림으로 계속 보여 주며, 앱을 닫아도 이어집니다. 받기 화면에서 언제든 멈출 수 있습니다.
```
> **확인 필요:** 앱은 받기 알림에서도 멈출 수 있어요(dl_bg). 승인 문구는 “받기 화면에서”만 말해요 — 그대로 둘지, “받기 화면과 알림에서”로 고칠지

- **사용자에게 미치는 영향 (선택 항목이 나오면):** 미디어 재생: 사용자가 시작한 재생이 끊김 / 데이터 동기화: 사용자가 시작한 다운로드가 앱을 닫으면 멈춤

| 초 | 시연 영상 30초 (시험 APK, 화면 녹화) |
|---|---|
| 0–4 | 빈센트 편지를 열고 문장의 듣기 단추를 눌러 낭독 시작 |
| 4–9 | 홈 버튼 → 다른 앱으로 가도 낭독이 이어짐, 알림창을 내려 재생 알림(⏮ ⏸ ⏭ ✕) 보이기 |
| 9–13 | 화면을 끄고 켜서 잠금 화면에서도 재생 중인 것 보이기, 알림의 ⏸ · ✕ 로 멈추고 끄기 |
| 13–18 | 앱으로 돌아와 미리 받기(낭독 받기) 확인 창 → 받기 누르기 |
| 18–24 | 최근 앱에서 앱을 닫아도 진행 알림(“낭독 받는 중”, 진행 막대 · 멈추기)이 계속 보이기 |
| 24–30 | 알림 또는 받기 화면에서 멈추기 → 알림이 사라짐 (또는 “받기 끝” 알림) |


영상은 미디어 재생 · 데이터 동기화 두 부분으로 나눠 두 링크로 내도 돼요. 시험 APK 는 debug 빌드라도 동작은 같아요 (확인 필요: 영상 올릴 곳 · 공개 범위).


## 9. 권한 메모 (심사 문의 · 개인정보 설명용)  ·  Permissions


| 권한 | 쓰는 곳 | 언제 묻나 |
|---|---|---|
| RECORD_AUDIO | 따라 읽기 녹음 (기기 안 저장, 억양 · 리듬 비교) · 복습의 ‘소리 내 말해 보기’ 음성 인식 | 그 기능을 처음 쓸 때만. 거절해도 다른 기능은 그대로 (말해 보기는 스스로 판정으로) |
| POST_NOTIFICATIONS | 편지 도착 · 저녁 알림, 낭독 재생 알림, 낭독 받기 진행 알림 | 첫 편지를 끝낸 뒤 한 번 |
| RECEIVE_BOOT_COMPLETED | 재부팅 · 시간대 · 시각 변경 · 앱 업데이트 뒤 도착 알림을 다시 예약 (ArrivalReceiver) | 묻지 않음 (일반 권한) |
| FOREGROUND_SERVICE · FOREGROUND_SERVICE_MEDIA_PLAYBACK | 편지 낭독을 화면 끔 · 다른 앱에서도 이어 듣기 (NarrationService) | 묻지 않음 · Play 신고(8) |
| FOREGROUND_SERVICE_DATA_SYNC | 사용자가 시작한 낭독 미리 받기를 앱을 닫아도 이어서 (DownloadService) | 묻지 않음 · Play 신고(8) |
| INTERNET · ACCESS_NETWORK_STATE | R2 에서 장의 낭독 · 그림 받기, 와이파이인지 확인 (모바일 데이터면 먼저 물음) · Play 결제 | 묻지 않음 |
| <queries> RecognitionService | 폰에 음성 인식 서비스가 있는지 찾기 (Android 11+) | — |


## 10. 출시 · 비공개 테스트  ·  Release › Closed testing

- **트랙:** 테스트 › 비공개 테스트 (기본 “Closed testing” 트랙)
- **AAB:** Actions › Android AAB › Run workflow, version = 0.9.0 (사용자가 “릴리즈하자” 한 뒤에만). versionCode = 1000 + 실행 번호
- **출시 이름:** 0.9.0
### Release notes · en-US (기본, <en-US> 태그 안)  (422 / 500자)

```text
The first closed-test release.
· Letters from Vincent and Mozart, one a day for each series
· Read, match the words, read along, reply (Today's grammar), painting walk
· Known-word share, language level, translations that step back
· Review, gallery, medals, My Record
The prologue and chapters I and II are free; chapter III onward is locked for now (payments coming soon).
Tell us anything from Settings › Send feedback.
```

### 출시 노트 · ko-KR (<ko-KR> 태그 안)  (221 / 500자)

```text
비공개 테스트 첫 버전이에요.
· 빈센트 · 모차르트의 편지, 작품마다 하루 한 통
· 읽기 · 낱말 맞추기 · 따라 읽기 · 답장 (오늘의 문법) · 그림 산책
· 아는 낱말 비율 · 언어 등급 · 실력만큼 물러나는 번역
· 복습 · 갤러리 · 메달 · 나의 기록
프롤로그와 I · II장은 무료, III장부터는 잠겨 있어요 (결제는 곧 열려요).
설정 › 의견 보내기로 무엇이든 알려 주세요.
```

- **테스터:** 이메일 목록 또는 Google 그룹. 15~20명 모집 (docs/next.md)  
  **확인 필요:** 테스터 명단 · 방식 (이메일 목록 / Google 그룹)
- **프로덕션 신청 조건:** 2023년 11월 13일 뒤에 만든 개인 개발자 계정이면: 비공개 테스트에 12명 이상이 참여한 상태로 14일 연속 → 그 뒤 프로덕션 액세스 신청  
  **확인 필요:** 개발자 계정 종류(개인/조직)와 만든 날짜, 조건 숫자는 신청 시점의 Play Console 안내로 다시 확인
- **피드백 채널 (테스트 설정):** ruahn49@gmail.com (앱 안: 설정 › 의견 보내기)
- **국가 / 지역:** 대한민국 (+ 테스터가 있는 나라)  
  **확인 필요:** 배포 국가

테스트 시작 전에 Play Console › 수익 창출 › 상품에 sub_all(monthly · yearly), series_vincent, series_mozart 를 등록하면 결제가 열려요 — 지금은 결제 프로필이 없어 등록하지 않음 (docs/billing.md, docs/next.md: 사용자가 따로 요청).


## 확인 필요 모음

1. **1. 기본 스토어 등록정보 › 앱 이름 · ko-KR (설명을 붙인 안)** — 이름을 짧게(언어 미술관) 둘지, 설명을 붙일지. 앱 아이콘 아래 이름(app_name)은 “언어 미술관”
2. **1. 기본 스토어 등록정보 › 메모** — 설명의 사실 근거: 편지 수 · 연도 · 원문 판본은 data/*/series.json 과 출처 페이지, 기능 이름은 design/strings.json, 무료 범위는 docs/billing.md, 개인정보 문장은 개인정보처리방침의 “한 줄 요약”. 낭독은 합성 음성(docs/voices.md, Supertonic)이라 설명에 “성우” 같은 말은 쓰지 않았어요. 합성 음성임을 설명에 밝힐지 — 확인 필요.
3. **1. 기본 스토어 등록정보 › 앱 카테고리** — 교육 / 참고자료(Books & Reference) 중 교육 권장
4. **1. 기본 스토어 등록정보 › 태그 (최대 5)** — Play 태그 목록에 실제로 있는 이름으로 고르기
5. **1. 기본 스토어 등록정보 › 영어 스크린샷 (en_1–6)** — en 세트 속 앱 화면은 아직 한국어 UI예요. 프로덕션 전에 영어 UI 로 다시 찍기.
6. **2. 앱 콘텐츠 · 개인정보처리방침 › 개인정보처리방침 URL** — 사이트 저장소 main 에 todayletters/privacy/index.html 이 있음을 확인했어요. 이 환경에서는 외부 접속이 막혀 브라우저로 열리는지는 직접 확인
7. **3. 앱 콘텐츠 · 광고 › 광고 ID (Advertising ID) 사용** — 첫 AAB 의 병합된 매니페스트에 com.google.android.gms.permission.AD_ID 가 없는지 Play Console 의 앱 번들 탐색기에서 확인
8. **4. 앱 콘텐츠 · 앱 액세스 › 선택** — 아래 잠긴 부분 때문에 “일부 기능 제한”으로 내고 안내문을 붙일지
9. **5. 앱 콘텐츠 · 콘텐츠 등급 (IARC 설문) › 약물 · 술 · 담배 언급** — 아니요 (확인 필요) · 빈센트가 받은 소포 목록에 “tabac(담배)”, 몇몇 편지에 술(vin) 낱말이 지나가듯 나옴. 묘사·권장은 아님
10. **5. 앱 콘텐츠 · 콘텐츠 등급 (IARC 설문) › 공포 · 불안감을 주는 내용** — 아니요 (확인 필요) · 편지에 죽음 · 병 · 상실(모차르트 어머니의 죽음, 빈센트의 병과 요양원)이 나오지만 사실을 전하는 글
11. **5. 앱 콘텐츠 · 콘텐츠 등급 (IARC 설문) › 메모** — 예상 등급: 모든 연령 / 3세 이상 수준. 설문 문구는 Play Console 에서 조금 다를 수 있어요 — 각 질문을 위 표의 요지에 맞춰 답하세요 (확인 필요).
12. **6. 앱 콘텐츠 · 타겟층 및 콘텐츠 › 타겟 연령** — 만 13세 미만을 고르면 가족 정책(Families) 심사가 붙어요. 개인정보처리방침도 “만 13세 미만 어린이를 대상으로 하지 않아요”라 13세 이상 권장
13. **6. 앱 콘텐츠 · 타겟층 및 콘텐츠 › 아동의 관심을 끌 수 있나요?** — 명화 · 그림 위주의 화면이 아동 대상으로 보일 수 있다고 판단되면 “예”로 바꾸고 이유를 적기
14. **7. 앱 콘텐츠 · 데이터 보안 › 필수 사용자 데이터 유형을 수집하거나 공유하나요?** — 아래 표의 판단(특히 말해 보기 음성 인식, Google 백업, Cloudflare 가 받는 IP)을 보고 최종 결정
15. **7. 앱 콘텐츠 · 데이터 보안 › 오디오 (복습 ‘소리 내 말해 보기’)** — 폰의 음성 인식 서비스(SpeechRecognizer)에 맡기고 EXTRA_PREFER_OFFLINE 으로 기기 안 처리를 요청. 앱은 소리를 저장 · 전송하지 않음 · 수집 아님 (확인 필요 — 인식 서비스가 자체 정책으로 처리할 수 있다고 방침에 적어 둠)
16. **7. 앱 콘텐츠 · 데이터 보안 › 앱 활동 · 진도 · 설정** — 기기 안 (letters.xml). Android 백업 · 기기 이전에만 포함 · 수집 아님 (확인 필요 — Android 백업을 수집으로 보지 않는 것이 일반적)
17. **7. 앱 콘텐츠 · 데이터 보안 › 네트워크 (낭독 · 그림 받기)** — Cloudflare R2 공개 주소에서 파일을 받기만 함. 앱이 보내는 개인 정보 없음. Cloudflare 가 IP · 요청 시각을 기술적으로 처리 · 수집 아님 (확인 필요)
18. **8. 앱 콘텐츠 · 포그라운드 서비스 권한 › 데이터 동기화 · 설명 (ko, 승인 문구)** — 앱은 받기 알림에서도 멈출 수 있어요(dl_bg). 승인 문구는 “받기 화면에서”만 말해요 — 그대로 둘지, “받기 화면과 알림에서”로 고칠지
19. **8. 앱 콘텐츠 · 포그라운드 서비스 권한 › 메모** — 영상은 미디어 재생 · 데이터 동기화 두 부분으로 나눠 두 링크로 내도 돼요. 시험 APK 는 debug 빌드라도 동작은 같아요 (확인 필요: 영상 올릴 곳 · 공개 범위).
20. **10. 출시 · 비공개 테스트 › 테스터** — 테스터 명단 · 방식 (이메일 목록 / Google 그룹)
21. **10. 출시 · 비공개 테스트 › 프로덕션 신청 조건** — 개발자 계정 종류(개인/조직)와 만든 날짜, 조건 숫자는 신청 시점의 Play Console 안내로 다시 확인
22. **10. 출시 · 비공개 테스트 › 국가 / 지역** — 배포 국가
