package com.snaploop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaploop.app.ui.SnapColors
import com.snaploop.app.ui.SnapLoopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SnapLoopTheme { SnapLoopRoot(firebaseReady = BuildConfig.FIREBASE_CONFIG_PRESENT) } }
    }
}

@Composable private fun SnapLoopRoot(firebaseReady: Boolean) {
    if (!firebaseReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(Modifier.padding(28.dp), shape=RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("SnapLoop", fontWeight=FontWeight.Black, fontSize=34.sp)
                    Text("Firebase configuration is required for this development build.")
                    Text("Add app/google-services.json from the existing SnapLoop Firebase project, then rebuild.")
                }
            }
        }
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(bottomBar={ NavigationBar {
        listOf("Home","Gallery","You").forEachIndexed { i,label -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={},label={Text(label,fontWeight=FontWeight.Bold)}) }
    }}) { inset ->
        when(tab) { 0 -> HomeShell(Modifier.padding(inset)); 1 -> GalleryShell(Modifier.padding(inset)); else -> YouShell(Modifier.padding(inset)) }
    }
}

@Composable private fun GradientAction(title:String, subtitle:String) {
    Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.linearGradient(listOf(SnapColors.Coral,SnapColors.HotPink,SnapColors.Lilac,SnapColors.Blue)),RoundedCornerShape(28.dp)).padding(22.dp)) {
        Column(Modifier.align(Alignment.BottomStart)) { Text(title,color=androidx.compose.ui.graphics.Color.White,fontWeight=FontWeight.Black,fontSize=22.sp); Text(subtitle,color=androidx.compose.ui.graphics.Color.White,fontSize=16.sp) }
    }
}
@Composable private fun HomeShell(modifier:Modifier=Modifier) { Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement=Arrangement.spacedBy(18.dp)) { Text("SnapLoop",fontSize=42.sp,fontWeight=FontWeight.Black); Text("Photos your friends took of you on their phones, brought to your phone automatically.",fontSize=18.sp); GradientAction("Create Event","Trip, party, family & more"); GradientAction("Join Event","Code, link or QR"); Text("Your Events",fontSize=28.sp,fontWeight=FontWeight.Black) } }
@Composable private fun GalleryShell(modifier:Modifier=Modifier) { Column(modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Gallery",fontSize=36.sp,fontWeight=FontWeight.Black);Text("Your matched Event photos appear here after scanning.") } }
@Composable private fun YouShell(modifier:Modifier=Modifier) { Column(modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text("You",fontSize=40.sp,fontWeight=FontWeight.Black); listOf("Edit Your Name","Update Face Setup","Photo Access","Privacy & Data","Replay Onboarding").forEach { Card(shape=RoundedCornerShape(24.dp)){Text(it,Modifier.fillMaxWidth().padding(22.dp),fontSize=20.sp,fontWeight=FontWeight.Bold)} } } }
