package com.offline.bydvoice.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.offline.bydvoice.ui.theme.Palette

/** One tappable tile: label + icon + the English command it fires. */
data class QuickCommand(val label: String, val icon: ImageVector, val text: String)

/** The most-used commands, as big buttons. Tapping fires the same path as speaking the phrase. */
val QUICK_COMMANDS = listOf(
    QuickCommand("AC on", Icons.Rounded.AcUnit, "turn on the air conditioning"),
    QuickCommand("AC off", Icons.Rounded.PowerSettingsNew, "turn off the air conditioning"),
    QuickCommand("Warmer", Icons.Rounded.Add, "make it warmer"),
    QuickCommand("Cooler", Icons.Rounded.Remove, "make it cooler"),
    QuickCommand("Defrost", Icons.Rounded.Air, "defrost the windshield"),
    QuickCommand("Recirculate", Icons.Rounded.Autorenew, "turn on recirculation"),
    QuickCommand("Open windows", Icons.Rounded.ArrowDownward, "open all the windows"),
    QuickCommand("Close windows", Icons.Rounded.ArrowUpward, "close all the windows"),
    QuickCommand("Open trunk", Icons.Rounded.Inventory2, "open the trunk"),
    QuickCommand("Driver heat", Icons.Rounded.Chair, "turn on the driver seat heating"),
    QuickCommand("Passenger heat", Icons.Rounded.Chair, "turn on the passenger seat heating"),
    QuickCommand("Sunroof", Icons.Rounded.WbSunny, "open the sunroof"),
    QuickCommand("Sport mode", Icons.Rounded.Speed, "sport mode"),
    QuickCommand("Eco mode", Icons.Rounded.Autorenew, "eco mode"),
    QuickCommand("Hazards", Icons.Rounded.Warning, "turn on the hazard lights"),
    QuickCommand("Lock", Icons.Rounded.Lock, "lock the car"),
    QuickCommand("Unlock", Icons.Rounded.LockOpen, "unlock the doors"),
    QuickCommand("Rear camera", Icons.Rounded.Videocam, "show me the rear camera"),
)

@Composable
fun QuickGrid(run: (String) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 158.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(QUICK_COMMANDS) { q ->
            androidx.compose.foundation.layout.Column(
                Modifier
                    .height(118.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.Surface2)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        run(q.text)
                    }
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(q.icon, null, tint = Palette.Accent, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    q.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}
