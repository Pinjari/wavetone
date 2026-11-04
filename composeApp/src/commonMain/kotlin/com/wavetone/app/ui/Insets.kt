package com.wavetone.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Applies platform system-bar insets (status + navigation) as padding. */
@Composable
expect fun Modifier.systemBarsPadding(): Modifier
