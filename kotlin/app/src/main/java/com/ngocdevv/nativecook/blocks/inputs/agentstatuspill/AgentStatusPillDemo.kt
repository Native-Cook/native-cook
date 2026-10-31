package com.ngocdevv.nativecook.blocks.inputs.agentstatuspill

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun AgentStatusPillDemo() {
    AgentStatusPill(autoPlay = true)
}

@Preview
@Composable
private fun AgentStatusPillDemoPreview() {
    AgentStatusPillDemo()
}
