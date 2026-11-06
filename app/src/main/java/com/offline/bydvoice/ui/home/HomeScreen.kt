package com.offline.bydvoice.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offline.bydvoice.R
import com.offline.bydvoice.command.CommandSpec
import com.offline.bydvoice.state.HistoryItem
import com.offline.bydvoice.state.Sensitivity
import com.offline.bydvoice.state.Timing
import com.offline.bydvoice.state.Phase
import com.offline.bydvoice.state.VoiceUiState
import com.offline.bydvoice.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the screen needs to show, gathered by MainActivity. */
data class HomeModel(
    val voice: VoiceUiState,
    val history: List<HistoryItem>,
    val disabled: Set<String>,
    val handsFree: Boolean,
    val voiceReplies: Boolean,
    val driverOnLeft: Boolean,
    val perms: Perms,
    val triggerKeys: String,
    val learning: Boolean,
    val timing: Timing,
)

data class Perms(
    val mic: Boolean, val overlay: Boolean, val notifications: Boolean,
    val systemSettings: Boolean, val battery: Boolean, val accessibility: Boolean,
    /** Vehicle-API permissions: null when this isn't a BYD head unit. */
    val car: Boolean?,
)

class HomeActions(
    val speak: () -> Unit,
    val tryCommand: (CommandSpec) -> Unit,
    val runText: (String) -> Unit,
    val toggleCommands: (List<String>, Boolean) -> Unit,
    val setHandsFree: (Boolean) -> Unit,
    val setVoiceReplies: (Boolean) -> Unit,
    val setDriverOnLeft: (Boolean) -> Unit,
    val setTiming: (Timing) -> Unit,
    val fixMic: () -> Unit,
    val fixCar: () -> Unit,
    val fixOverlay: () -> Unit,
    val fixNotifications: () -> Unit,
    val fixSystemSettings: () -> Unit,
    val fixBattery: () -> Unit,
    val fixAccessibility: () -> Unit,
    val learnButton: () -> Unit,
)

@Composable
fun HomeScreen(m: HomeModel, a: HomeActions) {
    Surface(color = Palette.Bg, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(
                        Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Header(m)
                        AssistantCard(m, a)
                        ControlsCard(m, a)
                        ActivityCard(m.history)
                    }
                    CommandsPane(m, a, Modifier.weight(0.58f).fillMaxHeight())
                }
            } else {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = tab, containerColor = Palette.Bg) {
                        Tab(tab == 0, { tab = 0 }, text = { Text("Quick") })
                        Tab(tab == 1, { tab = 1 }, text = { Text("Assistant") })
                        Tab(tab == 2, { tab = 2 }, text = { Text("Commands") })
                    }
                    when (tab) {
                        0 -> QuickGrid(a.runText, Modifier.fillMaxSize().padding(16.dp))
                        1 -> Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) { Header(m); AssistantCard(m, a); ControlsCard(m, a); ActivityCard(m.history) }
                        else -> CommandsPanel(m.disabled, a.toggleCommands, a.tryCommand, Modifier.fillMaxSize().padding(12.dp))
                    }
                }
            }
        }
    }
}

/** Right pane: a Quick grid of big buttons, or the full searchable command list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandsPane(m: HomeModel, a: HomeActions, modifier: Modifier) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            SegmentedButton(mode == 0, { mode = 0 }, SegmentedButtonDefaults.itemShape(0, 2), colors = segColors()) { Text("Quick") }
            SegmentedButton(mode == 1, { mode = 1 }, SegmentedButtonDefaults.itemShape(1, 2), colors = segColors()) { Text("All commands") }
        }
        if (mode == 0) QuickGrid(a.runText, Modifier.weight(1f))
        else CommandsPanel(m.disabled, a.toggleCommands, a.tryCommand, Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun Header(m: HomeModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.byd_wordmark), "BYD", Modifier.height(24.dp))
        Spacer(Modifier.width(18.dp))
        Box(Modifier.width(1.dp).height(36.dp).background(Palette.Stroke))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("Voice Commands", style = MaterialTheme.typography.headlineSmall)
            Text("Offline · on-device · nothing leaves the car", style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
        }
        val (label, color) = when {
            !m.perms.mic -> "Mic blocked" to Palette.Warn
            m.perms.car == false -> "Car controls off" to Palette.Warn
            m.voice.micSilent -> "Mic silent" to Palette.Warn
            !m.voice.modelReady -> "Loading model" to Palette.TextSecondary
            m.handsFree -> "Listening for “Hi BYD”" to Palette.Success
            else -> "Ready" to Palette.Success
        }
        StatusPill(label, color)
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

// ---------------------------------------------------------------- assistant

@Composable
private fun AssistantCard(m: HomeModel, a: HomeActions) {
    val v = m.voice
    Panel {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            VoiceOrb(v.phase, v.level, 220.dp, onClick = a.speak)
            val headline = when (v.phase) {
                Phase.IDLE -> if (m.handsFree) "Say “Hi BYD”" else "Tap the mic or press the wheel button"
                Phase.LOADING -> "Getting ready…"
                Phase.LISTENING -> "Listening…"
                Phase.PROCESSING -> "Working on it…"
                Phase.SUCCESS -> v.commandTitle ?: "Done"
                Phase.ERROR -> "Didn't work"
            }
            Text(headline, style = MaterialTheme.typography.titleLarge, color = phaseColor(v.phase).takeIf { v.phase != Phase.IDLE } ?: Palette.Text,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.height(64.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    v.phase == Phase.LISTENING && v.transcript.isBlank() ->
                        LevelBars(v.level, Palette.Accent, Modifier.width(220.dp).height(44.dp))
                    v.transcript.isNotBlank() && v.phase != Phase.IDLE ->
                        Text("“${v.transcript}”", style = MaterialTheme.typography.bodyLarge, color = Palette.Text,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    else -> Text("Push-to-talk keeps the microphone off until you ask.", style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextTertiary, textAlign = TextAlign.Center)
                }
            }
            AnimatedVisibility(v.reply != null && (v.phase == Phase.SUCCESS || v.phase == Phase.ERROR)) {
                Text(v.reply.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, textAlign = TextAlign.Center)
            }
            if (v.micSilent) {
                Spacer(Modifier.height(12.dp))
                Banner("The microphone is delivering pure silence. Check the microphone permission, or on an emulator enable host audio input.")
            }
        }
    }
}

// ---------------------------------------------------------------- controls

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ControlsCard(m: HomeModel, a: HomeActions) {
    Panel {
        Column(Modifier.padding(22.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SectionTitle(Icons.Rounded.Tune, "Controls")

            Labeled("Listening mode", "Push-to-talk keeps the mic off until triggered. Hands-free listens for “Hi BYD” (more battery).") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!m.handsFree, { a.setHandsFree(false) }, SegmentedButtonDefaults.itemShape(0, 2), colors = segColors()) { Text("Push-to-talk") }
                    SegmentedButton(m.handsFree, { a.setHandsFree(true) }, SegmentedButtonDefaults.itemShape(1, 2), colors = segColors()) { Text("Hands-free") }
                }
            }

            TimingSection(m.timing, a.setTiming)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Voice replies", style = MaterialTheme.typography.titleMedium)
                    Text("Speak a confirmation after each command", style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
                }
                Switch(m.voiceReplies, a.setVoiceReplies, colors = switchColors())
            }

            Labeled("Driver sits on the", "Used for “driver window” / “passenger window”.") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(m.driverOnLeft, { a.setDriverOnLeft(true) }, SegmentedButtonDefaults.itemShape(0, 2), colors = segColors()) { Text("Left (LHD)") }
                    SegmentedButton(!m.driverOnLeft, { a.setDriverOnLeft(false) }, SegmentedButtonDefaults.itemShape(1, 2), colors = segColors()) { Text("Right (RHD)") }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Steering-wheel mic button", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (m.learning) "Press the button on your steering wheel now…"
                    else "Keycodes: ${m.triggerKeys}. The press is consumed so the built-in assistant stays quiet.",
                    style = MaterialTheme.typography.bodySmall, color = if (m.learning) Palette.Accent else Palette.TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionChip(if (m.perms.accessibility) "Button service on" else "Enable button service", ok = m.perms.accessibility, onClick = a.fixAccessibility)
                    ActionChip("Learn button", ok = null, onClick = a.learnButton)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Permissions", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    m.perms.car?.let { ActionChip("Car controls", it, a.fixCar) }
                    ActionChip("Microphone", m.perms.mic, a.fixMic)
                    ActionChip("Show over apps", m.perms.overlay, a.fixOverlay)
                    ActionChip("Notifications", m.perms.notifications, a.fixNotifications)
                    ActionChip("Screen brightness", m.perms.systemSettings, a.fixSystemSettings)
                    ActionChip("Stay running", m.perms.battery, a.fixBattery)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimingSection(t: Timing, set: (Timing) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Listening timing", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (t != Timing()) TextButton({ set(Timing()) }) { Text("Reset", color = Palette.Accent) }
        }
        TimingSlider("Stop after a pause of", t.silenceMs, 500L..3_000L, 100L,
            "Shorter answers sooner; longer lets you pause mid-sentence.") { set(t.copy(silenceMs = it)) }
        TimingSlider("Give up if nothing is said for", t.noSpeechMs, 2_000L..15_000L, 1_000L,
            "How long the mic waits for you to start talking.") { set(t.copy(noSpeechMs = it)) }
        TimingSlider("Longest command", t.maxUtteranceMs, 4_000L..30_000L, 1_000L,
            "The mic closes after this even if you're still talking.") { set(t.copy(maxUtteranceMs = it)) }
        TimingSlider("Hands-free: wait after “Hi BYD”", t.awakeMs, 3_000L..20_000L, 1_000L,
            "How long it listens for a command once woken.") { set(t.copy(awakeMs = it)) }
        Labeled("Mic sensitivity", "High picks up quiet voices; Low ignores more road and fan noise.") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Sensitivity.entries.forEachIndexed { i, s ->
                    SegmentedButton(t.sensitivity == s, { set(t.copy(sensitivity = s)) }, SegmentedButtonDefaults.itemShape(i, Sensitivity.entries.size), colors = segColors()) {
                        Text(s.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimingSlider(label: String, valueMs: Long, range: LongRange, stepMs: Long, hint: String, onChange: (Long) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(if (stepMs < 1_000) "%.1f s".format(valueMs / 1000.0) else "${valueMs / 1000} s",
                style = MaterialTheme.typography.titleMedium, color = Palette.Accent)
        }
        Slider(
            value = valueMs.toFloat(),
            onValueChange = { v -> onChange((Math.round(v / stepMs) * stepMs).coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / stepMs - 1).toInt(),
            colors = SliderDefaults.colors(
                thumbColor = Palette.Accent, activeTrackColor = Palette.Accent, inactiveTrackColor = Palette.Surface3,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun segColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = Palette.AccentSoft, activeContentColor = Palette.Text, activeBorderColor = Palette.StrokeStrong,
    inactiveContainerColor = Palette.Surface2, inactiveContentColor = Palette.TextSecondary, inactiveBorderColor = Palette.Stroke,
)

@Composable
private fun Labeled(title: String, hint: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
    }
}

/** ok = true (granted), false (needs fixing), null (plain action). */
@Composable
private fun ActionChip(label: String, ok: Boolean?, onClick: () -> Unit) {
    val color = when (ok) { true -> Palette.Success; false -> Palette.Warn; null -> Palette.Text }
    Row(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface2)
            .border(1.dp, if (ok == false) Palette.Warn.copy(alpha = 0.5f) else Palette.Stroke, RoundedCornerShape(12.dp))
            .clickable(enabled = ok != true, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (ok) {
            true -> Icon(Icons.Rounded.CheckCircle, null, tint = color, modifier = Modifier.size(18.dp))
            false -> Icon(Icons.Rounded.Warning, null, tint = color, modifier = Modifier.size(18.dp))
            null -> Icon(Icons.Rounded.Mic, null, tint = Palette.Accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(if (ok == false) "$label · fix" else label, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
    }
}

// ---------------------------------------------------------------- activity

@Composable
private fun ActivityCard(history: List<HistoryItem>) {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    Panel {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(Icons.Rounded.History, "Recent")
            if (history.isEmpty()) {
                Text("Your commands will show up here.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextTertiary)
            }
            history.take(8).forEach { h ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(if (h.ok) Palette.Success else Palette.Warn))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.commandTitle ?: "Not understood", style = MaterialTheme.typography.titleMedium)
                        if (h.heard.isNotBlank()) Text("“${h.heard}”", style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(h.reply, style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(fmt.format(Date(h.time)), style = MaterialTheme.typography.labelMedium, color = Palette.TextTertiary)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(Palette.Surface1).border(1.dp, Palette.Stroke, RoundedCornerShape(28.dp)),
        content = content,
    )
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Banner(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.WarnSoft).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Warning, null, tint = Palette.Warn)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.Text)
    }
}

@Suppress("unused")
@Composable
private fun LinkButton(text: String, onClick: () -> Unit) = TextButton(onClick = onClick) { Text(text) }
