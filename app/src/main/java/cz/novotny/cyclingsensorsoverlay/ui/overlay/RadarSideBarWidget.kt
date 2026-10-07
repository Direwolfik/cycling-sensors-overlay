package cz.novotny.cyclingsensorsoverlay.ui.overlay

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarData
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarThreat
import cz.novotny.cyclingsensorsoverlay.domain.model.ThreatLevel
import cz.novotny.cyclingsensorsoverlay.ui.theme.CyclingSensorsOverlayTheme
import cz.novotny.cyclingsensorsoverlay.ui.theme.overlayColors

@Composable
fun RadarSideBarWidget(
    radarData: RadarData?,
    modifier: Modifier = Modifier,
    heightDp: Int = 260,
    widthDp: Int = 56,
    onCloseClick: (() -> Unit)? = null,
    onDrag: (dx: Int, dy: Int) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    val activeThreats = radarData?.threats?.filter { it.threatLevel != ThreatLevel.NONE } ?: emptyList()
    val maxThreatLevel = activeThreats.maxOfOrNull { it.threatLevel } ?: ThreatLevel.NONE

    val overlayColors = MaterialTheme.overlayColors

    val borderAndGlowColor = when (maxThreatLevel) {
        ThreatLevel.HIGH_SPEED -> overlayColors.threatHighSpeedBanner
        ThreatLevel.APPROACHING -> overlayColors.threatApproachingBanner
        ThreatLevel.NONE -> overlayColors.radarBorderClear
    }

    Box(
        modifier = modifier
            .wrapContentSize()
            .overlayDragTarget(onDrag, onDragEnd)
    ) {
        // Main Radar Container with top padding so ears sit on top corners protruding outside
        Surface(
            modifier = Modifier
                .padding(top = 16.dp, start = 8.dp, end = 8.dp)
                .width(widthDp.dp)
                .height(heightDp.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = if (maxThreatLevel != ThreatLevel.NONE) 2.dp else 1.dp,
                    color = borderAndGlowColor,
                    shape = RoundedCornerShape(24.dp)
                ),
            color = overlayColors.radarBackground,
            contentColor = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 10.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Central Vertical Radar Line & Threat Track Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Vertical Guide Line (150m top to 0m bottom)
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x80FFFFFF),
                                        Color(0x40FFFFFF),
                                        when (maxThreatLevel) {
                                            ThreatLevel.HIGH_SPEED -> overlayColors.threatHighSpeedBanner
                                            ThreatLevel.APPROACHING -> overlayColors.threatApproachingBanner
                                            ThreatLevel.NONE -> overlayColors.riderNormal
                                        }
                                    )
                                )
                            )
                    )

                    // Subtitle Tick Marks (100m and 50m)
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(1.dp)
                                .background(Color(0x40FFFFFF))
                        )
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(1.dp)
                                .background(Color(0x40FFFFFF))
                        )
                    }

                    // Plot Threat Dots
                    activeThreats.forEach { threat ->
                        RadarThreatDot(
                            threat = threat,
                            maxRangeMeters = 150f
                        )
                    }
                }

                // Rider Icon at Bottom (0m position)
                Surface(
                    shape = CircleShape,
                    color = when (maxThreatLevel) {
                        ThreatLevel.HIGH_SPEED -> overlayColors.threatHighSpeedBanner
                        ThreatLevel.APPROACHING -> overlayColors.threatApproachingBanner
                        ThreatLevel.NONE -> overlayColors.riderNormal
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                            contentDescription = "Rider (0m)",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Top-Right Mouse Ear: Close Button
        if (onCloseClick != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 0.dp, y = 0.dp)
                    .size(36.dp)
                    .shadow(4.dp, CircleShape),
                shape = CircleShape,
                color = overlayColors.earBackground,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                IconButton(
                    onClick = onCloseClick,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Radar Sidebar",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarThreatDot(
    threat: RadarThreat,
    maxRangeMeters: Float
) {
    val overlayColors = MaterialTheme.overlayColors
    val targetFraction = (threat.distanceMeters / maxRangeMeters).coerceIn(0f, 1f)

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 180),
        label = "ThreatDistanceAnimation_${threat.id}"
    )

    val topFraction = (1f - animatedFraction).coerceIn(0f, 1f)

    val dotColor = if (threat.threatLevel == ThreatLevel.HIGH_SPEED) {
        overlayColors.threatHighSpeedBanner
    } else {
        overlayColors.threatDotApproaching
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val totalHeight = maxHeight
        val yOffset = totalHeight * topFraction

        Box(
            modifier = Modifier
                .offset(y = (yOffset - 12.dp).coerceAtLeast(0.dp))
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = dotColor,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.5.dp, Color.White),
                    modifier = Modifier.size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (threat.threatLevel == ThreatLevel.HIGH_SPEED) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = overlayColors.threatPillBackground
                ) {
                    Text(
                        text = "${threat.distanceMeters.toInt()}m",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.dp)
                    )
                }
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

@Preview(name = "Radar Sidebar - Clear")
@Composable
private fun RadarSideBarClearPreview() {
    CyclingSensorsOverlayTheme {
        Box(
            modifier = Modifier
                .background(Color.DarkGray)
                .padding(16.dp)
        ) {
            RadarSideBarWidget(
                radarData = RadarData(threats = emptyList()),
                onCloseClick = {}
            )
        }
    }
}

@Preview(name = "Radar Sidebar - Approaching Threat")
@Composable
private fun RadarSideBarApproachingPreview() {
    CyclingSensorsOverlayTheme {
        Box(
            modifier = Modifier
                .background(Color.DarkGray)
                .padding(16.dp)
        ) {
            RadarSideBarWidget(
                radarData = RadarData(
                    threats = listOf(
                        RadarThreat(id = 1, threatLevel = ThreatLevel.APPROACHING, distanceMeters = 80f, speedKmH = 40f)
                    )
                ),
                onCloseClick = {}
            )
        }
    }
}

@Preview(name = "Radar Sidebar - High Speed Threat")
@Composable
private fun RadarSideBarHighSpeedPreview() {
    CyclingSensorsOverlayTheme {
        Box(
            modifier = Modifier
                .background(Color.DarkGray)
                .padding(16.dp)
        ) {
            RadarSideBarWidget(
                radarData = RadarData(
                    threats = listOf(
                        RadarThreat(id = 1, threatLevel = ThreatLevel.HIGH_SPEED, distanceMeters = 35f, speedKmH = 85f)
                    )
                ),
                onCloseClick = {}
            )
        }
    }
}

@Preview(name = "Radar Sidebar - Multiple Threats")
@Composable
private fun RadarSideBarMultipleThreatsPreview() {
    CyclingSensorsOverlayTheme {
        Box(
            modifier = Modifier
                .background(Color.DarkGray)
                .padding(16.dp)
        ) {
            RadarSideBarWidget(
                radarData = RadarData(
                    threats = listOf(
                        RadarThreat(id = 1, threatLevel = ThreatLevel.HIGH_SPEED, distanceMeters = 25f, speedKmH = 75f),
                        RadarThreat(id = 2, threatLevel = ThreatLevel.APPROACHING, distanceMeters = 110f, speedKmH = 45f)
                    )
                ),
                onCloseClick = {}
            )
        }
    }
}
