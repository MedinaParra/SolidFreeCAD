package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.generator.FreeCadMacroGenerator
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.theme.MyApplicationTheme
import java.util.UUID

enum class AppScreen {
    WELCOME,
    NEW_DOCUMENT,
    WORKSPACE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.WELCOME) }
                var projectState by remember { mutableStateOf(ProjectState()) }

                when (currentScreen) {
                    AppScreen.WELCOME -> {
                        WelcomeScreen(
                            onNewClick = {
                                currentScreen = AppScreen.NEW_DOCUMENT
                            }
                        )
                    }
                    AppScreen.NEW_DOCUMENT -> {
                        NewDocumentScreen(
                            onCancel = {
                                currentScreen = AppScreen.WELCOME
                            },
                            onSelectPart = {
                                projectState = ProjectState().copy(projectName = "Pieza_Libre_${UUID.randomUUID().toString().take(4)}")
                                currentScreen = AppScreen.WORKSPACE
                            }
                        )
                    }
                    AppScreen.WORKSPACE -> {
                        CadWorkspaceScreen(
                            initialState = projectState,
                            onGoBackToWelcome = {
                                currentScreen = AppScreen.WELCOME
                            },
                            onNewClick = {
                                currentScreen = AppScreen.NEW_DOCUMENT
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(onNewClick: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // Elegant SolidWorks desktop-inspired top bar - highly optimized for compact mobile headers
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFECEFF1))
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo brand block
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Red branding badge
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFD32F2F), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "SF",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "SolidFreeCAD",
                            color = Color(0xFF37474F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Middle toolbar icons - positioned higher and compact
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Home icon
                        IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Home, "Inicio", tint = Color(0xFF78909C), modifier = Modifier.size(16.dp))
                        }
                        
                        // New Document Button (clickable!)
                        IconButton(
                            onClick = onNewClick,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFFFFEB3B).copy(alpha = 0.3f), CircleShape)
                                .border(1.dp, Color(0xFFFFCC00), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NoteAdd,
                                contentDescription = "Nuevo Documento",
                                tint = Color(0xFFD84315),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Open Folder icon
                        IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.FolderOpen, "Abrir", tint = Color(0xFF78909C), modifier = Modifier.size(16.dp))
                        }

                        // Save icon
                        IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Save, "Guardar", tint = Color(0xFF78909C), modifier = Modifier.size(16.dp))
                        }

                        // Options / Settings icon
                        IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Settings, "Opciones", tint = Color(0xFF78909C), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Right search bar
                    Row(
                        modifier = Modifier
                            .width(130.dp)
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .border(0.5.dp, Color(0xFFCFD8DC), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Search, null, tint = Color(0xFF90A4AE), modifier = Modifier.size(12.dp))
                        Text(
                            text = "Comandos",
                            color = Color(0xFFB0BEC5),
                            fontSize = 10.sp
                        )
                    }
                }
                Divider(color = Color(0xFFCFD8DC), thickness = 1.dp)
            }
        },
        bottomBar = {
            // Desk Status Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFECEFF1))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Abre el diálogo de bienvenida. Presiona 'Nuevo' para iniciar un nuevo diseño.",
                        fontSize = 11.sp,
                        color = Color(0xFF546E7A)
                    )
                    Text(
                        text = "SOLIDWORKS 2026 SP0",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF90A4AE)
                    )
                }
            }
        }
    ) { innerPadding ->
        // Clean central branding area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF5F7FA)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Large styled red SolidFreeCAD logo ribbon using Canvas
                Canvas(
                    modifier = Modifier
                        .size(180.dp)
                        .clickable { onNewClick() }
                ) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f

                    // Draw an elegant stylized red curves (3D Ribbon)
                    val redPath1 = Path().apply {
                        moveTo(cx - 50f, cy - 30f)
                        quadraticBezierTo(cx - 70f, cy - 70f, cx - 10f, cy - 70f)
                        quadraticBezierTo(cx + 40f, cy - 70f, cx + 50f, cy - 20f)
                        quadraticBezierTo(cx + 60f, cy + 20f, cx + 10f, cy + 50f)
                        quadraticBezierTo(cx - 30f, cy + 70f, cx - 50f, cy + 40f)
                        quadraticBezierTo(cx - 65f, cy + 10f, cx - 30f, cy - 10f)
                        close()
                    }
                    drawPath(path = redPath1, color = Color(0xFFD32F2F))

                    // White / light red inner accent ribbon
                    val innerPath = Path().apply {
                        moveTo(cx - 20f, cy - 35f)
                        quadraticBezierTo(cx + 20f, cy - 45f, cx + 30f, cy - 20f)
                        quadraticBezierTo(cx + 35f, cy + 10f, cx + 10f, cy + 30f)
                        quadraticBezierTo(cx - 15f, cy + 45f, cx - 25f, cy + 25f)
                        close()
                    }
                    drawPath(path = innerPath, color = Color(0xFFFFCDD2))
                    
                    // Center geometric core
                    drawCircle(Color(0xFFB71C1C), radius = 18f, center = Offset(cx, cy - 5f))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "SolidFreeCAD",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF455A64),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "2026",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFF78909C),
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onNewClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NoteAdd, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Nuevo Documento", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun NewDocumentScreen(
    onCancel: () -> Unit,
    onSelectPart: () -> Unit
) {
    val context = LocalContext.current
    var selectedTemplate by remember { mutableStateOf("pieza") } // "pieza", "ensamblaje", "dibujo"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 680.dp)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Dialog Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFECEFF1))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nuevo documento de SolidFreeCAD",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF37474F)
                    )
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, "Cerrar", tint = Color(0xFF78909C), modifier = Modifier.size(16.dp))
                    }
                }

                // Templates Grid / Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. PIEZA (Part) - Enabled
                    TemplateOptionCard(
                        modifier = Modifier.weight(1f),
                        title = "Pieza",
                        description = "una representación en 3D de un único componente de diseño",
                        isSelected = selectedTemplate == "pieza",
                        enabled = true,
                        iconContent = {
                            IsometricPieceIcon(
                                modifier = Modifier
                                    .size(90.dp)
                                    .background(Color(0xFFECEFF1), RoundedCornerShape(8.dp))
                            )
                        },
                        onClick = {
                            selectedTemplate = "pieza"
                        }
                    )

                    // 2. ENSAMBLAJE (Assembly) - Disabled / In dev
                    TemplateOptionCard(
                        modifier = Modifier.weight(1f),
                        title = "Ensamblaje",
                        description = "una disposición en 3D de piezas y/o otros ensamblajes",
                        isSelected = selectedTemplate == "ensamblaje",
                        enabled = false,
                        iconContent = {
                            IsometricAssemblyIcon(
                                modifier = Modifier
                                    .size(90.dp)
                                    .background(Color(0xFFECEFF1), RoundedCornerShape(8.dp))
                            )
                        },
                        onClick = {
                            Toast.makeText(context, "Módulo Ensamblaje está en desarrollo", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // 3. DIBUJO (Technical Drawing) - Disabled / In dev
                    TemplateOptionCard(
                        modifier = Modifier.weight(1f),
                        title = "Dibujo",
                        description = "un dibujo técnico en 2D, normalmente de una pieza o de un ensamblaje",
                        isSelected = selectedTemplate == "dibujo",
                        enabled = false,
                        iconContent = {
                            DrawingSheetIcon(
                                modifier = Modifier
                                    .size(90.dp)
                                    .background(Color(0xFFECEFF1), RoundedCornerShape(8.dp))
                            )
                        },
                        onClick = {
                            Toast.makeText(context, "Módulo Dibujo técnico está en desarrollo", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Divider(color = Color(0xFFCFD8DC), thickness = 0.5.dp)

                // Action Buttons Bottom Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancelar", color = Color(0xFF546E7A), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (selectedTemplate == "pieza") {
                                onSelectPart()
                            }
                        },
                        enabled = selectedTemplate == "pieza",
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1B365D),
                            disabledContainerColor = Color(0xFFB0BEC5)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Aceptar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateOptionCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    isSelected: Boolean,
    enabled: Boolean,
    iconContent: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = true) { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF2980B9) else Color(0xFFCFD8DC),
                shape = RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFEBF5FB) else Color.White
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentSize(),
                contentAlignment = Alignment.Center
            ) {
                iconContent()
                if (!enabled) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.White.copy(alpha = 0.5f))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .background(Color(0xFFE74C3C), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PROXIMAMENTE",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (enabled) Color(0xFF2C3E50) else Color(0xFF7F8C8D),
                textAlign = TextAlign.Center
            )

            Text(
                text = description,
                fontSize = 10.sp,
                color = if (enabled) Color(0xFF7F8C8D) else Color(0xFFBDC3C7),
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                modifier = Modifier.height(36.dp)
            )
        }
    }
}

@Composable
fun IsometricPieceIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Let's draw an isometric L-bracket nicely scaled to fit 90dp.
        // Draw 6 shaded faces to construct an isometric 3D step shape!
        
        // 1. Top of the vertical column (light yellow)
        val top1 = Path().apply {
            moveTo(cx - 24f, cy - 20f)
            lineTo(cx - 8f, cy - 28f)
            lineTo(cx + 8f, cy - 20f)
            lineTo(cx - 8f, cy - 12f)
            close()
        }
        drawPath(path = top1, color = Color(0xFFFFF59D)) // Light yellow highlight

        // 2. Left vertical face of column (medium yellow)
        val left1 = Path().apply {
            moveTo(cx - 24f, cy - 20f)
            lineTo(cx - 8f, cy - 12f)
            lineTo(cx - 8f, cy + 12f)
            lineTo(cx - 24f, cy + 4f)
            close()
        }
        drawPath(path = left1, color = Color(0xFFFBC02D)) // Medium yellow shadow

        // 3. Right vertical face of column (dark golden)
        val right1 = Path().apply {
            moveTo(cx - 8f, cy - 12f)
            lineTo(cx + 8f, cy - 20f)
            lineTo(cx + 8f, cy + 4f)
            lineTo(cx - 8f, cy + 12f)
            close()
        }
        drawPath(path = right1, color = Color(0xFFF57F17)) // Dark yellow shadow

        // 4. Top of horizontal step (light yellow)
        val top2 = Path().apply {
            moveTo(cx - 8f, cy + 12f)
            lineTo(cx + 8f, cy + 4f)
            lineTo(cx + 24f, cy + 12f)
            lineTo(cx + 8f, cy + 20f)
            close()
        }
        drawPath(path = top2, color = Color(0xFFFFF59D))

        // 5. Left vertical front face of step (medium yellow)
        val left2 = Path().apply {
            moveTo(cx - 8f, cy + 12f)
            lineTo(cx + 8f, cy + 20f)
            lineTo(cx + 8f, cy + 36f)
            lineTo(cx - 8f, cy + 28f)
            close()
        }
        drawPath(path = left2, color = Color(0xFFFBC02D))

        // 6. Right vertical front face of step (dark golden)
        val right2 = Path().apply {
            moveTo(cx + 8f, cy + 20f)
            lineTo(cx + 24f, cy + 12f)
            lineTo(cx + 24f, cy + 28f)
            lineTo(cx + 8f, cy + 36f)
            close()
        }
        drawPath(path = right2, color = Color(0xFFF57F17))
    }
}

@Composable
fun IsometricAssemblyIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Draw yellow L-bracket in background
        val top1 = Path().apply {
            moveTo(cx - 24f, cy - 20f)
            lineTo(cx - 8f, cy - 28f)
            lineTo(cx + 8f, cy - 20f)
            lineTo(cx - 8f, cy - 12f)
            close()
        }
        drawPath(path = top1, color = Color(0xFFFFF59D))

        val left1 = Path().apply {
            moveTo(cx - 24f, cy - 20f)
            lineTo(cx - 8f, cy - 12f)
            lineTo(cx - 8f, cy + 12f)
            lineTo(cx - 24f, cy + 4f)
            close()
        }
        drawPath(path = left1, color = Color(0xFFFBC02D))

        val right1 = Path().apply {
            moveTo(cx - 8f, cy - 12f)
            lineTo(cx + 8f, cy - 20f)
            lineTo(cx + 8f, cy + 4f)
            lineTo(cx - 8f, cy + 12f)
            close()
        }
        drawPath(path = right1, color = Color(0xFFF57F17))

        // Left vertical front of step
        val left2 = Path().apply {
            moveTo(cx - 8f, cy + 12f)
            lineTo(cx + 8f, cy + 20f)
            lineTo(cx + 8f, cy + 36f)
            lineTo(cx - 8f, cy + 28f)
            close()
        }
        drawPath(path = left2, color = Color(0xFFFBC02D))

        // Blue cubic block assembled on top of the step (offsets to sit on right)
        // Sitting on: cx + 8f, cy + 4f
        val bOffsetLineX = 8f
        val bOffsetLineY = -4f

        val blueTop = Path().apply {
            moveTo(cx + bOffsetLineX, cy + bOffsetLineY - 12f)
            lineTo(cx + bOffsetLineX + 16f, cy + bOffsetLineY - 20f)
            lineTo(cx + bOffsetLineX + 32f, cy + bOffsetLineY - 12f)
            lineTo(cx + bOffsetLineX + 16f, cy + bOffsetLineY - 4f)
            close()
        }
        drawPath(path = blueTop, color = Color(0xFF90CAF9)) // Light Blue

        val blueLeft = Path().apply {
            moveTo(cx + bOffsetLineX, cy + bOffsetLineY - 12f)
            lineTo(cx + bOffsetLineX + 16f, cy + bOffsetLineY - 4f)
            lineTo(cx + bOffsetLineX + 16f, cy + bOffsetLineY + 12f)
            lineTo(cx + bOffsetLineX, cy + bOffsetLineY + 4f)
            close()
        }
        drawPath(path = blueLeft, color = Color(0xFF2196F3)) // Medium Blue

        val blueRight = Path().apply {
            moveTo(cx + bOffsetLineX + 16f, cy + bOffsetLineY - 4f)
            lineTo(cx + bOffsetLineX + 32f, cy + bOffsetLineY - 12f)
            lineTo(cx + bOffsetLineX + 32f, cy + bOffsetLineY + 4f)
            lineTo(cx + bOffsetLineX + 16f, cy + bOffsetLineY + 12f)
            close()
        }
        drawPath(path = blueRight, color = Color(0xFF0D47A1)) // Dark Blue
    }
}

@Composable
fun DrawingSheetIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Draw drawing template page (White card with grey border)
        val borderPad = 15f
        drawRect(
            color = Color.White,
            topLeft = Offset(borderPad, borderPad),
            size = size.copy(width = size.width - borderPad * 2, height = size.height - borderPad * 2)
        )
        drawRect(
            color = Color(0xFFCFD8DC),
            topLeft = Offset(borderPad, borderPad),
            size = size.copy(width = size.width - borderPad * 2, height = size.height - borderPad * 2),
            style = Stroke(width = 2f)
        )

        // Title box corner at bottom right
        drawRect(
            color = Color(0xFFECEFF1),
            topLeft = Offset(size.width - borderPad - 25f, size.height - borderPad - 15f),
            size = size.copy(width = 25f, height = 15f)
        )

        // Draw blueprint-like CAD details (front view circle, top view rectangle)
        drawCircle(
            color = Color(0xFF1976D2),
            radius = 12f,
            center = Offset(cx - 10f, cy - 10f),
            style = Stroke(width = 1.5f)
        )
        
        drawRect(
            color = Color(0xFF1976D2),
            topLeft = Offset(cx + 8f, cy - 18f),
            size = size.copy(width = 18f, height = 14f),
            style = Stroke(width = 1.5f)
        )

        drawRect(
            color = Color(0xFF1976D2),
            topLeft = Offset(cx - 16f, cy + 8f),
            size = size.copy(width = 24f, height = 10f),
            style = Stroke(width = 1.5f)
        )
    }
}

@Composable
fun VerticalTextTab(
    text: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(75.dp)
            .clickable(enabled = enabled) { onClick() }
            .background(if (isSelected) Color(0xFFCFD8DC) else Color.Transparent)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF1B365D) else if (enabled) Color(0xFF546E7A) else Color(0xFFB0BEC5),
            modifier = Modifier.rotate(-90f),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun CompactPresetChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(0.5.dp, Color(0xFFBDC3C7), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B365D)
        )
    }
}

@Composable
fun CadWorkspaceScreen(
    initialState: ProjectState,
    onGoBackToWelcome: () -> Unit,
    onNewClick: () -> Unit
) {
    val context = LocalContext.current

    // Core CAD design state
    var state by remember { mutableStateOf(initialState) }

    LaunchedEffect(initialState) {
        state = initialState
    }

    // UI state controllers
    var isLeftPanelVisible by remember { mutableStateOf(true) }
    var isBottomToolbarVisible by remember { mutableStateOf(true) }
    var isTreeExpanded by remember { mutableStateOf(true) }
    var activeSketchTool by remember { mutableStateOf<String?>(null) } // "Line", "Circle", "Rectangle", "Dimension", null
    var activePropertyOpType by remember { mutableStateOf<OperationType?>(null) } // Set to show PropertyManager
    var showMacroDialog by remember { mutableStateOf(false) }

    // Floating helpful alert banner with automatic timing fading out
    var bannerMessage by remember { mutableStateOf<String?>("¡Bienvenido! Selecciona 'Plano Alzado' y dibuja un boceto 2D.") }

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            kotlinx.coroutines.delay(3500)
            bannerMessage = null
        }
    }

    // Helper functions
    val resetProject = {
        state = ProjectState().copy(projectName = "Pieza_Libre_${UUID.randomUUID().toString().take(4)}")
        activeSketchTool = null
        activePropertyOpType = null
        bannerMessage = "Proyecto reiniciado. Dibuja sobre el plano XY."
    }

    val loadPresetBracket = {
        loadDemoBracket { newState -> state = newState }
        activeSketchTool = null
        activePropertyOpType = null
        bannerMessage = "Cargada Pieza de Demostración: Soporte con Vaciado."
    }

    val loadPresetPulley = {
        loadDemoPulley { newState -> state = newState }
        activeSketchTool = null
        activePropertyOpType = null
        bannerMessage = "Cargada Pieza de Demostración: Polea Revolucionada."
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                // Main Header styled like SolidWorks top bar (light grey with red logo and action shortcuts)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFECEFF1))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left SolidFreeCAD Branding Block
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFD32F2F), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "SF",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text(
                                    text = "SolidFreeCAD",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF37474F)
                                )
                                Text(
                                    text = state.projectName,
                                    fontSize = 8.sp,
                                    color = Color(0xFF546E7A),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Top bar Quick Actions (Home, New, Reset, View Mode, Export)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Home Shortcut to Welcome Screen
                            IconButton(
                                onClick = onGoBackToWelcome,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Inicio / Atrás",
                                    tint = Color(0xFF546E7A),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // New Document Shortcut
                            IconButton(
                                onClick = onNewClick,
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(Color(0xFFFFEB3B).copy(alpha = 0.2f), CircleShape)
                                    .border(0.5.dp, Color(0xFFFFCC00), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NoteAdd,
                                    contentDescription = "Nuevo Documento",
                                    tint = Color(0xFFD84315),
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Reset Button
                            IconButton(
                                onClick = resetProject,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Limpiar Proyecto",
                                    tint = Color(0xFF546E7A),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            // View Mode Toggle (2D sketch vs 3D solid)
                            IconButton(
                                onClick = {
                                    val is3D = !state.viewMode3D
                                    var currentSketches = state.sketches
                                    var selectedSkId = state.selectedSketchId
                                    if (!is3D && currentSketches.isEmpty()) {
                                        val newSketch = CadSketch(
                                            name = "Croquis ${currentSketches.size + 1}",
                                            plane = state.activePlane
                                        )
                                        currentSketches = currentSketches + newSketch
                                        selectedSkId = newSketch.id
                                        bannerMessage = "Croquis creado. Elige una herramienta abajo."
                                    } else if (!is3D) {
                                        bannerMessage = "Modo Croquis. Arrastra en la cuadrícula para trazar."
                                    } else {
                                        activeSketchTool = null
                                        bannerMessage = "Modo 3D. Gira la perspectiva arrastrando."
                                    }

                                    state = state.copy(
                                        viewMode3D = is3D,
                                        sketches = currentSketches,
                                        selectedSketchId = selectedSkId
                                    )
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(if (state.viewMode3D) Color(0xFF2980B9) else Color(0xFF27AE60), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (state.viewMode3D) Icons.Default.ViewInAr else Icons.Default.Edit,
                                    contentDescription = "Alternar Modo 2D/3D",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Export Button
                            IconButton(
                                onClick = {
                                    if (state.sketches.isEmpty() && state.operations.isEmpty()) {
                                        Toast.makeText(context, "Dibuja o añade operaciones primero", Toast.LENGTH_SHORT).show()
                                    } else {
                                        showMacroDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(Color(0xFFE67E22), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Código Macro",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Presets Bar (Quick demos selector)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE2E9F0))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ejemplos:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B365D)
                    )
                    
                    CompactPresetChip(
                        text = "Soporte",
                        onClick = loadPresetBracket
                    )

                    CompactPresetChip(
                        text = "Polea",
                        onClick = loadPresetPulley
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = state.projectName,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF566573)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main CAD Viewport drawing space
            Viewport3D(
                state = state,
                onUpdateCamera = { yaw, pitch, scale, ox, oy ->
                    state = state.copy(yaw = yaw, pitch = pitch, scale = scale, offsetX = ox, offsetY = oy)
                },
                activeSketchTool = activeSketchTool,
                onAddSketchEntity = { ent ->
                    val skId = state.selectedSketchId ?: return@Viewport3D
                    val updatedSketches = state.sketches.map { sk ->
                        if (sk.id == skId) {
                            sk.copy(entities = sk.entities + ent)
                        } else sk
                    }
                    state = state.copy(sketches = updatedSketches)
                    bannerMessage = "Elemento añadido al croquis. Selecciona otro o ve a 'Operaciones' para extruirlo."
                },
                modifier = Modifier.fillMaxSize()
            )

            // Left Side Panels Drawer: FeatureTree & PropertyManager (Slide responsive)
            AnimatedVisibility(
                visible = isLeftPanelVisible,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.TopStart)
            ) {
                Row(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    // Docked SolidWorks-style vertical tabs column
                    if (activePropertyOpType == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(28.dp)
                                .background(Color(0xFFECEFF1))
                                .border(0.5.dp, Color(0xFFCFD8DC)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            // Operations tab
                            VerticalTextTab(
                                text = "Operaciones",
                                isSelected = state.viewMode3D,
                                onClick = {
                                    state = state.copy(viewMode3D = true)
                                    activeSketchTool = null
                                    bannerMessage = "Modo 3D. Selecciona 'Operaciones' abajo para dar volumen."
                                }
                            )
                            
                            // Croquis tab
                            VerticalTextTab(
                                text = "Croquis",
                                isSelected = !state.viewMode3D,
                                onClick = {
                                    var currentSketches = state.sketches
                                    var selectedSkId = state.selectedSketchId
                                    if (currentSketches.isEmpty()) {
                                        val newSketch = CadSketch(
                                            name = "Croquis ${currentSketches.size + 1}",
                                            plane = state.activePlane
                                        )
                                        currentSketches = currentSketches + newSketch
                                        selectedSkId = newSketch.id
                                    }
                                    state = state.copy(
                                        viewMode3D = false,
                                        sketches = currentSketches,
                                        selectedSketchId = selectedSkId
                                    )
                                    bannerMessage = "Modo Croquis. Trazar figuras usando la barra inferior."
                                }
                            )

                            VerticalTextTab(text = "Superficies", isSelected = false, enabled = false)
                            VerticalTextTab(text = "Chapa Metal", isSelected = false, enabled = false)

                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { isLeftPanelVisible = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Ocultar panel",
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    // Sliding Feature Tree (SolidWorks design tree)
                    AnimatedVisibility(
                        visible = isTreeExpanded && activePropertyOpType == null,
                        enter = slideInHorizontally(initialOffsetX = { -it }),
                        exit = slideOutHorizontally(targetOffsetX = { -it })
                    ) {
                        FeatureTree(
                            state = state,
                            onSelectSketch = { skId ->
                                state = state.copy(selectedSketchId = skId, selectedOperationId = null)
                                bannerMessage = "Croquis seleccionado. Clic 'Ir a Croquis' arriba para editar boceto."
                            },
                            onSelectOperation = { opId ->
                                state = state.copy(selectedOperationId = opId, selectedSketchId = null)
                                bannerMessage = "Operación seleccionada."
                            },
                            onDeleteSketch = { skId ->
                                val updatedSketches = state.sketches.filterNot { it.id == skId }
                                val updatedOps = state.operations.filterNot { it.sketchId == skId }
                                state = state.copy(
                                    sketches = updatedSketches,
                                    operations = updatedOps,
                                    selectedSketchId = if (state.selectedSketchId == skId) null else state.selectedSketchId
                                )
                                bannerMessage = "Croquis borrado."
                            },
                            onDeleteOperation = { opId ->
                                val updatedOps = state.operations.filterNot { it.id == opId }
                                state = state.copy(
                                    operations = updatedOps,
                                    selectedOperationId = if (state.selectedOperationId == opId) null else state.selectedOperationId
                                )
                                bannerMessage = "Operación de diseño borrada."
                            },
                            onSelectPlane = { plane ->
                                state = state.copy(activePlane = plane)
                                bannerMessage = "Plano activo cambiado a ${plane.name}. Clic 'Ir a Croquis' para trazar boceto."
                            }
                        )
                    }

                    // Property Manager (SolidWorks panel for active extrusions/revolutions parameters)
                    val activeSketch = state.sketches.firstOrNull { it.id == state.selectedSketchId }
                    PropertyManager(
                        visible = activePropertyOpType != null,
                        opType = activePropertyOpType ?: OperationType.EXTRUDE_BOSS,
                        selectedSketchName = activeSketch?.name,
                        onAccept = { depth, angle, axis, radius, thickness, chosenOpType ->
                            // Add 3D operation to our history timeline!
                            val operationName = when (chosenOpType) {
                                OperationType.EXTRUDE_BOSS -> "Saliente-Extruir ${state.operations.size + 1}"
                                OperationType.EXTRUDE_CUT -> "Cortar-Extruir ${state.operations.size + 1}"
                                OperationType.REVOLVE_BOSS -> "Revolución ${state.operations.size + 1}"
                                OperationType.REVOLVE_CUT -> "Corte-Revolución ${state.operations.size + 1}"
                                OperationType.FILLET -> "Redondeo ${state.operations.size + 1}"
                                OperationType.SHELL -> "Vaciado Shell ${state.operations.size + 1}"
                            }

                            val newOp = CadOperation(
                                name = operationName,
                                type = chosenOpType,
                                sketchId = state.selectedSketchId,
                                depth = depth,
                                angle = angle,
                                axis = axis,
                                radius = radius,
                                thickness = thickness
                            )

                            // Add operation, switch immediately to 3D Viewmode to see the solid, and reset property panel
                            state = state.copy(
                                operations = state.operations + newOp,
                                viewMode3D = true,
                                selectedOperationId = newOp.id
                            )
                            activePropertyOpType = null
                            bannerMessage = "¡Operación '$operationName' creada con éxito! Observa la previsualización 3D."
                        },
                        onCancel = {
                            activePropertyOpType = null
                        }
                    )

                    // Sleek vertical tab handle to open/close FeatureManager sidebar
                    if (activePropertyOpType == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(16.dp)
                                .background(Color(0xFFE2E9F0))
                                .border(0.5.dp, Color(0xFFBDC3C7))
                                .clickable { isTreeExpanded = !isTreeExpanded },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isTreeExpanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                                contentDescription = "Expandir Sidebar",
                                tint = Color(0xFF1B365D),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Handle to show left panel when fully hidden
            if (!isLeftPanelVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 0.dp)
                        .width(18.dp)
                        .height(80.dp)
                        .background(Color(0xFFECEFF1).copy(alpha = 0.9f), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        .border(0.5.dp, Color(0xFFBDC3C7), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        .clickable { isLeftPanelVisible = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Mostrar panel",
                        tint = Color(0xFF1B365D),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Top overlay helper prompt / alert banner with premium fading timing
            AnimatedVisibility(
                visible = bannerMessage != null,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = if (isLeftPanelVisible) 80.dp else 24.dp, end = 16.dp)
                    .fillMaxWidth(0.85f)
            ) {
                val msg = bannerMessage ?: ""
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF0FEF9E7)),
                    border = borderStroke(0.5.dp, Color(0xFFF9E79F)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFD35400),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = msg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF7E5109),
                            lineHeight = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { bannerMessage = null },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            // Floating CAD Toolbars bottom docking station (Croquis vs Operaciones)
            AnimatedVisibility(
                visible = isBottomToolbarVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(0.95f)
            ) {
                FloatingActionToolbar(
                    viewMode3D = state.viewMode3D,
                    selectedSketchId = state.selectedSketchId,
                    activeSketchTool = activeSketchTool,
                    onSelectSketchTool = { tool ->
                        if (state.viewMode3D) {
                            state = state.copy(viewMode3D = false)
                        }
                        activeSketchTool = if (activeSketchTool == tool) null else tool
                        bannerMessage = if (activeSketchTool != null) {
                            "Herramienta '$tool' activa. Haz clic y arrastra en la cuadrícula para dibujarlo."
                        } else "Modo Croquis activo. Elige una herramienta."
                    },
                    onTriggerOperation = { opType ->
                        if (state.selectedSketchId == null && opType != OperationType.SHELL && opType != OperationType.FILLET) {
                            Toast.makeText(context, "Por favor, selecciona o crea un Croquis primero", Toast.LENGTH_LONG).show()
                            bannerMessage = "Crea un Croquis en el Árbol de operaciones antes de aplicar un modelado 3D."
                        } else {
                            activePropertyOpType = opType
                            bannerMessage = "Ajusta las dimensiones de tu operación en el panel izquierdo."
                        }
                    },
                    onHideToolbar = {
                        isBottomToolbarVisible = false
                    }
                )
            }

            // Handle to show tools panel when fully hidden
            if (!isBottomToolbarVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                        .background(Color(0xFFECEFF1).copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, Color(0xFFBDC3C7), RoundedCornerShape(6.dp))
                        .clickable { isBottomToolbarVisible = true }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Construction,
                            contentDescription = "Mostrar herramientas",
                            tint = Color(0xFF1B365D),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Mostrar herramientas",
                            color = Color(0xFF1B365D),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            tint = Color(0xFF1B365D),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Dialogue: Python Code Compiler Viewer
            MacroViewer(
                visible = showMacroDialog,
                macroCode = FreeCadMacroGenerator.generate(state.sketches, state.operations, state.projectName),
                projectName = state.projectName,
                onDismiss = { showMacroDialog = false }
            )
        }
    }
}

// Helper border stroke construction
fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) =
    androidx.compose.foundation.BorderStroke(width, color)

// Bottom Floating dock action menu
@Composable
fun FloatingActionToolbar(
    viewMode3D: Boolean,
    selectedSketchId: String?,
    activeSketchTool: String?,
    onSelectSketchTool: (String) -> Unit,
    onTriggerOperation: (OperationType) -> Unit,
    onHideToolbar: () -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0 = CROQUIS (Sketch), 1 = OPERACIONES (3D Features)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Color(0xFFBDC3C7), RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFDF8F9FA)),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(3.dp)) {
            // Dual workbench tabs with quick hide button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE5E8E8), RoundedCornerShape(6.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TabButton(
                        selected = activeTab == 0,
                        title = "CROQUIS",
                        icon = Icons.Default.GridGoldenratio,
                        onClick = { activeTab = 0 }
                    )
                    TabButton(
                        selected = activeTab == 1,
                        title = "OPERACIONES",
                        icon = Icons.Default.ViewInAr,
                        onClick = { activeTab = 1 }
                    )
                }

                IconButton(
                    onClick = onHideToolbar,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Ocultar barra de herramientas",
                        tint = Color(0xFFC0392B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Action items corresponding to selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeTab == 0) {
                    // Sketching Tools
                    ActionButton(
                        selected = activeSketchTool == "Line",
                        title = "Línea",
                        icon = Icons.Default.Gesture,
                        onClick = { onSelectSketchTool("Line") }
                    )
                    ActionButton(
                        selected = activeSketchTool == "Circle",
                        title = "Círculo",
                        icon = Icons.Default.RadioButtonUnchecked,
                        onClick = { onSelectSketchTool("Circle") }
                    )
                    ActionButton(
                        selected = activeSketchTool == "Rectangle",
                        title = "Rectángulo",
                        icon = Icons.Default.CropSquare,
                        onClick = { onSelectSketchTool("Rectangle") }
                    )
                    ActionButton(
                        selected = activeSketchTool == "Dimension",
                        title = "Cota",
                        icon = Icons.Default.LineWeight,
                        onClick = { onSelectSketchTool("Dimension") }
                    )
                } else {
                    // Feature Modelling Tools
                    ActionButton(
                        selected = false,
                        title = "Extruir",
                        icon = Icons.Default.UnfoldMore,
                        onClick = { onTriggerOperation(OperationType.EXTRUDE_BOSS) }
                    )
                    ActionButton(
                        selected = false,
                        title = "Revolución",
                        icon = Icons.Default.RotateRight,
                        onClick = { onTriggerOperation(OperationType.REVOLVE_BOSS) }
                    )
                    ActionButton(
                        selected = false,
                        title = "Redondeo",
                        icon = Icons.Default.RoundedCorner,
                        onClick = { onTriggerOperation(OperationType.FILLET) }
                    )
                    ActionButton(
                        selected = false,
                        title = "Vaciado",
                        icon = Icons.Default.Inbox,
                        onClick = { onTriggerOperation(OperationType.SHELL) }
                    )
                }
            }
        }
    }
}

@Composable
fun RowScope.TabButton(
    selected: Boolean,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .background(
                if (selected) Color(0xFF1B365D) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color(0xFF566573),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = title,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else Color(0xFF566573)
            )
        }
    }
}

@Composable
fun ActionButton(
    selected: Boolean,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (selected) Color(0xFF3498DB) else Color(0xFFEBF5FB),
                    CircleShape
                )
                .border(0.5.dp, if (selected) Color(0xFF2980B9) else Color(0xFFD4E6F1), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) Color.White else Color(0xFF2980B9),
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = title,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F4E79),
            textAlign = TextAlign.Center
        )
    }
}

// PRELOAD demo projects
fun loadDemoBracket(onLoad: (ProjectState) -> Unit) {
    val skId = UUID.randomUUID().toString()
    val testSketch = CadSketch(
        id = skId,
        name = "Croquis Base (Plano Alzado)",
        plane = WorkPlane.XY,
        entities = listOf(
            SketchEntity.Rectangle(cx = 0f, cy = 0f, width = 80f, height = 50f, label = "80 x 50 mm"),
            SketchEntity.Circle(cx = 0f, cy = 0f, radius = 12f, label = "Dia 24mm (Vaciado)")
        )
    )

    val opId1 = UUID.randomUUID().toString()
    val extBoss = CadOperation(
        id = opId1,
        name = "Saliente-Extruir 1",
        type = OperationType.EXTRUDE_BOSS,
        sketchId = skId,
        depth = 30f
    )

    // Consolidated Cut direct from our tool!
    val opId2 = UUID.randomUUID().toString()
    val extCut = CadOperation(
        id = opId2,
        name = "Cortar-Extruir 2 (Vaciado)",
        type = OperationType.EXTRUDE_CUT,
        sketchId = skId,
        depth = 30f
    )

    onLoad(
        ProjectState(
            projectName = "Soporte_Con_Vaciado",
            sketches = listOf(testSketch),
            operations = listOf(extBoss, extCut),
            selectedSketchId = skId,
            viewMode3D = true,
            yaw = -45f,
            pitch = 25f,
            scale = 2.6f
        )
    )
}

fun loadDemoPulley(onLoad: (ProjectState) -> Unit) {
    val skId = UUID.randomUUID().toString()
    val testSketch = CadSketch(
        id = skId,
        name = "Perfil Polea (Plano Perfil)",
        plane = WorkPlane.YZ,
        entities = listOf(
            // Centered shape to revolve
            SketchEntity.Rectangle(cx = 30f, cy = 0f, width = 20f, height = 40f, label = "Sección Polea"),
            SketchEntity.Circle(cx = 30f, cy = 0f, radius = 8f, label = "Círculo Toroidal")
        )
    )

    val opId = UUID.randomUUID().toString()
    val revBoss = CadOperation(
        id = opId,
        name = "Revolución 1",
        type = OperationType.REVOLVE_BOSS,
        sketchId = skId,
        angle = 360f,
        axis = "Y-Axis"
    )

    onLoad(
        ProjectState(
            projectName = "Polea_Revolucion",
            sketches = listOf(testSketch),
            operations = listOf(revBoss),
            selectedSketchId = skId,
            viewMode3D = true,
            yaw = -55f,
            pitch = 20f,
            scale = 2.2f
        )
    )
}
