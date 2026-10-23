package com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.AppColors
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.FadeInUp
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.touchableOpacity

/** `src/components/footer/index.tsx` */
@Composable
fun Footer(modifier: Modifier = Modifier) {
    FadeInUp(durationMillis = 800, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val shape = RoundedCornerShape(16.dp)

            // Confirm
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = shape,
                        ambientColor = Color.White,
                        spotColor = Color.White,
                    )
                    .background(Color.White, shape)
                    .touchableOpacity { Log.d("Footer", "Confirm pressed") }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Confirm",
                    color = AppColors.ConfirmText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Back
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, AppColors.BackBorder, shape)
                    .touchableOpacity { Log.d("Footer", "Back pressed") }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "← Back",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FooterPreview() {
    Footer()
}
