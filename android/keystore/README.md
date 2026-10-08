# 서명 키

- `sideload.jks`: 내 폰에 직접 설치하는 시험용 APK 의 고정 키 (비밀번호 `android`, 별칭 `sideload`). 비밀이 아닌 시험 키라 저장소에 둬요.
  늘 같은 키로 서명되므로 새 APK 를 받아 그대로 덮어 설치할 수 있어요.
- Play 업로드 키는 이것과 다른 키로, 출시를 준비할 때 만들어 GitHub Secrets 에만 둬요.
