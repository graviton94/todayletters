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
4. 앱 콘텐츠 › 포그라운드 서비스 신고: 미디어 재생 (편지 낭독) · 데이터 동기화 (낭독 미리 받기). 문구는 배포 준비 시안 ⑤, 시연 영상은 시험 APK 로 짧게.

## 5. 앱 밖 파일 (R2) 은 덮어쓰지 않아요

앱은 장마다 파일 크기를 `assets/letters/<작품>/remote.json` 에 갖고 있다가, 받은 크기로 다 받았는지 봐요 (`data/Downloads.kt`).
그래서 R2 에 이미 올린 파일을 같은 경로에 다른 내용으로 다시 올리면, 아직 업데이트하지 않은 폰은 받기에 계속 실패하고 이미 받은 폰은 옛 파일을 그대로 써요.

- 낭독 · 그림 · 곡을 고치면 **새 이름**으로 둬요 (예: `l3_m2_fr.m4a` → `l3_m2_fr.v2.m4a`, 그림은 `…-v2.webp`). 편지 데이터가 새 이름을 가리키게 하고 `scripts/generate.py` 를 돌리면 `remote.json` 이 따라와요.
- 옛 파일은 R2 에 그대로 둬요 (옛 버전 앱이 계속 받을 수 있게).
- `narration-r2.yml` 은 올리기 전에 `scripts/r2_guard.py` 로 같은 경로의 크기 · 내용을 비교하고, 다르면 멈춰요.
