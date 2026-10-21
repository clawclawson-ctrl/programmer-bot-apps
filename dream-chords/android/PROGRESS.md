# Dream Chords Android — PROGRESS
2026-10-19: v1 debug APK at /workspace/android/dreamchords-v1.apk (848 KB), package com.relaximus.dreamchords, minSdk 26, targetSdk 36.
- Pure Java WebView wrapper with no AndroidX libraries. assets/dream-chords.html is a copy of /workspace/dreamchords/dream-chords.html; re-copy it after web edits.
- It's served from https://appassets.androidplatform.net (a secure context, so WebCodecs works). Navigation away is blocked, and file and content access are off.
- Autoplay is allowed after a tap, the screen stays on while playing, and the app is portrait only.
- DCNative bridge: setKeepOn, begin/write (base64 chunks into the cache), save (MediaStore Movies/DreamChords), muxSave, shareLast (share sheet).
- muxSave takes the video-only H.264 MP4 made by JS plus 16-bit PCM, encodes AAC-LC 192k natively with MediaCodec, and joins them with MediaMuxer. This gives Gallery/X-friendly MP4s and doesn't depend on WebView AAC.
- Rec (live MediaRecorder) also saves through the bridge (mp4 if supported, else webm).
- Permissions: WRITE_EXTERNAL_STORAGE only up to API 28 (legacy Android 8-9); none on 10+.
- Build: `. ../env.sh && ./gradlew assembleDebug`. The foojay plugin was removed from settings. JDK 21 was reinstalled via apt on 2026-10-19 (it had gone missing). NEVER run the emulator.
- Not tested on a device: the native mux/save/share code compiled but has not run. The JS side was tested headless with a fake bridge.
2026-10-20: v2 APK (versionCode 2, versionName 1.1) at /workspace/android/dreamchords-v2.apk (851 KB). It bundles web v6 (voices, instruments, layers, drone) and uses the same debug signing key as v1, so it installs as an update.
2026-10-20: v3 APK (versionCode 3, versionName 1.2) at /workspace/android/dreamchords/dreamchords-v3.apk (+ copy in /workspace/android). Bundles web Math Beat (drums in offline render -> PCM -> native AAC mux). Same key as v1/v2 (cert sha256 4517ccb6...). Release dreamchords-v3.
2026-10-20: v4 APK (versionCode 4, 1.3) dreamchords-v4.apk: web percussion sounds (kits, per-lane sounds, tap preview). Same key. Release dreamchords-v4.
2026-10-21: v5 APK (versionCode 5, 1.4) dreamchords-v5.apk: extra dreamy progressions, 12 new instruments, colors. Same key 4517ccb6. Release dreamchords-v5.
