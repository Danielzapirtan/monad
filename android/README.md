# MONAD Native Android

This is the true-native Android implementation of the supported MONAD
workflows. It does not start Flask, require a browser, or use the desktop
Python runtime.

## Current scope

- Native Compose UI with Morphix and Diarix workspaces
- Android Storage Access Framework file import and native share actions
- TXT, Markdown, HTML, DOCX text extraction, and EPUB text extraction
- Plain-text/PDF export and PDF merge using Android `PdfRenderer`/`PdfDocument`
- Conversion to TXT, Markdown, HTML, or PDF; heading split; ZIP export
- Local heading-based outline generation and native Media3 audio/video preview
- Explicit exclusions: AZW3, YouTube, and pyannote

The document implementation intentionally follows Morphix's documented
structure-level behavior; it does not promise pixel-perfect preservation of
images, tables, fonts, or layout.

## Media engine boundary

The UI and workflow model are native, but imported-file transcription is not
silently delegated to Android's live speech recognizer. `TranscriptionEngine`
is the seam for adding a bundled Whisper-compatible runtime (for example
whisper.cpp or Sherpa-ONNX) and model download/selection. Until that engine is
added, the app reports this requirement explicitly rather than returning a
misleading transcript.

This is intentional: Android's speech service accepts microphone input, not
an arbitrary imported media URI. A production APK that must transcribe files
offline needs a bundled Whisper-compatible native/ONNX model runtime and model
assets; adding a fake fallback would violate the behavior of Diarix.

## Build

Open `android/` in Android Studio and run the `app` configuration. A Gradle
wrapper is not checked in yet because this environment has no Android SDK or
Gradle installation to generate and validate one.
