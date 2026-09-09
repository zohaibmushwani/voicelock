package com.example.voicelock.speakerid.embedding

import android.content.Context
import com.example.voicelock.speakerid.VoiceLockLog
import java.io.File

/** Makes packaged ONNX assets available as filesystem files required by ONNX Runtime. */
object ModelAssetInstaller {
    fun install(context: Context, assetName: String): File {
        val directory = File(context.noBackupFilesDir, "models").apply { mkdirs() }
        val target = File(directory, assetName)
        if (target.length() > 0L) {
            VoiceLockLog.info("Model ready from private storage: $assetName")
            return target
        }
        VoiceLockLog.info("Installing packaged model: $assetName")
        val temporary = File(directory, "$assetName.download")
        context.assets.open("models/$assetName").use { input -> temporary.outputStream().use(input::copyTo) }
        check(temporary.length() > 0L) { "Model asset is empty: $assetName" }
        check(temporary.renameTo(target)) { "Unable to install model asset: $assetName" }
        VoiceLockLog.info("Model installed: $assetName (${target.length() / 1_048_576} MiB)")
        return target
    }

    fun embeddingModel(context: Context): File = install(context, "voxceleb_ECAPA512_LM.onnx")
}
