package com.finanzaspersonales
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.finanzaspersonales.presentation.FinanceViewModel
import com.finanzaspersonales.presentation.FinanceScreen
import dagger.hilt.android.AndroidEntryPoint
@AndroidEntryPoint class MainActivity: ComponentActivity() {
 private val vm: FinanceViewModel by viewModels()
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { FinanceScreen(vm) } }
}
