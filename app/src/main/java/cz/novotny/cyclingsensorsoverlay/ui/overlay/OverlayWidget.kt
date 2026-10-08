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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Speed
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
import cz.novotny.cyclingsensorsoverlay.domain.model.HeartRateData
import cz.novotny.cyclingsensorsoverlay.domain.model.PowerData
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarData
import cz.novotny.cyclingsensorsoverlay.domain.model.RadarThreat
import cz.novotny.cyclingsensorsoverlay.domain.model.ThreatLevel
import cz.novotny.cyclingsensorsoverlay.ui.theme.CyclingSensorsOverlayTheme
import cz.novotny.cyclingsensorsoverlay.ui.theme.overlayColors
import java.util.Locale

@Composable
fun OverlayWidget(
    sensorState: CombinedSensorState,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDrag: (dx: Int, dy: Int) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    val radarData = sensorState.radarData
    val activeThreats = radarData?.threats?.filter { it.threatLevel != ThreatLevel.NONE } ?: emptyList()
    val maxThreatLevel = activeThreats.maxOfOrNull { it.threatLevel } ?: ThreatLevel.NONE
    val closestDistance = activeThreats.minOfOrNull { it.distanceMeters }

    val overlayColors = MaterialTheme.overlayColors

    val threatBorderColor by animateColorAsState(
        targetValue = when (maxThreatLevel) {
            ThreatLevel.HIGH_SPEED -> overlayColors.threatHighSpeedBorder
            ThreatLevel.APPROACHING -> overlayColors.threatApproachingBorder
            ThreatLevel.NONE -> overlayColors.threatSafeBorder
        },
        animationSpec = tween(durationMillis = 300),
        label = "ThreatBorderColor"
    )

    val radarBannerBg = when (maxThreatLevel) {
        ThreatLevel.HIGH_SPEED -> overlayColors.threatHighSpeedBorder.copy(alpha = 0.15f)
        ThreatLevel.APPROACHING -> overlayColors.threatApproachingBorder.copy(alpha = 0.15f)
        ThreatLevel.NONE -> overlayColors.sensorCardBackground
    }

    val radarBannerBorder = when (maxThreatLevel) {
        ThreatLevel.HIGH_SPEED -> overlayColors.threatHighSpeedBorder
        ThreatLevel.APPROACHING -> overlayColors.threatApproachingBorder
        ThreatLevel.NONE -> overlayColors.overlayDivider
    }

    val radarBannerContentColor = when (maxThreatLevel) {
        ThreatLevel.HIGH_SPEED -> overlayColors.threatHighSpeedBorder
        ThreatLevel.APPROACHING -> overlayColors.threatApproachingBorder
        ThreatLevel.NONE -> Color.LightGray
    }

    Box(
        modifier = modifier
            .wrapContentSize()
            .overlayDragTarget(onDrag, onDragEnd)
    ) {
        // Main Content Card with top padding so ears sit on the top corners protruding outside
        Surface(
            modifier = Modifier
                .padding(top = 16.dp, start = 8.dp, end = 8.dp)
                .width(220.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (maxThreatLevel != ThreatLevel.NONE) 2.dp else 1.5.dp,
                    color = threatBorderColor,
                    shape = RoundedCornerShape(16.dp)
                ),
            color = overlayColors.threatSafeBg,
            contentColor = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Compact Sensor Data Card: 3s Power & Speed, Cadence & HR
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(overlayColors.sensorCardBackground)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 3s Power & Current Speed on same line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 3s Power
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Power",
                                tint = overlayColors.power,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "3s ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.LightGray
                            )
                            Text(
                                text = sensorState.power3sAverage?.let { "$it W" } ?: "--",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                ),
                                color = overlayColors.power
                            )
                        }

                        // Current Speed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed",
                                tint = overlayColors.speed,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "SPD ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.LightGray
                            )
                            Text(
                                text = sensorState.effectiveSpeedKmh?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                ),
                                color = overlayColors.speed
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "km/h",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = Color.Gray
                            )
                        }
                    }

                    HorizontalDivider(color = overlayColors.overlayDivider, thickness = 0.5.dp)

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
                                tint = overlayColors.cadence,
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
                                tint = overlayColors.heartRate,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "HR ",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.Gray
                            )
                            Text(
                                text = sensorState.heartRateData?.bpm?.toString() ?: "--",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = overlayColors.heartRate
                            )
                        }
                    }
                }

                // Radar Alert Banner with subtle tint background and clear warning border
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .border(1.dp, radarBannerBorder, RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    color = radarBannerBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (maxThreatLevel != ThreatLevel.NONE) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Threat Warning",
                                    tint = radarBannerContentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = when (maxThreatLevel) {
                                    ThreatLevel.HIGH_SPEED -> "FAST VEHICLE!"
                                    ThreatLevel.APPROACHING -> "VEHICLE BEHIND"
                                    ThreatLevel.NONE -> "RADAR CLEAR"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = radarBannerContentColor
                            )
                        }

                        if ((maxThreatLevel != ThreatLevel.NONE) && (closestDistance != null)) {
                            Text(
                                text = "${closestDistance.toInt()}m (${activeThreats.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                ),
                                color = radarBannerContentColor
                            )
                        }
                    }
                }
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
            color = overlayColors.earBackground,
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

@Preview(name = "Overlay - Clear")
@Composable
private fun OverlayWidgetClearPreview() {
    CyclingSensorsOverlayTheme {
        Box(
            modifier = Modifier
                .background(Color.DarkGray)
                .padding(16.dp)
        ) {
            OverlayWidget(
                sensorState = CombinedSensorState(
                    power3sAverage = 210,
                    powerData = PowerData(instantaneousPower = 210, cadence = 85, speedKmh = 28.5f),
                    heartRateData = HeartRateData(bpm = 142),
                    radarData = RadarData(threats = emptyList())
                ),
                onCloseClick = {}
            )
        }
    }
}

@Preview(name = "Overlay - Approaching Threat")
@Composable
private fun OverlayWidgetApproachingPreview() {
    CyclingSensorsOverlayTheme {
        Box(
            modifier = Modifier
                .background(Color.DarkGray)
                .padding(16.dp)
        ) {
            OverlayWidget(
                sensorState = CombinedSensorState(
                    power3sAverage = 285,
                    powerData = PowerData(instantaneousPower = 290, cadence = 94, speedKmh = 32.4f),
                    heartRateData = HeartRateData(bpm = 168),
                    radarData = RadarData(
                        threats = listOf(
                            RadarThreat(id = 1, threatLevel = ThreatLevel.APPROACHING, distanceMeters = 75f, speedKmH = 45f)
                        )
                    )
                ),
                onCloseClick = {}
            )
        }
    }
}

@Preview(name = "Overlay - High Speed Threat")
@Composable
private fun OverlayWidgetHighSpeedPreview() {
    CyclingSensorsOverlayTheme {
        Box(
            modifier = Modifier
                .background(Color.DarkGray)
                .padding(16.dp)
        ) {
            OverlayWidget(
                sensorState = CombinedSensorState(
                    power3sAverage = 340,
                    powerData = PowerData(instantaneousPower = 355, cadence = 102, speedKmh = 38.0f),
                    heartRateData = HeartRateData(bpm = 178),
                    radarData = RadarData(
                        threats = listOf(
                            RadarThreat(id = 1, threatLevel = ThreatLevel.HIGH_SPEED, distanceMeters = 30f, speedKmH = 80f),
                            RadarThreat(id = 2, threatLevel = ThreatLevel.APPROACHING, distanceMeters = 90f, speedKmH = 50f)
                        )
                    )
                ),
                onCloseClick = {}
            )
        }
    }
}
