package com.offline.bydvoice.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SensorDoor
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offline.bydvoice.command.Category
import com.offline.bydvoice.command.CommandCatalog
import com.offline.bydvoice.command.CommandSpec
import com.offline.bydvoice.ui.theme.Palette

fun Category.icon(): ImageVector = when (this) {
    Category.CLIMATE -> Icons.Rounded.Thermostat
    Category.AIRFLOW -> Icons.Rounded.Air
    Category.SEATS -> Icons.Rounded.AirlineSeatReclineNormal
    Category.STEERING_MIRRORS -> Icons.Rounded.TripOrigin
    Category.DOORS_TRUNK -> Icons.Rounded.SensorDoor
    Category.WINDOWS -> Icons.Rounded.Window
    Category.ROOF_SHADE -> Icons.Rounded.WbSunny
    Category.LIGHTS -> Icons.Rounded.Lightbulb
    Category.CAMERAS -> Icons.Rounded.Videocam
    Category.DRIVING -> Icons.Rounded.Speed
    Category.SCREEN -> Icons.Rounded.Monitor
    Category.APPS -> Icons.Rounded.Apps
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommandsPanel(
    disabled: Set<String>,
    onToggle: (List<String>, Boolean) -> Unit,
    onTry: (CommandSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }

    val all = CommandCatalog.all
    val shown = remember(query, category) {
        val q = query.trim().lowercase()
        all.filter { (category == null || it.category == category) &&
            (q.isEmpty() || it.title.lowercase().contains(q) || it.examples.any { e -> e.contains(q) }) }
    }
    val enabledCount = all.count { it.id !in disabled }

    Panel(modifier) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Voice commands", style = MaterialTheme.typography.headlineSmall)
                    Text("$enabledCount of ${all.size} enabled · say them any way you like",
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
                }
                TextButton(onClick = { onToggle(shown.map { it.id }, true) }) { Text("Enable all") }
                TextButton(onClick = { onToggle(shown.map { it.id }, false) }) { Text("Disable all", color = Palette.TextSecondary) }
            }
            Spacer(Modifier.height(14.dp))
            TextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search commands or phrases") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Palette.Surface2, unfocusedContainerColor = Palette.Surface2,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Spacer(Modifier.height(12.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { CategoryChip("All", all.size, category == null) { category = null } }
            items(Category.entries.toList()) { c ->
                CategoryChip(c.title, all.count { it.category == c }, category == c, c.icon()) { category = if (category == c) null else c }
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            shown.groupBy { it.category }.forEach { (cat, specs) ->
                stickyHeader(key = "h_${cat.name}") {
                    val on = specs.count { it.id !in disabled }
                    Row(
                        Modifier.fillMaxWidth().background(Palette.Surface1).padding(start = 8.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(cat.icon(), null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(cat.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text("$on/${specs.size}", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                        Spacer(Modifier.weight(1f))
                        Switch(checked = on > 0, onCheckedChange = { v -> onToggle(specs.map { it.id }, v) }, colors = switchColors())
                    }
                }
                items(specs, key = { it.id }) { spec ->
                    CommandRow(spec, enabled = spec.id !in disabled, onToggle = { onToggle(listOf(spec.id), it) }, onTry = { onTry(spec) })
                }
            }
            if (shown.isEmpty()) item {
                Text("No commands match “$query”", Modifier.padding(24.dp), color = Palette.TextSecondary)
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, count: Int, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text("$label  $count", maxLines = 1) },
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(18.dp)) } },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Palette.Surface2, labelColor = Palette.TextSecondary, iconColor = Palette.TextSecondary,
            selectedContainerColor = Palette.AccentSoft, selectedLabelColor = Palette.Text, selectedLeadingIconColor = Palette.Accent,
        ),
        border = null,
    )
}

@Composable
private fun CommandRow(spec: CommandSpec, enabled: Boolean, onToggle: (Boolean) -> Unit, onTry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Palette.Surface2 else Palette.Surface1)
            .border(1.dp, if (enabled) Palette.Stroke else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable { onToggle(!enabled) }
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else 0.5f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(spec.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                spec.dynamic?.let {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Palette.AccentSoft).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("“${spec.examples.drop(1).firstOrNull() ?: spec.examples.first()}”",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onTry, enabled = enabled) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(if (enabled) Palette.Surface3 else Color.Transparent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, "Try ${spec.title}", tint = if (enabled) Palette.Text else Palette.TextTertiary, modifier = Modifier.size(20.dp))
            }
        }
        Switch(checked = enabled, onCheckedChange = onToggle, colors = switchColors())
    }
}

@Composable
fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White, checkedTrackColor = Palette.Accent, checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Palette.TextSecondary, uncheckedTrackColor = Palette.Surface3, uncheckedBorderColor = Color.Transparent,
)
