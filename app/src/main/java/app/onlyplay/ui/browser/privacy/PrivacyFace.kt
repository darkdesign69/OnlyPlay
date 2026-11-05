package app.onlyplay.ui.browser.privacy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import org.tensorflow.lite.Interpreter
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Face unlock for Privacy. The front camera finds your face, a FaceNet model turns it into a list of
 * numbers, and that list is compared with the ones saved when you set face unlock up. A match plus one
 * blink (so a photo does not work) unlocks. Anything else says "Face not recognised" and the lock
 * (PIN, pattern or password) is used instead. Nothing leaves the phone.
 */
object FaceLock {
  private const val MODEL = "facenet.tflite"
  private const val INPUT = 160
  private const val DIM = 128

  /** How alike two faces must be (0 to 1). Higher is stricter. */
  const val MATCH = 0.62f

  const val ENROLL_SHOTS = 6

  @Volatile private var interpreter: Interpreter? = null

  fun available(context: Context): Boolean =
    try {
      context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) &&
        (context.assets.list("")?.contains(MODEL) == true)
    } catch (_: Exception) {
      false
    }

  private fun file(context: Context) = File(context.noBackupFilesDir, "face_template.bin")

  fun hasTemplate(context: Context): Boolean = file(context).exists()

  fun clear(context: Context) {
    file(context).delete()
  }

  fun save(
    context: Context,
    list: List<FloatArray>,
  ) {
    DataOutputStream(file(context).outputStream().buffered()).use { out ->
      out.writeInt(list.size)
      list.forEach { v -> v.forEach { out.writeFloat(it) } }
    }
  }

  private fun load(context: Context): List<FloatArray> =
    try {
      DataInputStream(file(context).inputStream().buffered()).use { inp ->
        val n = inp.readInt()
        List(n) { FloatArray(DIM) { inp.readFloat() } }
      }
    } catch (_: Exception) {
      emptyList()
    }

  @Synchronized
  private fun model(context: Context): Interpreter {
    interpreter?.let { return it }
    val bytes = context.applicationContext.assets.open(MODEL).use { it.readBytes() }
    val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
    buf.put(bytes)
    buf.rewind()
    val ip = Interpreter(buf, Interpreter.Options().apply { numThreads = 4 })
    interpreter = ip
    return ip
  }

  /** Turns a cropped face into a unit-length list of 128 numbers. */
  @Synchronized
  fun embed(
    context: Context,
    face: Bitmap,
  ): FloatArray {
    val scaled = Bitmap.createScaledBitmap(face, INPUT, INPUT, true)
    val px = IntArray(INPUT * INPUT)
    scaled.getPixels(px, 0, INPUT, 0, 0, INPUT, INPUT)
    val v = FloatArray(INPUT * INPUT * 3)
    var k = 0
    for (c in px) {
      v[k++] = ((c shr 16) and 0xFF).toFloat()
      v[k++] = ((c shr 8) and 0xFF).toFloat()
      v[k++] = (c and 0xFF).toFloat()
    }
    var mean = 0f
    for (x in v) mean += x
    mean /= v.size
    var varSum = 0f
    for (x in v) varSum += (x - mean) * (x - mean)
    val std = max(sqrt(varSum / v.size), 1f / sqrt(v.size.toFloat()))
    val input = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder())
    for (x in v) input.putFloat((x - mean) / std)
    input.rewind()
    val out = Array(1) { FloatArray(DIM) }
    model(context).run(input, out)
    val e = out[0]
    var norm = 0f
    for (x in e) norm += x * x
    norm = sqrt(norm).coerceAtLeast(1e-6f)
    for (i in e.indices) e[i] = e[i] / norm
    return e
  }

  /** How alike this face is to the saved ones: the average of the best three matches. */
  fun score(
    context: Context,
    face: FloatArray,
  ): Float {
    val saved = load(context)
    if (saved.isEmpty()) return 0f
    val sims =
      saved
        .map { s ->
          var d = 0f
          for (i in 0 until DIM) d += s[i] * face[i]
          d
        }.sortedDescending()
        .take(3)
    return sims.average().toFloat()
  }
}

private val mainHandler = Handler(Looper.getMainLooper())

private fun upright(
  src: Bitmap,
  degrees: Int,
): Bitmap {
  if (degrees == 0) return src
  val m = Matrix()
  m.postRotate(degrees.toFloat())
  return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

private fun crop(
  src: Bitmap,
  box: Rect,
): Bitmap? {
  val l = box.left.coerceIn(0, src.width - 1)
  val t = box.top.coerceIn(0, src.height - 1)
  val r = box.right.coerceIn(l + 1, src.width)
  val b = box.bottom.coerceIn(t + 1, src.height)
  if (r - l < 40 || b - t < 40) return null
  return Bitmap.createBitmap(src, l, t, r - l, b - t)
}

/**
 * Camera check shown in place (a circle with a status line under it). With [enroll] it saves your
 * face (a few shots); otherwise it checks the person in front of the camera against the saved face.
 * [onSuccess] runs when it worked; [onFallback] when the person gives up (to use the PIN, pattern or
 * password instead).
 */
@Composable
fun FaceScanPanel(
  enroll: Boolean,
  circle: Dp,
  fallbackLabel: String,
  onSuccess: () -> Unit,
  onFallback: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var granted by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
  }
  var asked by remember { mutableStateOf(false) }
  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
  LaunchedEffect(Unit) {
    if (!granted && !asked) {
      asked = true
      permission.launch(Manifest.permission.CAMERA)
    }
  }

  var attempt by remember { mutableIntStateOf(0) }
  var status by remember { mutableStateOf(if (enroll) "Look at the camera" else "Look at the camera and blink once") }
  var failed by remember { mutableStateOf(false) }
  var progress by remember { mutableIntStateOf(0) }
  var done by remember { mutableStateOf(false) }

  val previewView =
    remember {
      PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      }
    }

  DisposableEffect(granted, attempt) {
    if (!granted) return@DisposableEffect onDispose { }
    failed = false
    progress = 0
    status = if (enroll) "Look at the camera" else "Look at the camera and blink once"
    val executor = Executors.newSingleThreadExecutor()
    val detector =
      FaceDetection.getClient(
        FaceDetectorOptions
          .Builder()
          .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
          .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
          .setMinFaceSize(0.25f)
          .build(),
      )
    val providerFuture = ProcessCameraProvider.getInstance(context)
    var provider: ProcessCameraProvider? = null
    val shots = ArrayList<FloatArray>()
    var lastShot = 0L
    var matched = false
    var tries = 0
    var openSeen = false
    var closedSeen = false
    var blinked = false
    val startedAt = System.currentTimeMillis()
    var finished = false

    fun finish(ok: Boolean, message: String) {
      if (finished) return
      finished = true
      mainHandler.post {
        if (ok) {
          done = true
        } else {
          failed = true
          status = message
        }
      }
    }

    providerFuture.addListener(
      {
        try {
          val p = providerFuture.get()
          provider = p
          val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
          val analysis =
            ImageAnalysis
              .Builder()
              .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
              .build()
          analysis.setAnalyzer(executor) { proxy ->
            try {
              if (finished) return@setAnalyzer
              if (!enroll && System.currentTimeMillis() - startedAt > 10_000) {
                finish(false, if (matched) "Blink not detected. Try again." else "Face not recognised")
                return@setAnalyzer
              }
              val frame = upright(proxy.toBitmap(), proxy.imageInfo.rotationDegrees)
              val faces = Tasks.await(detector.process(InputImage.fromBitmap(frame, 0)))
              if (faces.size != 1) {
                if (faces.size > 1) mainHandler.post { status = "Only one face, please" }
                return@setAnalyzer
              }
              val face = faces[0]
              if (abs(face.headEulerAngleY) > 25f || abs(face.headEulerAngleX) > 25f) {
                mainHandler.post { status = "Face the camera straight on" }
                return@setAnalyzer
              }
              val l = face.leftEyeOpenProbability
              val r = face.rightEyeOpenProbability
              val eyes = if (l != null && r != null) (l + r) / 2f else null
              if (enroll) {
                if (eyes == null || eyes < 0.5f) return@setAnalyzer
                val now = System.currentTimeMillis()
                if (now - lastShot < 450) return@setAnalyzer
                val c = crop(frame, face.boundingBox) ?: return@setAnalyzer
                shots.add(FaceLock.embed(context, c))
                lastShot = now
                val n = shots.size
                mainHandler.post {
                  progress = n
                  status = "Scanning… $n of ${FaceLock.ENROLL_SHOTS}"
                }
                if (n >= FaceLock.ENROLL_SHOTS) {
                  FaceLock.save(context, shots)
                  finish(true, "")
                }
              } else {
                if (eyes != null) {
                  if (eyes > 0.7f) {
                    if (closedSeen) blinked = true
                    openSeen = true
                  } else if (eyes < 0.25f && openSeen) {
                    closedSeen = true
                  }
                }
                if (!matched && (eyes == null || eyes > 0.5f)) {
                  val c = crop(frame, face.boundingBox) ?: return@setAnalyzer
                  val s = FaceLock.score(context, FaceLock.embed(context, c))
                  if (s >= FaceLock.MATCH) matched = true else tries++
                  if (!matched && tries >= 12) {
                    finish(false, "Face not recognised")
                    return@setAnalyzer
                  }
                }
                if (matched && blinked) finish(true, "")
              }
            } catch (_: Exception) {
              // skip this frame
            } finally {
              proxy.close()
            }
          }
          p.unbindAll()
          p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        } catch (_: Exception) {
          finish(false, "Couldn't open the camera")
        }
      },
      ContextCompat.getMainExecutor(context),
    )
    onDispose {
      finished = true
      try {
        provider?.unbindAll()
      } catch (_: Exception) {
      }
      executor.shutdown()
      detector.close()
    }
  }

  LaunchedEffect(done) {
    if (done) {
      delay(250)
      onSuccess()
    }
  }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier =
        Modifier
          .size(circle)
          .clip(CircleShape)
          .background(Color(0xFF1B1B1B)),
      contentAlignment = Alignment.Center,
    ) {
      if (granted) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
      }
    }
    Spacer(Modifier.height(10.dp))
    Text(
      text = if (!granted) "Camera permission is needed for face unlock" else if (done) "Done" else status,
      style = MaterialTheme.typography.titleSmall,
      color = if (failed || !granted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )
    if (!granted && asked) {
      TextButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
      if (failed) {
        TextButton(onClick = { attempt++ }) { Text("Try again") }
      }
      if (failed || !granted || enroll) {
        TextButton(onClick = onFallback) { Text(if (enroll) "Cancel" else fallbackLabel) }
      }
    }
  }
}

/** Face set-up in its own solid panel (it never lets the app background show through). */
@Composable
fun FaceScanDialog(
  enroll: Boolean,
  fallbackLabel: String,
  onSuccess: () -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f)) {
      Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = if (enroll) "Set up face unlock" else "Face unlock",
          style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(24.dp))
        FaceScanPanel(
          enroll = enroll,
          circle = 280.dp,
          fallbackLabel = fallbackLabel,
          onSuccess = onSuccess,
          onFallback = onDismiss,
        )
      }
    }
  }
}
