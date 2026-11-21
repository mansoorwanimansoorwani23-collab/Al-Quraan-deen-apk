package com.example.ui.screens.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiChatMessage
import com.example.ai.AiVoicePlaybackState
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.GoldAccentDark
import java.util.Locale

/**
 * Interactive Voice Player Bar for Hidayah AI:
 * - Pause & Resume from exact position
 * - Rewind (-5s) & Forward (+5s)
 * - Draggable Seekbar with live mm:ss timestamps
 * - Speed toggle (1x / 1.25x / 1.5x / 0.75x)
 */
@Composable
fun AiVoicePlayerBar(
    voiceState: AiVoicePlaybackState,
    onTogglePauseResume: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val sliderValue = if (isDraggingSlider) dragFraction else voiceState.progressFraction
    val speedLabel = when {
        kotlin.math.abs(voiceState.playbackSpeed - 1.0f) < 0.05f -> "1x"
        kotlin.math.abs(voiceState.playbackSpeed - 1.25f) < 0.05f -> "1.25x"
        kotlin.math.abs(voiceState.playbackSpeed - 1.5f) < 0.05f -> "1.5x"
        else -> "0.75x"
    }

    LiquidGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_voice_player_bar"),
        shape = RoundedCornerShape(20.dp),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompactWidth = maxWidth < 340.dp
            val transportGap = if (isCompactWidth) 12.dp else 20.dp

            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Row: Voice Info + Speed Pill + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (voiceState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = voiceState.activeVoiceName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    !voiceState.statusMessage.isNullOrBlank() -> voiceState.statusMessage
                                    voiceState.isLoading -> "Loading voice..."
                                    voiceState.isPaused -> "Paused • Tap Play"
                                    else -> "Playing"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .defaultMinSize(minWidth = 44.dp, minHeight = 34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onCycleSpeed)
                                .testTag("ai_voice_speed_button"),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = speedLabel,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        IconButton(
                            onClick = onStop,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("ai_voice_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Stop and close player",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Seekbar + Live Timestamps
                if (!voiceState.isLoading && voiceState.totalDurationMs > 0L) {
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            isDraggingSlider = true
                            dragFraction = it
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            onSeekFraction(dragFraction)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("ai_voice_seek_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val shownMs = if (isDraggingSlider) {
                            (dragFraction * voiceState.totalDurationMs).toLong()
                        } else {
                            voiceState.currentPositionMs
                        }
                        Text(
                            text = formatDurationMs(shownMs),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDurationMs(voiceState.totalDurationMs),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Transport Controls: Rewind -5s | Play / Pause | Forward +5s
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onRewind,
                        enabled = !voiceState.isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ai_voice_rewind_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Replay5,
                            contentDescription = "Rewind 5 seconds",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(transportGap))

                    FilledIconButton(
                        onClick = onTogglePauseResume,
                        enabled = !voiceState.isLoading,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ai_voice_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (voiceState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (voiceState.isPlaying) "Pause voice" else "Resume voice",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(transportGap))

                    IconButton(
                        onClick = onForward,
                        enabled = !voiceState.isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ai_voice_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Forward5,
                            contentDescription = "Forward 5 seconds",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats markdown bold (**text**), headings (###), and bullets cleanly into readable styled text
 * so users never see ugly raw asterisks or hashes in AI replies.
 */
fun formatMarkdownMessage(raw: String): AnnotatedString {
    val lines = raw.lines()
    return buildAnnotatedString {
        lines.forEachIndexed { lineIdx, origLine ->
            val trimmed = origLine.trimStart()
            val isHeading = trimmed.startsWith("#")
            val cleanedLine = when {
                isHeading -> trimmed.trimStart('#').trim()
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> "• " + trimmed.drop(2)
                else -> origLine
            }

            if (isHeading) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlineBold(cleanedLine)
                }
            } else {
                appendInlineBold(cleanedLine)
            }
            if (lineIdx < lines.lastIndex) {
                append("\n")
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInlineBold(text: String) {
    var cursor = 0
    while (cursor < text.length) {
        val start = text.indexOf("**", cursor)
        if (start < 0) {
            append(text.substring(cursor).replace("*", ""))
            break
        }
        if (start > cursor) {
            append(text.substring(cursor, start).replace("*", ""))
        }
        val end = text.indexOf("**", start + 2)
        if (end < 0) {
            append(text.substring(start + 2).replace("*", ""))
            break
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(start + 2, end))
        }
        cursor = end + 2
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%d:%02d", min, sec)
}

@Composable
fun AiChatBubble(
    message: AiChatMessage,
    isAudioLoading: Boolean = false,
    isAudioPlaying: Boolean = false,
    isAudioPaused: Boolean = false,
    isVoicePreloaded: Boolean = false,
    isVoicePreloading: Boolean = false,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onReadAloud: () -> Unit
) {
    val formattedText = remember(message.text, message.isUser) {
        if (message.isUser) AnnotatedString(message.text) else formatMarkdownMessage(message.text)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(if (message.isUser) 0.85f else 0.97f),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (message.isUser) 20.dp else 6.dp,
                bottomEnd = if (message.isUser) 6.dp else 20.dp
            ),
            borderColor = if (message.isUser) {
                GoldAccentDark.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
            },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (message.isUser && message.isVoiceMessage) {
                    Row(
                        modifier = Modifier.padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Voice message",
                            tint = GoldAccentDark,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Voice Message",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccentDark
                        )
                    }
                }
                if (!message.isUser) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 6.dp)
                        ) {
                            Text(
                                text = if (message.providerLabel.isNotBlank()) {
                                    "Hidayah AI • ${message.providerLabel}"
                                } else {
                                    "Hidayah AI"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (message.isStreaming) {
                                Spacer(modifier = Modifier.width(6.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.6.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (!message.isStreaming && isVoicePreloaded) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF16A34A).copy(alpha = 0.14f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF16A34A))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Voice Ready",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF16A34A)
                                    )
                                }
                            }
                        } else if (isVoicePreloading && !isAudioPlaying && !isAudioPaused) {
                            Text(
                                text = "Preparing voice...",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!message.isUser && message.isStreaming && message.text.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Writing answer...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = formattedText,
                        fontSize = 14.5.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = true
                    )
                }

                if (!message.isUser && message.text.isNotBlank() && !message.isStreaming) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Friendly primary Listen / Pause / Resume pill button (>= 48dp accessible touch height)
                        Surface(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onReadAloud)
                                .testTag("ai_listen_pill_${message.id}"),
                            shape = RoundedCornerShape(14.dp),
                            color = when {
                                isAudioPlaying -> MaterialTheme.colorScheme.primary
                                isAudioPaused -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                            },
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isAudioPlaying) 0.9f else 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when {
                                    isAudioLoading -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(15.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(7.dp))
                                        Text(
                                            text = "Loading...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    isAudioPlaying -> {
                                        Icon(
                                            imageVector = Icons.Filled.Pause,
                                            contentDescription = "Pause voice",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Pause",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    isAudioPaused -> {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Resume voice",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Resume",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Listen to answer",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Listen",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Copy & Share buttons
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onCopy,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy answer",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onShare,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Share answer",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
