package com.ngocdevv.nativecook.blocks.charts.neonbarboot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The block fills the screen and keeps the status bar, so it is shown without a stage. */
@Composable
fun NeonBarBootDemo() { NeonBarBoot(autoPlay = true) }

@Preview
@Composable
private fun Preview() { NeonBarBootDemo() }
