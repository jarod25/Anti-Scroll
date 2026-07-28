package fr.jarodkohler.antiscroll.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ApplicationIcon(
    label: String,
    icon: ImageBitmap?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    if (icon == null) {
        Surface(
            modifier = modifier.size(size),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        Image(
            bitmap = icon,
            contentDescription = label,
            modifier = modifier.size(size)
        )
    }
}
