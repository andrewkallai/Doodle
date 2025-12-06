package com.example.doodleapp

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.graphics.createBitmap
import com.example.doodleapp.ui.theme.DoodleAppTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.border

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoodleAppTheme {
                // Just a surface container for the whole screen
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DrawingScreen()
                }
            }
        }
    }
}

data class PathData(val path: Path, val strokeWidth: Float, val color: Color)
enum class DrawingTool { PEN, ERASER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen() {
    val paths = remember { mutableStateListOf<PathData>() }
    var currentStrokeWidth by remember { mutableStateOf(10f) } // Default slightly thicker
    var currentColor by remember { mutableStateOf(Color.Black) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showStrokePicker by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf(DrawingTool.PEN) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var canvasSize by remember { mutableStateOf<Size?>(null) }

    val saveDrawing: () -> Unit = {
        scope.launch {
            canvasSize?.let { size ->
                try {
                    val bitmap = createBitmap(size.width.toInt(), size.height.toInt())
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)

                    val paint = android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.STROKE
                        isAntiAlias = true
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                    }

                    paths.forEach { pathData ->
                        paint.color = pathData.color.toArgb()
                        paint.strokeWidth = pathData.strokeWidth
                        canvas.drawPath(pathData.path.asAndroidPath(), paint)
                    }

                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "Doodle_${System.currentTimeMillis()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Doodles")
                    }

                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        context.contentResolver.openOutputStream(it)?.use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        }
                        val result = snackbarHostState.showSnackbar(
                            message = "Masterpiece saved!",
                            actionLabel = "Open",
                            withDismissAction = true
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "image/png")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Error saving image")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->

        // --- DIALOGS ---
        if (showColorPicker) {
            ColorPickerDialog(
                onDismiss = { showColorPicker = false },
                onColorSelected = {
                    currentColor = it
                    selectedTool = DrawingTool.PEN
                    showColorPicker = false
                }
            )
        }

        if (showStrokePicker) {
            StrokePickerDialog(
                onDismiss = { showStrokePicker = false },
                onStrokeSelected = {
                    currentStrokeWidth = it
                    showStrokePicker = false
                }
            )
        }

        // --- MAIN LAYOUT ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. THE CANVAS (Full Screen Layer)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .clipToBounds()
                    .onSizeChanged { canvasSize = it.toSize() }
                    .pointerInput(true) {
                        detectDragGestures(
                            onDragStart = {
                                paths.add(
                                    PathData(
                                        path = Path().apply { moveTo(it.x, it.y) },
                                        strokeWidth = currentStrokeWidth,
                                        color = if (selectedTool == DrawingTool.ERASER) Color.White else currentColor
                                    )
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val lastPathData = paths.last()
                                val newPath = Path().apply {
                                    addPath(lastPathData.path)
                                    lineTo(change.position.x, change.position.y)
                                }
                                paths[paths.size - 1] = lastPathData.copy(path = newPath)
                            }
                        )
                    }
            ) {
                paths.forEach { pathData ->
                    drawPath(
                        path = pathData.path,
                        color = pathData.color,
                        style = Stroke(
                            width = pathData.strokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }
            }

            // 2. THE FLOATING TOOLBAR (Bottom Layer)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                    .shadow(8.dp, RoundedCornerShape(50)), // Soft shadow
                shape = RoundedCornerShape(percent = 50), // Pill shape
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // Stroke Picker
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { showStrokePicker = true }
                            .background(Color.LightGray.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .size((currentStrokeWidth / 1.5).coerceIn(4.0, 24.0).dp)
                                .background(Color.Black, CircleShape)
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(24.dp))

                    // Color / Pen Tool
                    IconButton(
                        onClick = {
                            selectedTool = DrawingTool.PEN
                            showColorPicker = true
                        },
                        modifier = Modifier
                            .background(
                                if(selectedTool == DrawingTool.PEN) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                CircleShape
                            )
                    ) {
                        // Showing the actual current color inside the pen icon container
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(currentColor, CircleShape)
                                .border(1.dp, Color.Gray, CircleShape)
                        )
                    }

                    // Eraser Tool
                    IconButton(
                        onClick = { selectedTool = DrawingTool.ERASER },
                        modifier = Modifier
                            .background(
                                if(selectedTool == DrawingTool.ERASER) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                CircleShape
                            )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Eraser", tint = if(selectedTool == DrawingTool.ERASER) MaterialTheme.colorScheme.primary else Color.Gray)
                        // Note: Using 'Delete' icon as a generic tool holder, but functionally behaves as eraser logic
                    }

                    VerticalDivider(modifier = Modifier.height(24.dp))

                    // Clear Canvas
                    IconButton(onClick = { paths.clear() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                    }

                    // Save Button
                    FilledTonalIconButton(
                        onClick = { saveDrawing() },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ColorPickerDialog(onDismiss: () -> Unit, onColorSelected: (Color) -> Unit) {
    val colors = listOf(
        Color(0xFFFF0000), Color(0xFFFFA500), Color(0xFFFFFF00), Color(0xFF7FFF00),
        Color(0xFF00FF00), Color(0xFF00FF7F), Color(0xFF00FFFF), Color(0xFF007FFF),
        Color(0xFF0000FF), Color(0xFF7F00FF), Color(0xFFFF00FF), Color(0xFFFF007F),
        Color.Black, Color.DarkGray
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a Color") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(colors) { color ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onColorSelected(color) }
                            .border(1.dp, Color.LightGray, CircleShape)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun StrokePickerDialog(onDismiss: () -> Unit, onStrokeSelected: (Float) -> Unit) {
    val strokeWidths = listOf(5f, 10f, 15f, 25f, 40f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Brush Size") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                strokeWidths.forEach { stroke ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onStrokeSelected(stroke) }
                            .border(1.dp, Color.LightGray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size((stroke / 1.5f).dp)
                                .background(Color.Black, CircleShape)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}