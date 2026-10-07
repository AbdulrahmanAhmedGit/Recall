package com.example.myapplication4.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication4.data.LessonOverview
import com.example.myapplication4.data.SubjectEntity
import com.example.myapplication4.ui.design.*

enum class RecallDestination(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Outlined.Today), Library("Library", Icons.Outlined.LocalLibrary),
    Insights("Insights", Icons.Outlined.QueryStats), Settings("Settings", Icons.Outlined.Tune)
}

@Composable
fun RecallDock(selected: RecallDestination, onSelected: (RecallDestination) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.padding(horizontal = RecallSpacing.md).navigationBarsPadding().height(RecallSizes.dockHeight), shape = RecallRadii.extraLarge, color = MaterialTheme.colorScheme.surfaceElevated, contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 0.dp, shadowElevation = 8.dp) {
        Row(Modifier.fillMaxSize().padding(horizontal = RecallSpacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
            RecallDestination.entries.forEach { destination -> DockItem(destination, destination == selected) { onSelected(destination) } }
        }
    }
}

@Composable private fun RowScope.DockItem(destination: RecallDestination, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.muted, tween(RecallMotion.quick), label = "dockColor")
    val scale by animateFloatAsState(if (selected) 1f else .96f, tween(RecallMotion.quick), label = "dockScale")
    val background by animateColorAsState(if (selected) MaterialTheme.colorScheme.surfaceSelected else Color.Transparent, tween(RecallMotion.quick), label = "dockIndicator")
    val interaction = remember { MutableInteractionSource() }
    Column(Modifier.weight(1f).fillMaxHeight().clip(RecallRadii.medium).clickable(interaction, null, role = Role.Tab, onClick = onClick).scale(scale), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.height(30.dp).width(42.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) { Icon(destination.icon, destination.label, Modifier.size(RecallSizes.icon), tint = color) }
        Text(destination.label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
fun RecallTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = RecallSpacing.sm, bottom = RecallSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) RecallIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        Column(Modifier.weight(1f).padding(start = if (onBack == null) 0.dp else RecallSpacing.xs)) {
            BidiAwareText(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
            if (subtitle != null) BidiAwareText(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted, maxLines = 2)
        }
        action?.invoke()
    }
}

@Composable fun RecallIconButton(icon: ImageVector, description: String, onClick: () -> Unit) { IconButton(onClick, Modifier.size(RecallSizes.touch)) { Icon(icon, description, Modifier.size(RecallSizes.icon)) } }

@Composable
fun RecallPrimaryButton(text: String, icon: ImageVector? = null, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.heightIn(min = RecallSizes.buttonHeight), enabled = enabled, shape = RecallRadii.medium, contentPadding = PaddingValues(horizontal = RecallSpacing.ml)) { if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(RecallSpacing.xs)) }; Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) { Row(Modifier.fillMaxWidth().padding(top = RecallSpacing.lg, bottom = RecallSpacing.sm), verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() }); if (action != null) TextButton(onClick = onAction) { Text(action) } } }

@Composable
fun SubjectRow(subject: SubjectEntity, lessonCount: Int, cardCount: Int, due: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RecallRadii.medium).clickable(onClick = onClick).padding(vertical = RecallSpacing.md, horizontal = RecallSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        val accent = subjectAccent(subject.accent)
        Box(Modifier.size(46.dp).clip(RecallRadii.medium).background(subjectAccentContainer(subject.accent)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.LocalLibrary, null, tint = accent, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(RecallSpacing.sm)); Column(Modifier.weight(1f)) { BidiAwareText(subject.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("$lessonCount lessons · $cardCount cards", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) }
        if (due > 0) CountBadge("$due due") else Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun LessonRow(lesson: LessonOverview, showSubject: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RecallRadii.medium).clickable(onClick = onClick).padding(vertical = RecallSpacing.md, horizontal = RecallSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(42.dp).clip(CircleShape).background(subjectAccent(lesson.accent)))
        Spacer(Modifier.width(RecallSpacing.sm)); Column(Modifier.weight(1f)) { BidiAwareText(lesson.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis); val meta = listOfNotNull(if (showSubject) lesson.subjectName else null, lesson.chapterName).joinToString(" · "); if(meta.isNotEmpty()) BidiAwareText(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted, maxLines = 1) }
        Column(horizontalAlignment = Alignment.End) { Text(if (lesson.due > 0) "${lesson.due} due" else "Caught up", style = MaterialTheme.typography.labelMedium, color = if (lesson.due > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.muted); Text("${lesson.total} cards", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.muted) }
    }
}

@Composable fun CountBadge(text: String) { Surface(color = MaterialTheme.colorScheme.surfaceSelected, contentColor = MaterialTheme.colorScheme.primary, shape = CircleShape) { Text(text, modifier = Modifier.padding(horizontal = RecallSpacing.sm, vertical = RecallSpacing.xxs), style = MaterialTheme.typography.labelMedium) } }

@Composable
fun RecallEmptyState(icon: ImageVector, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = RecallSpacing.xxl, horizontal = RecallSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceInteractive), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }; Spacer(Modifier.height(RecallSpacing.md)); BidiAwareText(title, style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(RecallSpacing.xxs)); BidiAwareText(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.muted); if(action != null) RecallPrimaryButton(action, onClick = onAction, modifier = Modifier.padding(top = RecallSpacing.ml)) }
}

@Composable fun subjectAccent(value: String): Color {
    val light = MaterialTheme.colorScheme.isRecallLight
    return when (value) {
        "purple" -> if (light) Color(0xFF6D55A0) else Color(0xFFC9B4F3)
        "orange" -> if (light) Color(0xFF996022) else Color(0xFFF2BE7B)
        "teal" -> if (light) Color(0xFF25716E) else Color(0xFF84D1CC)
        else -> if (light) Color(0xFF35668F) else Color(0xFFA6CDF2)
    }
}

@Composable private fun subjectAccentContainer(value: String): Color {
    val light = MaterialTheme.colorScheme.isRecallLight
    return when (value) {
        "purple" -> if (light) Color(0xFFEDE7F7) else Color(0xFF352C48)
        "orange" -> if (light) Color(0xFFF8EADB) else Color(0xFF443322)
        "teal" -> if (light) Color(0xFFDDEFEA) else Color(0xFF203D3B)
        else -> if (light) Color(0xFFE2EDF7) else Color(0xFF24394B)
    }
}
