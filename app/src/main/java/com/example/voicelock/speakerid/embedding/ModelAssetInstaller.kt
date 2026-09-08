package com.example.voicelock.speakerid.embedding

import android.content.Context
import java.io.File

/** Makes packaged ONNX assets available as filesystem files required by ONNX Runtime. */
object ModelAssetInstaller {
    fun install(context: Context, assetName: String): File {
        val directory = File(context.noBackupFilesDir, "models").apply { mkdirs() }
        val target = File(directory, assetName)
        if (target.length() > 0L) return target
        val temporary = File(directory, "$assetName.download")
        context.assets.open("models/$assetName").use { input -> temporary.outputStream().use(input::copyTo) }
        check(temporary.length() > 0L) { "Model asset is empty: $assetName" }
        check(temporary.renameTo(target)) { "Unable to install model asset: $assetName" }
        return target
    }

    fun embeddingModel(context: Context): File = install(context, "voxceleb_ECAPA512_LM.onnx")
    fun vadModel(context: Context): File = install(context, "silero_vad_v6.2.1.onnx")
}
