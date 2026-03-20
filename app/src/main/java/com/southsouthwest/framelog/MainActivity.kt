package com.southsouthwest.framelog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.southsouthwest.framelog.ui.navigation.FrameLogNavGraph
import com.southsouthwest.framelog.ui.theme.FrameLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrameLogTheme {
                val navController = rememberNavController()
                FrameLogNavGraph(navController)
            }
        }
    }
}
