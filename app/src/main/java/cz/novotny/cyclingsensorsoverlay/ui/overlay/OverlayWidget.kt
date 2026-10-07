package cz.novotny.cyclingsensorsoverlay.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.novotny.cyclingsensorsoverlay.domain.model.CombinedSensorState

@Composable
fun OverlayWidget(
    sensorState: CombinedSensorState,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDrag: (dx: Int, dy: Int) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    val radarData = sensorState.radarData
    val activeThreats = radarData?.threats?.filter { it.threatLevel > 0 } ?: emptyList()
    val maxThreatLevel = activeThreats.maxOfOrNull { it.threatLevel } ?: 0
    val closestDistance = activeThreats.minOfOrNull { it.distanceMeters }

    val threatBackgroundColor by animateColorAsState(
        targetValue = when (maxThreatLevel) {
            2 -> Color(0xCCB00020) // High speed threat: Dark Red
            1 -> Color(0xCCE65100) // Approaching threat: Dark Amber/Orange
            else -> Color(0xEE121212) // Safe: Dark Grey translucent
        },
        animationSpec = tween(durationMillis = 300),
        label = "ThreatBgColor"
    )

    val threatBorderColor by animateColorAsState(
        targetValue = when (maxThreatLevel) {
            2 -> Color(0xFFFF5252)
            1 -> Color(0xFFFFB74D)
            else -> Color(0xFF424242)
        },
        animationSpec = tween(durationMillis = 300),
        label = "ThreatBorderColor"
    )

    val earBgColor = Color(0xEE1E1E1E)

    Box(
        modifier = modifier.wrapContentSize()
    ) {
        // Main Content Card with top padding so ears sit on the top corners protruding outside
        Surface(
            modifier = Modifier
                .padding(top = 16.dp, start = 8.dp, end = 8.dp)
                .width(220.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(16.dp))
                .border(1.5.dp, threatBorderColor, RoundedCornerShape(16.dp)),
            color = threatBackgroundColor,
            contentColor = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top Edge Drag Handle Strip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .overlayDragTarget(onDrag, onDragEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    )
                }

                // Compact Sensor Data Card: 3s Power, Cadence, HR
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x22000000))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 3s Power
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Power",
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "3s Power",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.LightGray
                            )
                        }
                        Text(
                            text = sensorState.power3sAverage?.let { "${it} W" } ?: "--",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            ),
                            color = Color(0xFFFFD54F)
                        )
                    }

                    HorizontalDivider(color = Color(0x33FFFFFF), thickness = 0.5.dp)

                    // Cadence & HR side-by-side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cadence
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                                contentDescription = "Cadence",
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CAD ",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.Gray
                            )
                            Text(
                                text = sensorState.powerData?.cadence?.toString() ?: "--",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = Color.White
                            )
                        }

                        // HR
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Heart Rate",
                                tint = Color(0xFFE57373),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "HR ",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.Gray
                            )
                            Text(
                                text = sensorState.heartRateData?.bpm?.let { "$it" } ?: "--",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = Color(0xFFE57373)
                            )
                        }
                    }
                }

                // Radar Alert Banner
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(8.dp),
                    color = when (maxThreatLevel) {
                        2 -> Color(0xFFFF1744)
                        1 -> Color(0xFFFF9100)
                        else -> Color(0xFF2E2E2E)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (maxThreatLevel > 0) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Threat Warning",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = when (maxThreatLevel) {
                                    2 -> "FAST VEHICLE!"
                                    1 -> "VEHICLE BEHIND"
                                    else -> "RADAR CLEAR"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = Color.White
                            )
                        }

                        if (maxThreatLevel > 0 && closestDistance != null) {
                            Text(
                                text = "${closestDistance.toInt()}m (${activeThreats.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Top-Left Mouse Ear: Drag Handle
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 0.dp, y = 0.dp)
                .size(36.dp)
                .shadow(4.dp, CircleShape)
                .overlayDragTarget(onDrag, onDragEnd),
            shape = CircleShape,
            color = earBgColor,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag Telemetry Overlay",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Top-Right Mouse Ear: Close Button
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 0.dp, y = 0.dp)
                .size(36.dp)
                .shadow(4.dp, CircleShape),
            shape = CircleShape,
            color = earBgColor,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Telemetry Overlay",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun Modifier.overlayDragTarget(
    onDrag: (dx: Int, dy: Int) -> Unit,
    onDragEnd: () -> Unit
): Modifier {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    return this.pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = { currentOnDragEnd() },
            onDragCancel = { currentOnDragEnd() }
        ) { change, dragAmount ->
            change.consume()
            currentOnDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
        }
    }
}

@Preview
@Composable
private fun OverlayWidgetPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .background(Color.DarkGray)
                .padding(16.dp)
        ) {
            OverlayWidget(
                sensorState = CombinedSensorState(),
                onCloseClick = {}
            )
        }
    }
}
