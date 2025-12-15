package amalia.skripsi.deteksipadi.ui.screens.petani.detection

import amalia.skripsi.deteksipadi.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.util.concurrent.ExecutorService


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarWithBackIcon(
    title: String,
    onBackClick: () -> Unit = {},
    showBackIcon: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF719D3D)
            )
    ) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                if (showBackIcon) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            },
            // Membuat background TopAppBar transparan agar gradient di Box terlihat
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier.padding(vertical = 0.dp)
        )
    }
}

@Composable
fun ScannerTopBar(
    navController: NavController,
    cameraExecutor: ExecutorService
) {
    TopAppBarWithBackIcon(
        title = stringResource(R.string.detection_title),
        onBackClick = {
            cameraExecutor.shutdown()
            navController.navigate("home") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
    )
}


@Composable
fun ScannerBottomBar(
    onGalleryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding() // ini yang bikin UI naik dari nav bar
    ){
        BottomAppBar(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp)),
            tonalElevation = 4.dp,
            containerColor = Color(0xFFD4E6D7)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { /* Sudah di mode kamera */ },
                    modifier = Modifier
                        .padding(vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF719D3D)),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_camera_alt_24),
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.title_camera),
                        color = Color.White
                    )
                }

                Button(
                    onClick = onGalleryClick,
                    modifier = Modifier
                        .padding(vertical = 10.dp),
                    border = BorderStroke(2.dp, Color(0xFF719D3D)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF719D3D)
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_photo_library_24),
                        contentDescription = null,
                        tint = Color(0xFF719D3D)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.title_gallery),
                        color = Color(0xFF719D3D)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun IconTextColumn(iconId: Int, text: String, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() })
    {
        Icon(painter = painterResource(id = iconId), contentDescription = text, tint = tint, modifier = Modifier.size(20.dp))
        Text(text = text, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}