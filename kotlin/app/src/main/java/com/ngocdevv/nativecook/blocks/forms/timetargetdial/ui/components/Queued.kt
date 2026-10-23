package com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.AppColors
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.FadeInUp
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.LabelFont

/** `src/components/queued/index.tsx` */
@Composable
fun Queued(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        FadeInUp(durationMillis = 300) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(AppColors.Teal, RoundedCornerShape(24.dp))
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // RN lets the two lines overflow the 36dp content box; mirror that instead of clipping.
                Column(modifier = Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true)) {
                    Text(
                        text = "Landing page 1",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = LabelFont,
                    )
                    Text(
                        text = "For Chris",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(Color.White, CircleShape)
                        .padding(vertical = 4.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(AppColors.Yellow, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp)) // columnGap 4 + marginLeft 4
                    Text(
                        text = "Queued",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        FadeInUp(durationMillis = 500) {
            Text(
                text = "Adjust project time target",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LabelFont,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun QueuedPreview() {
    Queued()
}
