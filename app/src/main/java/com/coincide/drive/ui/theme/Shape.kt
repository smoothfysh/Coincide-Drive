package com.coincide.drive.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Shapes adhering strictly to the Stitch Tonal Material Workspace specification:
 * - 8dp spatial rhythm
 * - Pill shapes (rounded-full / 100.dp) for prominent interactive buttons and chips
 * - 16-24dp rounded corners for tonal surface containers
 */
val CoincideShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Reusable Pill Shape for CTA buttons and status chips
val PillShape = CircleShape
val TonalCardShape = RoundedCornerShape(20.dp)
val BottomSheetPillShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
