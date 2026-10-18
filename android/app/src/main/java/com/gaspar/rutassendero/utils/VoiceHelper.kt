package com.gaspar.rutassendero.utils

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale

/**
 * Encapsula la lógica para llamar al reconocedor de voz nativo de Android.
 */
class VoiceHelper(
    private val activity: ComponentActivity,
    private val onResult: (String?) -> Unit
) {
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenTexts = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!spokenTexts.isNullOrEmpty()) {
                val fullText = spokenTexts[0]
                onResult(fullText.trim())
            } else {
                onResult(null)
            }
        } else {
            onResult(null)
        }
    }

    fun launch() {
        Toast.makeText(activity, "Iniciando dictado... (Requiere internet sin voz offline)", Toast.LENGTH_LONG).show()
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Describa el punto de ruta por voz")
            }
            launcher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "El dispositivo no soporta reconocimiento de voz nativo.", Toast.LENGTH_SHORT).show()
        }
    }
}
