package dev.stade.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual val isBackgroundRemovalSupported: Boolean = true

actual suspend fun removeImageBackground(bytes: ByteArray): ByteArray? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val options = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .build()
    val segmenter = SubjectSegmentation.getClient(options)
    val inputImage = InputImage.fromBitmap(bitmap, 0)
    val foreground = runCatching {
        awaitTask(segmenter.process(inputImage)).foregroundBitmap
    }.getOrNull() ?: return null
    val out = ByteArrayOutputStream()
    foreground.compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
    task.addOnSuccessListener { result -> cont.resume(result) }
    task.addOnFailureListener { e -> cont.resumeWithException(e) }
}
