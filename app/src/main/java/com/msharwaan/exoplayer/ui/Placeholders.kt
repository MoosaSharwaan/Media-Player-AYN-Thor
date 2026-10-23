package com.msharwaan.exoplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msharwaan.exoplayer.nav.Route
import com.msharwaan.exoplayer.ui.theme.Palette

/** Stand-ins for screens built in later stages. */
@Composable
fun PlaceholderTop(route: Route) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.CenterStart) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Exo Player", color = Palette.Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(route.toString(), color = Palette.Muted, fontSize = 16.sp)
        }
    }
}

@Composable
fun PlaceholderBottom(route: Route) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.BottomStart) {
        Legend("A" to "Open", "B" to "Back")
    }
}
