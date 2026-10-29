package dev.sidali.echo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EchoTheme { Screen() } }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app stops a test and forgets the take.
        Memory.stop.set(true)
        if (isFinishing || !isChangingConfigurations) Memory.take = null
    }
}

/** The last take, in memory only. It survives a rotation and is forgotten when you leave. */
private object Memory {
    @Volatile var take: Take? = null
    val stop = AtomicBoolean(false)
}

private enum class Phase { IDLE, RECORDING, PROCESSING, PLAYING, PLAYING_RAW }

@Composable
private fun Screen() {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var mic by remember { mutableStateOf(Route.current(ctx)) }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var level by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(0f) }
    var take by remember { mutableStateOf(Memory.take) }
    var allowed by remember { mutableStateOf(hasMic(ctx)) }
    var denied by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // Plugging or unplugging a headset updates the microphone line right away.
    DisposableEffect(Unit) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(d: Array<out AudioDeviceInfo>) { mic = Route.current(ctx) }
            override fun onAudioDevicesRemoved(d: Array<out AudioDeviceInfo>) { mic = Route.current(ctx) }
        }
        am.registerAudioDeviceCallback(cb, null)
        onDispose { am.unregisterAudioDeviceCallback(cb); Memory.stop.set(true) }
    }

    fun play(raw: Boolean) {
        val t = take ?: return
        Memory.stop.set(false)
        phase = if (raw) Phase.PLAYING_RAW else Phase.PLAYING
        scope.launch {
            withContext(Dispatchers.Default) {
                CallSession(ctx).use { s -> s.play(if (raw) t.raw else t.call, { progress = it }, { Memory.stop.get() }) }
            }
            phase = Phase.IDLE; progress = 0f
        }
    }

    fun test() {
        Memory.stop.set(false); busy = false
        phase = Phase.RECORDING; progress = 0f
        scope.launch {
            val t = withContext(Dispatchers.Default) {
                CallSession(ctx).use { s ->
                    val r = s.record({ l, p -> level = l; progress = p }, { Memory.stop.get() })
                    if (r != null && !Memory.stop.get()) {
                        withContext(Dispatchers.Main) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            take = r; Memory.take = r; phase = Phase.PLAYING; level = 0f; progress = 0f
                        }
                        s.play(r.call, { progress = it }, { Memory.stop.get() })
                    }
                    r
                }
            }
            if (t == null && !Memory.stop.get()) busy = true
            phase = Phase.IDLE; level = 0f; progress = 0f
        }
    }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok; denied = !ok
        if (ok) test()
    }

    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(horizontal = Tokens.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Tokens.space24))
        Orb(phase, level, progress, Modifier.fillMaxWidth().height(Tokens.heroHeight))
        Spacer(Modifier.height(Tokens.space16))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(Tokens.space8))
        Text(
            stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = Tokens.colors.secondaryText, modifier = Modifier.widthIn(max = Tokens.textWidth),
        )
        Spacer(Modifier.height(Tokens.space32))

        Column(verticalArrangement = Arrangement.spacedBy(Tokens.cardGap)) {
            MicCard(mic)
            if (denied && !allowed) Card {
                Text(stringResource(R.string.mic_needed_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Tokens.space4))
                Text(stringResource(R.string.mic_needed_body), style = MaterialTheme.typography.bodyMedium, color = Tokens.colors.secondaryText)
                Spacer(Modifier.height(Tokens.space8))
                Capsule(stringResource(R.string.mic_settings)) {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                }
            }
            if (busy) Card {
                Text(stringResource(R.string.mic_busy), style = MaterialTheme.typography.bodyMedium)
            }
            val t = take
            if (t != null && phase == Phase.IDLE) ResultCard(t, onAgain = { play(false) }, onRaw = { play(true) })
        }
        Spacer(Modifier.height(Tokens.space24))

        val label = when (phase) {
            Phase.IDLE -> stringResource(R.string.test_me)
            Phase.RECORDING -> stringResource(R.string.recording, (Audio.SECONDS * (1 - progress)).toInt() + 1)
            Phase.PROCESSING -> stringResource(R.string.processing)
            Phase.PLAYING -> stringResource(R.string.playing)
            Phase.PLAYING_RAW -> stringResource(R.string.playing_raw)
        }
        PrimaryButton(if (phase == Phase.IDLE) label else stringResource(R.string.stop) + " · " + label) {
            when {
                phase != Phase.IDLE -> Memory.stop.set(true)
                hasMic(ctx) -> test()
                else -> ask.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        Spacer(Modifier.height(Tokens.space24))
        Text(
            stringResource(R.string.privacy), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            color = Tokens.colors.secondaryText, modifier = Modifier.widthIn(max = Tokens.textWidth),
        )
        Spacer(Modifier.height(Tokens.space8))
        TextLink(stringResource(R.string.about)) { AboutActivity.open(ctx) }
        Spacer(Modifier.height(Tokens.space16))
    }
}

private fun hasMic(ctx: android.content.Context) =
    ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
private fun micTitle(m: Mic) = when (m.kind) {
    MicKind.USB -> m.name.ifEmpty { stringResource(R.string.mic_usb) }
    MicKind.WIRED -> m.name.ifEmpty { stringResource(R.string.mic_wired) }
    MicKind.BLUETOOTH -> m.name.ifEmpty { stringResource(R.string.mic_bluetooth) }
    MicKind.PHONE -> stringResource(R.string.mic_phone)
}

@Composable
private fun MicCard(m: Mic) {
    Card {
        SectionLabel(stringResource(R.string.mic_label))
        Spacer(Modifier.height(Tokens.space12))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (m.kind == MicKind.BLUETOOTH) Tokens.colors.warning else Tokens.colors.success, live = false)
            Spacer(Modifier.width(Tokens.space12))
            AnimatedContent(
                m, label = "mic",
                transitionSpec = { fadeIn(tween(Tokens.fadeInMs)) togetherWith fadeOut(tween(Tokens.fadeOutMs)) },
            ) { Text(micTitle(it), style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.height(Tokens.space8))
        Text(
            stringResource(
                when (m.kind) {
                    MicKind.USB -> R.string.mic_usb_body
                    MicKind.WIRED -> R.string.mic_wired_body
                    MicKind.BLUETOOTH -> R.string.mic_bluetooth_body
                    MicKind.PHONE -> R.string.mic_phone_body
                },
            ),
            style = MaterialTheme.typography.bodyMedium, color = Tokens.colors.secondaryText,
        )
    }
}

@Composable
private fun ResultCard(t: Take, onAgain: () -> Unit, onRaw: () -> Unit) {
    val c = Tokens.colors
    val (title, body, color) = when (t.level) {
        Level.SILENT -> Triple(R.string.level_silent, R.string.level_silent_body, c.danger)
        Level.QUIET -> Triple(R.string.level_quiet, R.string.level_quiet_body, c.warning)
        Level.GOOD -> Triple(R.string.level_good, R.string.level_good_body, c.success)
        Level.CLIPPING -> Triple(R.string.level_clipping, R.string.level_clipping_body, c.warning)
    }
    Card {
        SectionLabel(stringResource(R.string.result_label))
        Spacer(Modifier.height(Tokens.space12))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color, live = false)
            Spacer(Modifier.width(Tokens.space12))
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.level_db, t.loudDb.roundToInt()),
                style = MaterialTheme.typography.bodySmall, color = c.secondaryText,
            )
        }
        Spacer(Modifier.height(Tokens.space8))
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = c.secondaryText)
        Spacer(Modifier.height(Tokens.space4))
        Text(stringResource(R.string.recorded_with, micTitle(t.mic)), style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
        Spacer(Modifier.height(Tokens.space8))
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.space8)) {
            Capsule(stringResource(R.string.play_again), onAgain)
            Capsule(stringResource(R.string.play_raw), onRaw)
        }
    }
}

/**
 * The hero: a voice and its echo. Idle, three still rings. Recording, the rings follow your voice and an
 * arc counts the five seconds. Playing, the arc follows the playback.
 */
@Composable
private fun Orb(phase: Phase, level: Float, progress: Float, modifier: Modifier) {
    val c = Tokens.colors
    val l by animateFloatAsState(level, spring(dampingRatio = 0.7f, stiffness = 400f), label = "level")
    val desc = stringResource(R.string.tagline)
    Canvas(modifier.semantics { contentDescription = desc }) {
        val center = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension * 0.18f
        val ring = size.minDimension * 0.012f
        for (i in 3 downTo 1) {
            val grow = if (phase == Phase.RECORDING) l * 0.35f * i else 0f
            drawCircle(
                c.accent.copy(alpha = 0.10f * (4 - i) + (if (phase == Phase.RECORDING) l * 0.15f else 0f)),
                radius = r * (1f + 0.42f * i + grow), center = center, style = Stroke(ring * 2),
            )
        }
        drawCircle(c.accent, radius = r * (1f + if (phase == Phase.RECORDING) l * 0.25f else 0f), center = center)
        drawCircle(c.onAccent, radius = r * 0.22f, center = center)
        if (phase != Phase.IDLE && phase != Phase.PROCESSING) {
            val d = r * 2 * 2.55f
            drawArc(
                c.honey, -90f, 360f * progress, false,
                topLeft = Offset(center.x - d / 2, center.y - d / 2), size = Size(d, d),
                style = Stroke(ring * 3, cap = StrokeCap.Round),
            )
        }
    }
}
