package com.metrolist.music.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.metrolist.music.R

/** Google Sans, the one typeface of the «Такт» design (OFL, see assets/licenses). */
val GoogleSans =
    FontFamily(
        Font(R.font.google_sans_regular, FontWeight.Normal),
        Font(R.font.google_sans_medium, FontWeight.Medium),
        Font(R.font.google_sans_semibold, FontWeight.SemiBold),
        Font(R.font.google_sans_bold, FontWeight.Bold),
    )

private fun TextStyle.sans(
    weight: FontWeight? = null,
    tracking: Float? = null,
) = copy(
    fontFamily = GoogleSans,
    fontWeight = weight ?: fontWeight,
    letterSpacing = tracking?.em ?: letterSpacing,
)

/** Material roles in Google Sans: headings a step bolder and tighter, body as Material has it. */
val TaktTypography =
    Typography().run {
        copy(
            displayLarge = displayLarge.sans(FontWeight.Bold, -0.02f),
            displayMedium = displayMedium.sans(FontWeight.Bold, -0.02f),
            displaySmall = displaySmall.sans(FontWeight.Bold, -0.02f),
            headlineLarge = headlineLarge.sans(FontWeight.SemiBold, -0.015f),
            headlineMedium = headlineMedium.sans(FontWeight.SemiBold, -0.015f),
            headlineSmall = headlineSmall.sans(FontWeight.Bold, -0.01f),
            titleLarge = titleLarge.sans(FontWeight.SemiBold),
            titleMedium = titleMedium.sans(FontWeight.Medium),
            titleSmall = titleSmall.sans(FontWeight.Medium),
            bodyLarge = bodyLarge.sans(),
            bodyMedium = bodyMedium.sans(),
            bodySmall = bodySmall.sans(),
            labelLarge = labelLarge.sans(FontWeight.Medium),
            labelMedium = labelMedium.sans(FontWeight.Medium),
            labelSmall = labelSmall.sans(FontWeight.Bold, 0.06f),
        )
    }

/** Covers in rows 12, cards 18, tiles 24, heroes and sheets 28; actions are pills. */
val TaktShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
