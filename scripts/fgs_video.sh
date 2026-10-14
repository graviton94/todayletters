#!/usr/bin/env bash
# Play Console 포그라운드 서비스 신고용 시연 영상 (영어 화면 · 소리 없음). .github/workflows/fgs-video.yml 이 에뮬레이터에서 돌린다.
#   fgs_media_playback.mp4 : 앱 열기 → 편지 열기 → 자동 낭독 → 홈 → 알림판의 재생 알림 → 알림에서 멈춤
#   fgs_data_sync.mp4      : 설정 › 모든 장 받기 (사용자가 확인) → 진행 → 최근 앱에서 밀어 닫기 → 알림이 계속 → 알림의 Stop
# debug 빌드만 읽는 tl.* 실행 옵션으로 처음 소개 · 튜토리얼을 건너뛴다 (DevHooks.kt). 화면은 uiautomator 로 글자를 찾아 누른다.
set -u
P=io.github.graviton94.todayletters
OUT=${1:-fgs}
mkdir -p "$OUT"
LOG="$OUT/steps.txt"
: > "$LOG"
log() { echo "[$(date +%T)] $*" | tee -a "$LOG"; }

# uiautomator 로 지금 화면을 받아 정규식(글자 · 설명)에 맞는 첫 칸의 가운데를 찍는다. 못 찾으면 1.
# 사용: find_xy '정규식' → "x y"
find_xy() {
  timeout 15 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  timeout 10 adb exec-out cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null || return 1
  python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
pat = re.compile(sys.argv[1])
try:
    root = ET.parse(sys.argv[2]).getroot()
except Exception:
    sys.exit(1)
for n in root.iter("node"):
    for k in ("text", "content-desc", "resource-id"):
        v = n.get(k) or ""
        if v and pat.search(v):
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
            if not m: continue
            x1, y1, x2, y2 = map(int, m.groups())
            if x2 <= x1 or y2 <= y1: continue
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
PY
}
# tap '정규식' [기다릴 초] [화면 아래로 밀어 찾기 0/1]
tap() {
  local re="$1" wait="${2:-15}" scroll="${3:-0}" end=$((SECONDS + ${2:-15})) xy
  while [ $SECONDS -lt $end ]; do
    if xy=$(find_xy "$re"); then
      set -- $xy
      if [ "$2" -gt 2250 ] || [ "$2" -lt 60 ]; then adb shell input swipe 540 1700 540 900 400; sleep 1; continue; fi
      adb shell input tap "$1" "$2"; log "tap '$re' at $1,$2"; return 0
    fi
    [ "$scroll" = 1 ] && adb shell input swipe 540 1700 540 900 400
    sleep 1
  done
  log "NOT FOUND: '$re'"; cp "$OUT/ui.xml" "$OUT/notfound_$(echo "$re" | tr -cd 'A-Za-z0-9' | head -c 20).xml" 2>/dev/null
  return 1
}
snap() { timeout 20 adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null; }
rec_start() { adb shell rm -f /sdcard/$1.mp4; adb shell screenrecord --time-limit 170 --bit-rate 6000000 --size 720x1600 /sdcard/$1.mp4 & REC=$!; sleep 1.5; log "record $1"; }
rec_stop() {
  sleep 1.5
  adb shell pkill -INT screenrecord >/dev/null 2>&1 || adb shell killall -2 screenrecord >/dev/null 2>&1
  wait $REC 2>/dev/null; sleep 2
  adb pull /sdcard/$1.mp4 "$OUT/$1.mp4" >/dev/null 2>&1; ls -la "$OUT/$1.mp4" | tee -a "$LOG"
}
launch() { adb shell am start -W -n $P/.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$@" >/dev/null; }
# 앱 열기 → 입구 → 서재 → 빈센트 (오늘)
enter_vincent() {
  launch; sleep 2
  tap 'Tap to enter' 15; sleep 2.5
  tap 'Vincent → ' 10; sleep 3.5
}

# ── 준비 (녹화 안 함) ─────────────────────────────
adb shell settings put global hide_error_dialogs 1
adb shell cmd locale set-app-locales $P --locales en-US 2>/dev/null
adb shell pm grant $P android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell settings put secure show_ime_with_hard_keyboard 0
adb shell cmd notification set_dnd off 2>/dev/null
launch --ez tl.ready true --ez tl.unlock true; sleep 12
adb shell am force-stop $P
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell cmd statusbar collapse; sleep 2

# ── 1. 미디어 재생 ────────────────────────────────
rec_start fgs_media_playback
sleep 1
enter_vincent
tap '^Open the letter$' 10; sleep 3
# 도착 화면: 그림 · 첫 줄을 빈센트 목소리로 쓴 뒤 ‘Read the letter’
tap '^Read the letter$' 25 || { adb shell input tap 540 1200; sleep 1; tap '^Read the letter$' 8; }
sleep 7; snap m1_room
adb shell input keyevent KEYCODE_HOME; sleep 3.5
adb shell cmd statusbar expand-notifications; sleep 4.5; snap m2_shade
tap '^Pause$' 6 || tap 'actionPlayPause' 4; sleep 3.5; snap m3_paused
tap '^Play$' 4 && { sleep 3; tap '^Pause$' 4; sleep 2; }
adb shell cmd statusbar collapse; sleep 1.5
rec_stop fgs_media_playback
adb shell am force-stop $P; sleep 1

# ── 2. 데이터 동기화 (미리 받기) ──────────────────
adb shell input keyevent KEYCODE_HOME; adb shell cmd statusbar collapse; sleep 1
rec_start fgs_data_sync
sleep 1
enter_vincent
tap '^Settings$' 8; sleep 2
tap '^Download all chapters' 25 1; sleep 2.5
tap '^Download · ' 8; sleep 6; snap d1_progress
# 최근 앱에서 밀어 닫기 (사용자가 앱을 닫는 것과 같게)
adb shell input keyevent KEYCODE_APP_SWITCH; sleep 2.5; snap d2_recents
adb shell input swipe 540 1300 540 150 250; sleep 2; snap d3_closed
adb shell input keyevent KEYCODE_HOME; sleep 2
log "process after close: $(adb shell pidof $P)"
adb shell cmd statusbar expand-notifications; sleep 9; snap d4_shade
tap '^(Stop|STOP)$' 6; sleep 3; snap d5_stopped
adb shell cmd statusbar collapse; sleep 1.5
rec_stop fgs_data_sync

adb shell dumpsys activity services $P | grep -iE "ServiceRecord|isForeground" > "$OUT/services.txt" 2>/dev/null || true
adb logcat -d -s AndroidRuntime:E > "$OUT/crash.txt" 2>/dev/null || true
ls -la "$OUT"
