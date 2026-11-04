package com.example.glooplauncher

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GloopTVApp()
        }
    }
}

data class AppModel(
    val label: String,
    val packageName: String
)

@Composable
fun GloopTVApp() {
    val context = LocalContext.current
    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        resolveInfos.map {
            AppModel(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName
            )
        }.distinctBy { it.packageName }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Left Column: Character + Title
            Column(
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                GloopCharacter()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Gloop TV",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Right Column: App Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 130.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(apps) { app ->
                    AppTile(app = app) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GloopCharacter() {
    // Gentle floating bob animation
    val infiniteTransition = rememberInfiniteTransition(label = "bob")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(y = offsetY.dp)
            .size(120.dp)
    ) {
        // Character Body (Gloop Blob)
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(
                    color = Color(0xFF10B981), // Emerald green slime/blob color
                    shape = RoundedCornerShape(topStart = 45.dp, topEnd = 45.dp, bottomStart = 35.dp, bottomEnd = 35.dp)
                )
        )

        // Eyes and Smile Canvas
        Canvas(modifier = Modifier.size(100.dp)) {
            val eyeRadius = 6.dp.toPx()
            
            // Left eye
            drawCircle(
                color = Color.White,
                radius = eyeRadius,
                center = Offset(size.width * 0.35f, size.height * 0.42f)
            )
            drawCircle(
                color = Color.Black,
                radius = eyeRadius * 0.5f,
                center = Offset(size.width * 0.37f, size.height * 0.42f)
            )

            // Right eye
            drawCircle(
                color = Color.White,
                radius = eyeRadius,
                center = Offset(size.width * 0.65f, size.height * 0.42f)
            )
            drawCircle(
                color = Color.Black,
                radius = eyeRadius * 0.5f,
                center = Offset(size.width * 0.67f, size.height * 0.42f)
            )

            // Smile
            drawArc(
                color = Color(0xFF064E3B),
                startAngle = 10f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(size.width * 0.38f, size.height * 0.52f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.24f, size.height * 0.16f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            )
        }
    }
}

@Composable
fun AppTile(app: AppModel, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale = if (isFocused) 1.08f else 1.0f
    val backgroundColor = if (isFocused) Color(0xFF3B82F6) else Color(0xFF1E293B)
    val textColor = if (isFocused) Color.White else Color(0xFFCBD5E1)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(130.dp, 90.dp)
            .scale(scale)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(10.dp)
    ) {
        Text(
            text = app.label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

