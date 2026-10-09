package com.example.presentation.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Authentic graphic header for the Home Screen.
 * Uses the exact user uploaded image with ONLY the side drawer button overlay.
 */
@Composable
fun HomeHeader(
    onDrawerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(Color(0xFF07090E))
        ) {
            Image(
                painter = painterResource(id = R.drawable.header_home),
                contentDescription = "WiFi Card Master Pro",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // ONLY Drawer Menu Button placed on top-left (vertically centered in 72dp)
            IconButton(
                onClick = onDrawerClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_menu),
                    contentDescription = stringResource(R.string.menu_drawer),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Graphic header for sub-pages.
 * Uses the user uploaded header image without title, overlaying the dynamic destination page title & subtitle.
 */
@Composable
fun PagesHeader(
    title: String,
    subtitle: String,
    isRoot: Boolean = false,
    onNavClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(Color(0xFF07090E))
        ) {
            Image(
                painter = painterResource(id = R.drawable.header_pages),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Navigation button (Drawer or Back) on top-left (vertically centered in 72dp)
            IconButton(
                onClick = onNavClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            ) {
                Icon(
                    painter = painterResource(id = if (isRoot) R.drawable.ic_menu else R.drawable.ic_back),
                    contentDescription = if (isRoot) stringResource(R.string.menu_drawer) else stringResource(R.string.btn_back),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Clean typography title overlay on top-right
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF140306).copy(alpha = 0.9f))
                        .border(1.dp, NeonRed.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}
