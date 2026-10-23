package com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.AppColors
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.touchableOpacity

private data class TabItem(
    val name: String,
    val label: String,
    val icon: ImageVector?,
)

private val tabs = listOf(
    TabItem(name = "home", label = "Home", icon = Icons.Outlined.Home),
    TabItem(name = "payments", label = "Payments", icon = Icons.Outlined.CreditCard),
    TabItem(name = "project", label = "+ Project", icon = null),
    TabItem(name = "messages", label = "Messages", icon = Icons.Outlined.ChatBubbleOutline),
    TabItem(name = "team", label = "Team", icon = Icons.Outlined.People),
)

/** `src/components/bottom-tab/index.tsx` */
@Composable
fun BottomTab(modifier: Modifier = Modifier) {
    // The RN version compares `usePathname()` against the tab route ("/" is Home).
    var activeTab by remember { mutableStateOf("home") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.Background),
    ) {
        HorizontalDivider(thickness = 1.dp, color = AppColors.Divider)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val isActive = activeTab == tab.name
                Box(
                    modifier = Modifier
                        .then(if (index == 2) Modifier.padding(horizontal = 8.dp) else Modifier)
                        .touchableOpacity(activeOpacity = 0.7f) { activeTab = tab.name },
                    contentAlignment = Alignment.Center,
                ) {
                    if (tab.name == "project") {
                        Box(
                            modifier = Modifier
                                .background(AppColors.Grey, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tab.label,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        val tint = if (isActive) Color.White else AppColors.Inactive
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = tab.icon!!,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = tab.label,
                                color = tint,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        // paddingBottom: bottom safe-area inset
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun BottomTabPreview() {
    BottomTab()
}
