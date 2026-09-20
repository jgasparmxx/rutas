package com.gaspar.rutassendero.utils

import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

/**
 * Encapsula la lógica para solicitar fotos a la cámara nativa de Android,
 * aliviando la carga del MainActivity.
 */
class CameraHelper(
    private val activity: ComponentActivity,
    private val onResult: (Boolean, File?) -> Unit
) {
    var photoFile: File? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        onResult(success, photoFile)
    }

    fun launch(file: File) {
        photoFile = file
        try {
            val uri = FileProvider.getUriForFile(
                activity,
                "com.gaspar.rutassendero.fileprovider",
                file
            )
            launcher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(activity, "Error al abrir la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
