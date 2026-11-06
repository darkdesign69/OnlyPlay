package app.onlyplay.ui.browser.privacy

import android.content.Context
import android.hardware.biometrics.BiometricManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import app.onlyplay.ui.theme.AlertDialog
import androidx.compose.material3.OutlinedTextField
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.biometrics.BiometricPrompt
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import app.onlyplay.preferences.BrowserPreferences
import app.onlyplay.preferences.preference.collectAsState
import app.onlyplay.presentation.Screen
import app.onlyplay.ui.player.PlayerActivity
import app.onlyplay.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File

/**
 * Privacy: videos you hide live here. They are removed from your folders, the gallery and other
 * apps, and can only be played from this screen after unlocking (PIN, pattern or password, or face).
 */
@Serializable
object PrivacyScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val preferences = koinInject<BrowserPreferences>()

    val pinHash by preferences.privacyPinHash.collectAsState()
    val faceOn by preferences.privacyFingerprint.collectAsState()
    val lockType by preferences.privacyLockType.collectAsState()
    val gridView by preferences.privacyGrid.collectAsState()
    val sortKey by preferences.privacySort.collectAsState()
    val sortDesc by preferences.privacyDesc.collectAsState()
    val gridColumns by preferences.privacyColumns.collectAsState()
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var hidden by remember { mutableStateOf<List<HiddenVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val canFace = remember { FaceLock.available(context) }
    var faceScan by rememberSaveable { mutableStateOf(false) }
    var faceEnroll by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<HiddenVideo?>(null) }
    val selecting = selectedIds.isNotEmpty()
    BackHandler(enabled = selecting) { selectedIds = emptySet() }

    LaunchedEffect(unlocked) {
      if (unlocked) {
        hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) }
      }
      loading = false
    }

    var durations by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(hidden, sortKey) {
      if (sortKey == 3) {
        durations =
          withContext(Dispatchers.IO) {
            hidden.associate { v -> v.id to (durations[v.id] ?: readDurationMs(v.hiddenPath)) }
          }
      }
    }

    val sorted =
      remember(hidden, sortKey, sortDesc, durations) {
        val base =
          when (sortKey) {
            0 -> hidden.sortedBy { it.name.lowercase() }
            1 -> hidden.sortedBy { it.size }
            3 -> hidden.sortedBy { durations[it.id] ?: 0L }
            4 -> hidden.sortedBy { (File(it.originalPath).parent ?: "").lowercase() }
            5 -> hidden.sortedBy { it.name.substringAfterLast('.', "").lowercase() }
            else -> hidden.sortedBy { File(it.hiddenPath).lastModified() }
          }
        if (sortDesc) base.reversed() else base
      }

    fun reload() {
      scope.launch { hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) } }
    }

    fun toggle(video: HiddenVideo) {
      selectedIds = if (video.id in selectedIds) selectedIds - video.id else selectedIds + video.id
    }

    val setupMode = pinHash.isEmpty()
    val videosMode = !setupMode && unlocked

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text =
                when {
                  setupMode -> "Set lock"
                  videosMode && selecting -> "${selectedIds.size} selected"
                  videosMode -> "Private Videos"
                  else -> "Privacy"
                },
            )
          },
          navigationIcon = {
            if (selecting) {
              IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Filled.Close, contentDescription = "Cancel selection") }
            } else {
              IconButton(onClick = { backstack.removeLastOrNull() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
              }
            }
          },
          actions = {
            if (videosMode && selecting) {
              IconButton(
                onClick = {
                  val chosen = hidden.filter { it.id in selectedIds }
                  selectedIds = emptySet()
                  scope.launch {
                    var ok = 0
                    chosen.forEach { if (PrivacyStore.unhide(context, it)) ok++ }
                    hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) }
                    Toast.makeText(context, "Restored $ok of ${chosen.size} video(s) to their folders", Toast.LENGTH_SHORT).show()
                  }
                },
              ) { Icon(Icons.Filled.LockOpen, contentDescription = "Unlock") }
              if (selectedIds.size == 1) {
                IconButton(onClick = { renameTarget = hidden.firstOrNull { it.id in selectedIds } }) {
                  Icon(Icons.Filled.Edit, contentDescription = "Rename")
                }
              }
              IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
            if (videosMode && !selecting) {
              IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.GridView, contentDescription = "View and sort options")
              }
            }
          },
        )
      },
      floatingActionButton = {
        if (videosMode && !selecting) {
          FloatingActionButton(onClick = { showImport = true }) { Icon(Icons.Filled.Add, contentDescription = "Import videos") }
        }
      },
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        when {
          setupMode ->
            SetLockContent(
              showFace = canFace,
              onCreate = { type, secret, useFace ->
                val salt = newPrivacySalt()
                preferences.privacyPinSalt.set(salt)
                preferences.privacyPinHash.set(hashPrivacyPin(salt, secret))
                preferences.privacyLockType.set(type)
                preferences.privacyFingerprint.set(false)
                unlocked = true
                if (useFace) faceEnroll = true
              },
            )
          !unlocked ->
            UnlockContent(
              type = lockType,
              faceEnabled = faceOn && canFace,
              onSubmit = { secret ->
                val ok = hashPrivacyPin(preferences.privacyPinSalt.get(), secret) == preferences.privacyPinHash.get()
                if (ok) unlocked = true
                ok
              },
              onFaceRequest = { faceScan = true },
              faceActive = faceScan,
              faceFallback = "Use ${lockName(lockType)} instead",
              onFaceDone = {
                faceScan = false
                unlocked = true
              },
              onFaceClose = { faceScan = false },
            )
          loading -> Text(text = "Loading…", modifier = Modifier.padding(32.dp))
          sorted.isEmpty() -> EmptyContent()
          gridView ->
            LazyVerticalGrid(
              columns = GridCells.Fixed(gridColumns),
              modifier = Modifier.fillMaxSize(),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              gridItems(sorted, key = { it.id }) { video ->
                HiddenVideoCard(
                  video = video,
                  selected = video.id in selectedIds,
                  onClick = { if (selecting) toggle(video) else playHidden(context, video) },
                  onLongClick = { toggle(video) },
                )
              }
            }
          else ->
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
              items(sorted, key = { it.id }) { video ->
                HiddenVideoRow(
                  video = video,
                  selected = video.id in selectedIds,
                  onClick = { if (selecting) toggle(video) else playHidden(context, video) },
                  onLongClick = { toggle(video) },
                )
              }
            }
        }
      }
    }

    if (menuOpen) {
      PrivacyOptionsDialog(
        gridView = gridView,
        columns = gridColumns,
        sortKey = sortKey,
        sortDesc = sortDesc,
        canFace = canFace,
        faceOn = faceOn,
        onGrid = { preferences.privacyGrid.set(it) },
        onColumns = { preferences.privacyColumns.set(it) },
        onSortKey = { preferences.privacySort.set(it) },
        onSortDesc = { preferences.privacyDesc.set(it) },
        onChangeLock = {
          menuOpen = false
          showChangePin = true
        },
        onToggleFace = {
          menuOpen = false
          if (faceOn) {
            preferences.privacyFingerprint.set(false)
            FaceLock.clear(context)
          } else {
            faceEnroll = true
          }
        },
        onDone = { menuOpen = false },
      )
    }
    if (faceEnroll) {
      FaceScanDialog(
        enroll = true,
        fallbackLabel = "Cancel",
        onSuccess = {
          preferences.privacyFingerprint.set(true)
          faceEnroll = false
        },
        onDismiss = { faceEnroll = false },
      )
    }
    if (showChangePin) {
      ChangeLockDialog(
        currentType = lockType,
        verify = { secret -> hashPrivacyPin(preferences.privacyPinSalt.get(), secret) == preferences.privacyPinHash.get() },
        onDismiss = { showChangePin = false },
        onNewLock = { type, secret ->
          val salt = newPrivacySalt()
          preferences.privacyPinSalt.set(salt)
          preferences.privacyPinHash.set(hashPrivacyPin(salt, secret))
          preferences.privacyLockType.set(type)
          showChangePin = false
          Toast.makeText(context, "Lock changed", Toast.LENGTH_SHORT).show()
        },
      )
    }
    if (confirmDelete) {
      val chosen = hidden.filter { it.id in selectedIds }
      AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${chosen.size} video(s)?") },
        text = { Text("They will be deleted from your phone for good. This can't be undone.") },
        confirmButton = {
          TextButton(
            onClick = {
              confirmDelete = false
              selectedIds = emptySet()
              scope.launch {
                val n = PrivacyStore.delete(context, chosen)
                hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) }
                Toast.makeText(context, "$n video(s) deleted", Toast.LENGTH_SHORT).show()
              }
            },
          ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
      )
    }
    renameTarget?.let { target ->
      var newName by remember(target.id) { mutableStateOf(target.name) }
      AlertDialog(
        onDismissRequest = { renameTarget = null },
        title = { Text("Rename") },
        text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
        confirmButton = {
          TextButton(
            onClick = {
              renameTarget = null
              selectedIds = emptySet()
              scope.launch {
                val ok = PrivacyStore.rename(context, target, newName)
                hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) }
                if (!ok) Toast.makeText(context, "Couldn't rename (name empty or already used)", Toast.LENGTH_SHORT).show()
              }
            },
          ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
      )
    }

    if (showImport) {
      ImportDialog(
        onDismiss = { showImport = false },
        onImport = { paths ->
          showImport = false
          scope.launch {
            val n = PrivacyStore.hide(context, paths)
            hidden = withContext(Dispatchers.IO) { PrivacyStore.load(context) }
            Toast.makeText(context, "$n video(s) moved to Private Videos", Toast.LENGTH_SHORT).show()
          }
        },
      )
    }
  }
}

private fun playHidden(
  context: Context,
  video: HiddenVideo,
) {
  val intent = Intent(Intent.ACTION_VIEW, Uri.fromFile(File(video.hiddenPath)))
  intent.setClass(context, PlayerActivity::class.java)
  intent.putExtra("internal_launch", true)
  intent.putExtra("privacy_video", true)
  intent.putExtra("title", video.name)
  context.startActivity(intent)
}

private fun readDurationMs(path: String): Long =
  runCatching {
    val r = MediaMetadataRetriever()
    try {
      r.setDataSource(path)
      r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } finally {
      r.release()
    }
  }.getOrDefault(0L)

/**
 * Full-screen lock shown over a hidden video in the player after the app was left or the screen was
 * turned off. The video stays paused underneath until the lock is opened again.
 */
@Composable
fun PrivacyUnlockOverlay(onUnlocked: () -> Unit) {
  val context = LocalContext.current
  val preferences = koinInject<BrowserPreferences>()
  val lockType by preferences.privacyLockType.collectAsState()
  val faceOn by preferences.privacyFingerprint.collectAsState()
  val canFace = remember { FaceLock.available(context) }
  var faceScan by remember { mutableStateOf(false) }
  Surface(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
      UnlockContent(
        type = lockType,
        faceEnabled = faceOn && canFace,
        heading = "Private video locked",
        onSubmit = { secret ->
          val ok = hashPrivacyPin(preferences.privacyPinSalt.get(), secret) == preferences.privacyPinHash.get()
          if (ok) onUnlocked()
          ok
        },
        onFaceRequest = { faceScan = true },
        faceActive = faceScan,
        faceFallback = "Use ${lockName(lockType)} instead",
        onFaceDone = {
          faceScan = false
          onUnlocked()
        },
        onFaceClose = { faceScan = false },
      )
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Face unlock (see PrivacyFace.kt)
// ---------------------------------------------------------------------------------------------

// ---------------------------------------------------------------------------------------------
// PIN screens (dots + number pad)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PinDots(
  count: Int,
  filled: Int,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
    repeat(count) { i ->
      Box(
        modifier =
          Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(if (i < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)),
      )
    }
  }
}

@Composable
private fun KeyCell(
  label: String,
  modifier: Modifier,
  onClick: () -> Unit,
) {
  Box(modifier = modifier.height(84.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
    Text(label, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Light)
  }
}

@Composable
private fun PinKeypad(
  onDigit: (Char) -> Unit,
  onBackspace: () -> Unit,
  leftSlot: @Composable () -> Unit = {},
) {
  Column(
    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).navigationBarsPadding(),
  ) {
    listOf("123", "456", "789").forEach { row ->
      Row(modifier = Modifier.fillMaxWidth()) {
        row.forEach { d -> KeyCell(d.toString(), Modifier.weight(1f)) { onDigit(d) } }
      }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.weight(1f).height(84.dp), contentAlignment = Alignment.Center) { leftSlot() }
      KeyCell("0", Modifier.weight(1f)) { onDigit('0') }
      Box(modifier = Modifier.weight(1f).height(84.dp).clickable(onClick = onBackspace), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", modifier = Modifier.size(34.dp))
      }
    }
  }
}

private const val LOCK_PIN = "pin"
private const val LOCK_PATTERN = "pattern"
private const val LOCK_PASSWORD = "password"

private fun lockName(type: String): String =
  when (type) {
    LOCK_PATTERN -> "pattern"
    LOCK_PASSWORD -> "password"
    else -> "PIN"
  }

@Composable
private fun PatternPad(
  onComplete: (String) -> Unit,
  modifier: Modifier = Modifier,
  error: Boolean = false,
) {
  val primary = MaterialTheme.colorScheme.primary
  val errorColor = MaterialTheme.colorScheme.error
  val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
  var boxSize by remember { mutableStateOf(IntSize.Zero) }
  var path by remember { mutableStateOf(listOf<Int>()) }
  var finger by remember { mutableStateOf<Offset?>(null) }
  var locked by remember { mutableStateOf(false) }
  val currentOnComplete by rememberUpdatedState(onComplete)

  LaunchedEffect(locked) {
    if (locked) {
      delay(600)
      path = emptyList()
      finger = null
      locked = false
    }
  }

  fun centerOf(i: Int): Offset {
    val cell = boxSize.width / 3f
    return Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
  }

  fun hit(p: Offset) {
    val cell = boxSize.width / 3f
    for (i in 0 until 9) {
      if (i in path) continue
      val c = centerOf(i)
      val dx = c.x - p.x
      val dy = c.y - p.y
      if (dx * dx + dy * dy < (cell * 0.3f) * (cell * 0.3f)) {
        path = path + i
        break
      }
    }
  }

  Canvas(
    modifier =
      modifier
        .aspectRatio(1f)
        .onSizeChanged { boxSize = it }
        .pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown()
            if (locked) return@awaitEachGesture
            path = emptyList()
            finger = down.position
            hit(down.position)
            drag(down.id) { change ->
              finger = change.position
              hit(change.position)
              change.consume()
            }
            finger = null
            if (path.isNotEmpty()) {
              locked = true
              currentOnComplete(path.joinToString(""))
            }
          }
        },
  ) {
    val color = if (error) errorColor else primary
    for (k in 0 until path.size - 1) {
      drawLine(color, centerOf(path[k]), centerOf(path[k + 1]), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
    }
    val f = finger
    if (f != null && path.isNotEmpty()) {
      drawLine(color, centerOf(path.last()), f, strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
    }
    for (i in 0 until 9) {
      val sel = i in path
      drawCircle(if (sel) color else idle, radius = if (sel) 14.dp.toPx() else 9.dp.toPx(), center = centerOf(i))
      if (sel) drawCircle(color.copy(alpha = 0.25f), radius = 30.dp.toPx(), center = centerOf(i))
    }
  }
}

@Composable
private fun PasswordEntry(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  onDone: () -> Unit = {},
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
    keyboardActions = KeyboardActions(onDone = { onDone() }),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
  )
}

/** Choose a lock type (PIN / pattern / password) and set it. The secret is returned as text. */
@Composable
private fun SetLockContent(
  showFace: Boolean,
  onCreate: (type: String, secret: String, useFace: Boolean) -> Unit,
) {
  var type by remember { mutableStateOf(LOCK_PIN) }
  var useFace by remember { mutableStateOf(showFace) }
  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      listOf(LOCK_PIN to "PIN", LOCK_PATTERN to "Pattern", LOCK_PASSWORD to "Password").forEach { (t, label) ->
        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(label) })
      }
    }
    if (showFace) {
      Row(
        modifier = Modifier.fillMaxWidth().clickable { useFace = !useFace }.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(checked = useFace, onCheckedChange = { useFace = it })
        Text("Unlock with face", style = MaterialTheme.typography.bodyMedium)
      }
    }
    key(type) {
      when (type) {
        LOCK_PATTERN -> SetPatternBody { onCreate(LOCK_PATTERN, it, useFace) }
        LOCK_PASSWORD -> SetPasswordBody { onCreate(LOCK_PASSWORD, it, useFace) }
        else -> SetPinBody { onCreate(LOCK_PIN, it, useFace) }
      }
    }
  }
}

@Composable
private fun SetPinBody(onDone: (String) -> Unit) {
  var first by remember { mutableStateOf("") }
  var entry by remember { mutableStateOf("") }
  var error by remember { mutableStateOf(false) }
  Column(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(if (first.isEmpty()) "Set PIN" else "Confirm PIN", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(36.dp))
      PinDots(count = 4, filled = entry.length)
      Spacer(Modifier.height(24.dp))
      Text(
        text = if (error) "The PINs don't match. Try again." else "Remember it: it can't be reset without clearing the app's data.",
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 32.dp),
      )
    }
    PinKeypad(
      onDigit = { d ->
        if (entry.length < 4) entry += d
        if (entry.length == 4) {
          if (first.isEmpty()) {
            first = entry
            entry = ""
            error = false
          } else if (entry == first) {
            onDone(entry)
          } else {
            error = true
            first = ""
            entry = ""
          }
        }
      },
      onBackspace = { if (entry.isNotEmpty()) entry = entry.dropLast(1) },
    )
  }
}

@Composable
private fun SetPatternBody(onDone: (String) -> Unit) {
  var first by remember { mutableStateOf("") }
  var message by remember { mutableStateOf<String?>(null) }
  var isError by remember { mutableStateOf(false) }
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(if (first.isEmpty()) "Draw a pattern" else "Draw it again to confirm", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    Text(
      text = message ?: "Connect at least 4 dots. Remember it: it can't be reset without clearing the app's data.",
      style = MaterialTheme.typography.bodySmall,
      color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
    PatternPad(
      error = isError,
      modifier = Modifier.fillMaxWidth(0.8f),
      onComplete = { seq ->
        when {
          seq.length < 4 -> {
            message = "Connect at least 4 dots"
            isError = true
          }
          first.isEmpty() -> {
            first = seq
            message = null
            isError = false
          }
          seq == first -> onDone(seq)
          else -> {
            first = ""
            message = "The patterns don't match. Try again."
            isError = true
          }
        }
      },
    )
  }
}

@Composable
private fun SetPasswordBody(onDone: (String) -> Unit) {
  var pass by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf("") }
  val mismatch = confirm.isNotEmpty() && pass != confirm
  Column(
    modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
  ) {
    Text("Set password", style = MaterialTheme.typography.titleMedium)
    PasswordEntry("Password (at least 4 characters)", pass, { pass = it })
    PasswordEntry("Confirm password", confirm, { confirm = it })
    Text(
      text = if (mismatch) "The passwords don't match" else "Remember it: it can't be reset without clearing the app's data.",
      style = MaterialTheme.typography.bodySmall,
      color = if (mismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 32.dp),
    )
    TextButton(onClick = { onDone(pass) }, enabled = pass.length >= 4 && pass == confirm) { Text("Set password") }
  }
}

/** Asks for the current secret, by lock type. [onSubmit] returns true when it was right. */
@Composable
private fun UnlockContent(
  type: String,
  faceEnabled: Boolean,
  heading: String? = null,
  onSubmit: (String) -> Boolean,
  onFaceRequest: () -> Unit,
  faceActive: Boolean = false,
  faceFallback: String = "",
  onFaceDone: () -> Unit = {},
  onFaceClose: () -> Unit = {},
) {
  var entry by remember { mutableStateOf("") }
  var wrong by remember { mutableStateOf(false) }
  LaunchedEffect(faceEnabled) {
    if (faceEnabled) onFaceRequest()
  }
  val title = heading ?: "Enter ${lockName(type)}"
  val wrongText = "Wrong ${lockName(type)}"

  when (type) {
    LOCK_PATTERN ->
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        if (faceActive) {
          FaceScanPanel(
            enroll = false,
            circle = 150.dp,
            fallbackLabel = faceFallback,
            onSuccess = onFaceDone,
            onFallback = onFaceClose,
            modifier = Modifier.padding(bottom = 16.dp),
          )
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(text = if (wrong) wrongText else " ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(20.dp))
        PatternPad(
          error = wrong,
          modifier = Modifier.fillMaxWidth(0.8f),
          onComplete = { seq -> wrong = seq.length < 4 || !onSubmit(seq) },
        )
        if (faceEnabled) {
          Spacer(Modifier.height(16.dp))
          TextButton(onClick = { onFaceRequest() }) { Text("Use face unlock") }
        }
      }
    LOCK_PASSWORD ->
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
      ) {
        if (faceActive) {
          FaceScanPanel(
            enroll = false,
            circle = 150.dp,
            fallbackLabel = faceFallback,
            onSuccess = onFaceDone,
            onFallback = onFaceClose,
            modifier = Modifier.padding(bottom = 16.dp),
          )
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        PasswordEntry("Password", entry, { entry = it; wrong = false }) {
          if (!onSubmit(entry)) {
            wrong = true
            entry = ""
          }
        }
        Text(text = if (wrong) wrongText else " ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(
          onClick = {
            if (!onSubmit(entry)) {
              wrong = true
              entry = ""
            }
          },
          enabled = entry.isNotEmpty(),
        ) { Text("Unlock") }
        if (faceEnabled) {
          TextButton(onClick = { onFaceRequest() }) { Text("Use face unlock") }
        }
      }
    else ->
      Column(modifier = Modifier.fillMaxSize()) {
        Column(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          if (faceActive) {
          FaceScanPanel(
            enroll = false,
            circle = 150.dp,
            fallbackLabel = faceFallback,
            onSuccess = onFaceDone,
            onFallback = onFaceClose,
            modifier = Modifier.padding(bottom = 16.dp),
          )
        }
          Text(title, style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(if (faceActive) 20.dp else 36.dp))
          PinDots(count = maxOf(4, entry.length), filled = entry.length)
          Spacer(Modifier.height(24.dp))
          Text(
            text = if (wrong) wrongText else " ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
          TextButton(
            onClick = {
              if (!onSubmit(entry)) {
                wrong = true
                entry = ""
              }
            },
            enabled = entry.length >= 4,
          ) { Text("Unlock") }
        }
        PinKeypad(
          onDigit = { d ->
            if (entry.length < 8) {
              entry += d
              wrong = false
              if (entry.length >= 4 && onSubmit(entry)) entry = ""
            }
          },
          onBackspace = { if (entry.isNotEmpty()) entry = entry.dropLast(1) },
          leftSlot = {
            if (faceEnabled) {
              IconButton(onClick = { onFaceRequest() }) {
                Icon(Icons.Filled.Face, contentDescription = "Use face unlock", modifier = Modifier.size(38.dp))
              }
            }
          },
        )
      }
  }
}

@Composable
private fun ChangeLockDialog(
  currentType: String,
  verify: (String) -> Boolean,
  onDismiss: () -> Unit,
  onNewLock: (type: String, secret: String) -> Unit,
) {
  var verified by remember { mutableStateOf(false) }
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
          Text("Change lock", style = MaterialTheme.typography.titleLarge)
        }
        if (!verified) {
          UnlockContent(
            type = currentType,
            faceEnabled = false,
            heading = "Enter current ${lockName(currentType)}",
            onSubmit = { s ->
              val ok = verify(s)
              if (ok) verified = true
              ok
            },
            onFaceRequest = {},
          )
        } else {
          SetLockContent(showFace = false, onCreate = { t, s, _ -> onNewLock(t, s) })
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Private videos
// ---------------------------------------------------------------------------------------------

private val thumbCache = LruCache<String, Bitmap>(60)

@Composable
private fun rememberThumb(path: String): Bitmap? {
  val bmp by produceState<Bitmap?>(thumbCache.get(path), path) {
    if (value == null) {
      value =
        withContext(Dispatchers.IO) {
          runCatching {
            val r = MediaMetadataRetriever()
            try {
              r.setDataSource(path)
              r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                Bitmap.createScaledBitmap(it, 320, (320f * it.height / it.width).toInt().coerceAtLeast(1), true)
              }
            } finally {
              r.release()
            }
          }.getOrNull()?.also { thumbCache.put(path, it) }
        }
    }
  }
  return bmp
}

@Composable
private fun Thumb(
  path: String,
  modifier: Modifier,
) {
  val bmp = rememberThumb(path)
  Box(
    modifier = modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
    contentAlignment = Alignment.Center,
  ) {
    if (bmp != null) {
      Image(bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HiddenVideoRow(
  video: HiddenVideo,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val context = LocalContext.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Box {
      Thumb(video.hiddenPath, Modifier.width(112.dp).aspectRatio(16f / 9f))
      if (selected) SelectedMark(Modifier.align(Alignment.TopStart))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = video.name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = Formatter.formatFileSize(context, video.size),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HiddenVideoCard(
  video: HiddenVideo,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val context = LocalContext.current
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick),
  ) {
    Box {
      Thumb(video.hiddenPath, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
      if (selected) SelectedMark(Modifier.align(Alignment.TopStart))
    }
    Text(
      text = video.name,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(top = 6.dp),
    )
    Text(
      text = Formatter.formatFileSize(context, video.size),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.outline,
    )
  }
}

@Composable
private fun OptionTile(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
  Column(
    modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
    Spacer(Modifier.height(4.dp))
    Text(label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
  }
}

@Composable
private fun PrivacyOptionsDialog(
  gridView: Boolean,
  columns: Int,
  sortKey: Int,
  sortDesc: Boolean,
  canFace: Boolean,
  faceOn: Boolean,
  onGrid: (Boolean) -> Unit,
  onColumns: (Int) -> Unit,
  onSortKey: (Int) -> Unit,
  onSortDesc: (Boolean) -> Unit,
  onChangeLock: () -> Unit,
  onToggleFace: () -> Unit,
  onDone: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDone,
    title = null,
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("Layout", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
          OptionTile(Icons.AutoMirrored.Filled.ViewList, "List", !gridView, { onGrid(false) })
          OptionTile(Icons.Filled.GridView, "Grid", gridView, { onGrid(true) })
        }
        if (gridView) {
          HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
          Text("Grid columns", style = MaterialTheme.typography.titleMedium)
          androidx.compose.material3.Slider(
            value = columns.toFloat(),
            onValueChange = { onColumns(it.toInt().coerceIn(2, 4)) },
            valueRange = 2f..4f,
            steps = 1,
          )
          Text(
            "$columns columns",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.align(Alignment.CenterHorizontally),
          )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Text("Sort", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
          OptionTile(Icons.Filled.Title, "Title", sortKey == 0, { onSortKey(0) })
          OptionTile(Icons.Filled.CalendarToday, "Date", sortKey == 2, { onSortKey(2) })
          OptionTile(Icons.Filled.SwapVert, "Size", sortKey == 1, { onSortKey(1) })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
          OptionTile(Icons.Filled.Schedule, "Length", sortKey == 3, { onSortKey(3) })
          OptionTile(Icons.Filled.Folder, "Folder", sortKey == 4, { onSortKey(4) })
          OptionTile(Icons.Filled.Category, "Type", sortKey == 5, { onSortKey(5) })
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = !sortDesc,
            onClick = { onSortDesc(false) },
            label = { Text("A-Z", maxLines = 1) },
            leadingIcon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.weight(1f),
          )
          FilterChip(
            selected = sortDesc,
            onClick = { onSortDesc(true) },
            label = { Text("Z-A", maxLines = 1) },
            leadingIcon = { Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.weight(1f),
          )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Text("Lock", style = MaterialTheme.typography.titleMedium)
        Row(
          modifier = Modifier.fillMaxWidth().clickable(onClick = onChangeLock).padding(vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) { Text("Change lock", modifier = Modifier.weight(1f)) }
        if (canFace) {
          Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleFace).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Unlock with face", modifier = Modifier.weight(1f))
            Checkbox(checked = faceOn, onCheckedChange = null)
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
  )
}

@Composable
private fun SelectedMark(modifier: Modifier) {
  Icon(
    Icons.Filled.CheckCircle,
    contentDescription = "Selected",
    tint = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(6.dp).size(26.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
  )
}

@Composable
private fun EmptyContent() {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = Icons.Outlined.Inbox,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.outlineVariant,
      modifier = Modifier.size(140.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text(
      text = "Only you can watch video in private folder. Import video now.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.outline,
      textAlign = TextAlign.Center,
    )
  }
}

// ---------------------------------------------------------------------------------------------
// Import: pick videos from the phone and move them into Private Videos
// ---------------------------------------------------------------------------------------------

private class ImportItem(
  val name: String,
  val path: String,
  val size: Long,
)

@Composable
private fun ImportDialog(
  onDismiss: () -> Unit,
  onImport: (List<String>) -> Unit,
) {
  val context = LocalContext.current
  var videos by remember { mutableStateOf<List<ImportItem>?>(null) }
  var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
  LaunchedEffect(Unit) {
    videos =
      withContext(Dispatchers.IO) {
        val out = mutableListOf<ImportItem>()
        val privateRoot = PrivacyStore.privateDir(context).absolutePath
        runCatching {
          context.contentResolver
            .query(
              MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
              arrayOf(MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATA, MediaStore.Video.Media.SIZE),
              null,
              null,
              MediaStore.Video.Media.DATE_ADDED + " DESC",
            )?.use { c ->
              while (c.moveToNext()) {
                val path = c.getString(1) ?: continue
                if (path.startsWith(privateRoot) || !File(path).isFile) continue
                out.add(ImportItem(c.getString(0) ?: File(path).name, path, c.getLong(2)))
              }
            }
        }
        out
      }
  }
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
          Text("Import videos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
          TextButton(onClick = { onImport(selected.toList()) }, enabled = selected.isNotEmpty()) {
            Text("Hide (${selected.size})")
          }
        }
        HorizontalDivider()
        val list = videos
        if (list == null) {
          Text("Loading…", modifier = Modifier.padding(24.dp))
        } else if (list.isEmpty()) {
          Text("No videos found", modifier = Modifier.padding(24.dp))
        } else {
          LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(list, key = { it.path }) { item ->
              val checked = item.path in selected
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .clickable { selected = if (checked) selected - item.path else selected + item.path }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Checkbox(checked = checked, onCheckedChange = { selected = if (it) selected + item.path else selected - item.path })
                Column(modifier = Modifier.weight(1f)) {
                  Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                  Text(
                    Formatter.formatFileSize(context, item.size) + "  •  " + (File(item.path).parentFile?.name ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
