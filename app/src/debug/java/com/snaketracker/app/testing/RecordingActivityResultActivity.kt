package com.snaketracker.app.testing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Debug-only host for instrumented Compose tests that must observe what the
 * real `ActivityResultRegistry` launch pipeline produces: it records the
 * intents `startActivityForResult` receives instead of delegating to the
 * system, so tests can assert the production contract intent (action,
 * category, mime type) without opening any real system UI. Declared in the
 * debug variant manifest (see `app/src/debug/AndroidManifest.xml`) because an
 * activity declared only in the androidTest manifest would resolve to the
 * separate `.test` process.
 */
class RecordingActivityResultActivity : ComponentActivity() {
    val launchedIntents = mutableListOf<Intent>()

    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        launchedIntents += intent
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        launchedIntents += intent
    }
}
