package com.monad.nativeapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface TranscriptionEngine {
    suspend fun transcribe(context: Context, media: Uri, language: String): String
}

/**
 * Browser-free engine seam. Android's SpeechRecognizer is a live-microphone
 * recognizer and cannot consume an imported media URI, so imported-file
 * transcription is intentionally explicit until a bundled Whisper/Sherpa
 * runtime is added.
 */
class AndroidSpeechTranscriptionEngine : TranscriptionEngine {
    override suspend fun transcribe(context: Context, media: Uri, language: String): String {
        error(
            "Imported-file transcription needs the bundled Whisper engine. " +
                "The Android speech service only accepts live microphone input."
        )
    }
}
