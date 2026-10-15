# 언어 미술관 · 출시 빌드 (Play 스토어용 AAB)

`CLAUDE.md` 의 순서대로, 사용자가 **“릴리즈하자”** 고 할 때만 돌려요. 그 전에는 빌드 워크플로를 돌리지 않아요.

| 워크플로 | 무엇 | 서명 |
|---|---|---|
| `android.yml` | 푸시마다 컴파일 확인 · debug APK | 저장소의 시험 키 (`sideload`) |
| `android-apk.yml` | 내 폰에 직접 설치할 release APK (`android-latest`) | 업로드 키 비밀값이 없으면 `sideload` |
| `android-aab.yml` | Play 스토어에 올릴 App Bundle (`android-aab-latest` · 실행의 Artifacts) | **업로드 키** (없으면 실패) |

`versionCode`: `android.yml` · `android-apk.yml` 은 실행 번호, `android-aab.yml` 은 **1000 + 실행 번호** (시험 APK 와 겹치지 않고 계속 커져요).
`versionName` 은 `android-aab.yml` 을 돌릴 때 넣는 `version` (예: 1.0.0).

## 1. 업로드 키 (한 번, GitHub 안에서)

todaybible · carpediem 과 같은 방식이에요. 키를 내 컴퓨터에서 만들지 않아요.

1. Settings › Secrets and variables › Actions 에 `ANDROID_UPLOAD_PASSPHRASE` (16자 이상) 를 직접 넣어요.
2. Actions › **Android signing key (one time)** › Run workflow.
   GitHub 안에서 키를 만들고, 그 암호로 GPG 잠금을 해서 `android/keystore/upload.jks.gpg` 로 커밋해요.
   잠금 풀린 키와 암호는 저장소 · 로그 · 대화 어디에도 남지 않아요 (로그에는 공개해도 되는 인증서 SHA-256 지문만).
3. 키스토어 암호와 키 암호는 이 비밀값 하나로 같아요. 별칭은 `upload`.

## 2. 빌드할 때

`android-aab.yml` 이 `ANDROID_UPLOAD_PASSPHRASE` 로 `.gpg` 를 풀어 실행기의 임시 폴더에만 두고
`ANDROID_UPLOAD_KEYSTORE` · `_STORE_PASSWORD` · `_KEY_ALIAS` · `_KEY_PASSWORD` 로 넘겨요. 끝나면 (실패해도) 풀린 키를 지워요.
`android/app/build.gradle.kts` 는 넷이 다 있을 때만 `upload` 서명을 쓰고, 없으면 `sideload` 키 (그래서 `android-apk.yml` 은 그대로).
AAB 가 `sideload` 키로 서명됐으면 실패시켜요. 결과: `.aab` 와 `mapping.txt` (Artifacts 14일) · Releases 의 `android-aab-latest`.

## 3. Play App Signing

- Play Console › 앱 만들기 → 첫 버전을 올릴 때 **Play App Signing** 에 등록돼요 (기본). 앱 서명 키는 Google 이 갖고, 우리는 **업로드 키**로 서명한 AAB 만 올려요.
- 처음 올린 AAB 의 서명 인증서가 업로드 키로 등록돼요. 그 뒤로는 같은 업로드 키로만 올릴 수 있어요.
- 업로드 키를 잃어버리거나 바꿔야 하면: **Android signing key** 를 `replace` 켜고 다시 돌린 뒤, Play Console › 설정 › 앱 무결성 › 앱 서명 › 업로드 키 재설정 요청 (새 키의 인증서 PEM 을 올림).
- 결제 · 서명 확인에 쓰는 SHA-256 은 Play Console › 앱 무결성 › 앱 서명 키 인증서에서 봐요 (스토어 설치본은 업로드 키가 아니라 이 키로 서명돼요).
- 스토어 설치본과 `sideload` 로 서명한 시험 APK 는 서명이 달라서 서로 덮어 설치되지 않아요 (지우고 설치).

## 4. 올리기

1. Actions › **Android AAB** › Run workflow, `version` 입력 (사용자가 “릴리즈하자” 고 한 뒤).
2. 실행이 끝나면 Artifacts 의 `TodayLetters-aab` 또는 Releases 의 `android-aab-latest` 에서 `.aab` 를 받아요.
3. Play Console › 테스트(내부 · 비공개) 또는 프로덕션 › 새 버전 만들기 → `.aab` 업로드 → 출시 노트.
4. 앱 콘텐츠 › 포그라운드 서비스 신고: 미디어 재생 (편지 낭독) · 데이터 동기화 (낭독 미리 받기). 문구 · 영상은 아래 4-1.

### 4-1. 포그라운드 서비스 신고 (문구 · 시연 영상)

Play Console › 앱 콘텐츠 › **포그라운드 서비스 권한**. 매니페스트: `NarrationService` = mediaPlayback, `DownloadService` = dataSync. 두 유형 모두 신고하고 유형마다 영상 링크를 넣는다.
문구는 승인된 그대로 `design/store/play/listing.md` 8장에 있다 (아래는 같은 글).

| 유형 | 설명 (en) | 설명 (ko) |
|---|---|---|
| 미디어 재생 `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Continues playing the narration of a letter while the screen is off or the user is in another app. Playback is always started by the user, who can pause, skip and stop it from the notification. | 편지 낭독을 화면이 꺼지거나 다른 앱을 쓰는 중에도 이어서 들려줍니다. 재생은 사용자가 직접 시작하고, 알림에서 멈추고 넘기고 끌 수 있습니다. |
| 데이터 동기화 `FOREGROUND_SERVICE_DATA_SYNC` | Downloads audio files to the device so they can be played without an internet connection. The download is always started by the user, shows its progress in a notification for its whole duration and continues if the app is closed. The user can stop it at any time from the download screen. | 오디오 파일을, 인터넷 없이 들을 수 있도록 기기에 내려받습니다. 받기는 사용자가 직접 시작하고, 진행 상황을 알림으로 계속 보여 주며, 앱을 닫아도 이어집니다. 받기 화면에서 언제든 멈출 수 있습니다. |

**시연 영상** (영어 화면 · 소리 없음, 에뮬레이터 녹화): Actions › **FGS demo video** (`fgs-video.yml`) 를 직접 돌리면 debug APK 를 에뮬레이터에서 열어 `scripts/fgs_video.sh` 로 두 장면을 녹화하고, `commit` 을 켜 두면 이 가지에 커밋한다 (Artifacts `fgs-video` 에도 있음).

- `design/store/play/fgs_media_playback.mp4` — 앱 열기 → 빈센트의 편지 열기 → 낭독 시작 → 홈으로 나가도 낭독 → 알림판의 재생 알림 (⏮ ⏸ ⏭) → 알림에서 멈춤.
- `design/store/play/fgs_data_sync.mp4` — 설정 › 저장 공간 › 모든 장 받기 → 확인 → 진행 → 최근 앱에서 밀어 닫기 → 알림이 계속 진행 → 알림의 Stop.

> **상태 (2026-10-14): 영상은 아직 없다.** 7번 돌렸는데 매번 입구(“Tap to enter”)에서 서재로 들어가고 3~4초 뒤 에뮬레이터(37.2.12 · API 34 google_apis · swiftshader)가 통째로 멈췄다.
> 소리 장치 유무 · PulseAudio · wav · `-gpu guest` · 무음 재생 유지로는 바뀌지 않았다 (소리 대기 전환 탓은 아님). 실행 기록은 `fgs-review-<번호>` 가지 (`steps.txt` · `logcat_live.txt`).
> 다음에 해 볼 것: 서재를 거치지 않고 편지로 바로 여는 debug 실행 옵션, 에뮬레이터 빌드 고정(`emulator-build`) 또는 API 33/35 이미지, 녹화 없이 같은 경로를 먼저 시험. 급하면 실기기 화면 녹화로 같은 장면을 찍는다.

영상은 YouTube **일부 공개** 또는 Google 드라이브 공유 링크로 올려 신고서에 붙인다. 녹화는 debug 빌드만 읽는 실행 옵션(`tl.ready` · `tl.unlock`, `DevHooks.kt`)으로 처음 소개 · 튜토리얼을 건너뛰고 모든 장을 연다 (release 빌드는 읽지 않음).

## 5. 앱 밖 파일 (R2) 은 덮어쓰지 않아요

앱은 장마다 파일 크기를 `assets/letters/<작품>/remote.json` 에 갖고 있다가, 받은 크기로 다 받았는지 봐요 (`data/Downloads.kt`).
그래서 R2 에 이미 올린 파일을 같은 경로에 다른 내용으로 다시 올리면, 아직 업데이트하지 않은 폰은 받기에 계속 실패하고 이미 받은 폰은 옛 파일을 그대로 써요.

- 낭독 · 그림 · 곡을 고치면 **새 이름**으로 둬요 (예: `l3_m2_fr.m4a` → `l3_m2_fr.v2.m4a`, 그림은 `…-v2.webp`). 편지 데이터가 새 이름을 가리키게 하고 `scripts/generate.py` 를 돌리면 `remote.json` 이 따라와요.
- 옛 파일은 R2 에 그대로 둬요 (옛 버전 앱이 계속 받을 수 있게).
- `narration-r2.yml` 은 올리기 전에 `scripts/r2_guard.py` 로 같은 경로의 크기 · 내용을 비교하고, 다르면 멈춰요.
- 빠진 소리 만들기: Actions › **Narration to R2** 를 `make = new` 로 돌리면 오늘의 회화 (`t<줄>_<언어>.m4a`, 편지 `talk.audio = "t"`; "them" 은 인물 목소리, "me" 는 배역표 `talk_me`) · 카드 낱말 `v_<언어>_<slug>` · 표현 `ph<n>_<언어>` 가운데 **저장소에 없는 것만** 만들어 이 가지에 커밋하고 올려요 (`scripts/narrate.py NARRATE_ONLY=new NARRATE_SKIP_EXISTING=1`). 회화 줄을 고치면 소리는 새 이름(`talk.audio` 를 `tb` 처럼)으로.
