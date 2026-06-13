package dev.vodnixir.delirium.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val DeliriumShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Fully rounded ends — used for buttons, chips and pills. */
val PillShape = RoundedCornerShape(percent = 50)

/** The signature "locket" frame used for photos. */
val LocketShape = RoundedCornerShape(40.dp)
