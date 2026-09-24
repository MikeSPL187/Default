package com.metrolist.music.ui.screens.wrapped.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.screens.wrapped.components.AnimatedBackground
import com.metrolist.music.ui.screens.wrapped.components.ShapeType

@Composable
fun ConclusionPage(onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(elementCount = 30, shapeTypes = listOf(ShapeType.Circle, ShapeType.Line))
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.wrapped_logo_content_description),
                modifier = Modifier.size(96.dp),
                tint = colors.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.wrapped_thank_you),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wrapped_special_thanks),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(40.dp))
            Button(onClick = onClose) {
                Text(
                    text = stringResource(R.string.wrapped_close),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}
