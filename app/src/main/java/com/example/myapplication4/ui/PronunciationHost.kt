package com.example.myapplication4.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication4.R
import com.example.myapplication4.RecallViewModel
import com.example.myapplication4.ui.design.*

@Composable
internal fun PronunciationHost(vm: RecallViewModel, content: @Composable () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.pronunciation.message.collectAsStateWithLifecycle()
    val speechStatus by vm.pronunciation.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val host = remember { SnackbarHostState() }
    val manage = stringResource(R.string.manage_voices)
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.pronunciation.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vm.pronunciation.stop() }
    }
    LaunchedEffect(message, manage) {
        host.currentSnackbarData?.dismiss()
        message?.let {
            val result = host.showSnackbar(it, if (speechStatus == com.example.myapplication4.pronunciation.SpeechStatus.Preparing) null else manage, withDismissAction = true)
            if (vm.pronunciation.message.value == it) vm.pronunciation.dismissMessage()
            if (result == SnackbarResult.ActionPerformed) vm.pronunciation.manageVoices(context)
        }
    }
    CompositionLocalProvider(LocalPronunciation provides { text, language -> vm.pronunciation.speak(text, language, settings.speechRate) }) {
        Box(Modifier.fillMaxSize()) {
            content()
            SnackbarHost(host, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = RecallSizes.touch, start = RecallSpacing.md, end = RecallSpacing.md))
        }
    }
}
