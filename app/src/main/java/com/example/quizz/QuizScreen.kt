package com.example.quizz

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.quizz.model.QuizState
import com.example.quizz.ui.theme.AnswerHighlight
import com.example.quizz.ui.theme.QuestionHighlight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(viewModel: QuizViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                viewModel.loadFromPdf(context, uri)
            }
        }
    )

    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.items.isEmpty() -> {
                EmptyQuizState(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    onPickPdf = {
                        pdfPicker.launch(arrayOf("application/pdf"))
                    }
                )
            }
            state.isFinished -> {
                QuizFinished(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    state = state,
                    onRestart = viewModel::restart,
                    onShare = {
                        shareResults(
                            context = context,
                            score = state.score,
                            total = state.totalQuestions
                        )
                    }
                )
            }
            else -> {
                ActiveQuiz(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    state = state,
                    onAnswerSelected = viewModel::selectAnswer,
                    onNext = {
                        val selectedIndex = state.selectedAnswerIndex
                        if (selectedIndex != null) {
                            val isCorrect = state.currentItem?.correctAnswerIndex == selectedIndex
                            viewModel.submitAnswer(isCorrect)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyQuizState(modifier: Modifier = Modifier, onPickPdf: () -> Unit) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.pick_pdf),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onPickPdf) {
            Text(text = stringResource(id = R.string.pick_pdf))
        }
    }
}

@Composable
private fun ActiveQuiz(
    modifier: Modifier = Modifier,
    state: QuizState,
    onAnswerSelected: (Int) -> Unit,
    onNext: () -> Unit
) {
    val currentItem = state.currentItem ?: return
    val shuffledAnswers = remember(state.currentIndex, state.items) {
        currentItem.answers.mapIndexed { index, text -> AnswerOption(index, text) }.shuffled()
    }
    var selectedDisplayIndex by remember(state.currentIndex) { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.selectedAnswerIndex, shuffledAnswers) {
        val selected = state.selectedAnswerIndex
        selectedDisplayIndex = selected?.let { index ->
            shuffledAnswers.indexOfFirst { it.originalIndex == index }.takeIf { it >= 0 }
        }
    }

    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "${state.currentIndex + 1}/${state.totalQuestions}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = QuestionHighlight.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = currentItem.question,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(shuffledAnswers) { index, option ->
                val isSelected = selectedDisplayIndex == index
                AnswerOptionCard(
                    option = option,
                    isSelected = isSelected,
                    onClick = {
                        selectedDisplayIndex = index
                        onAnswerSelected(option.originalIndex)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        val enabled = state.selectedAnswerIndex != null
        Button(
            onClick = onNext,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(id = R.string.next_question))
        }
        state.lastAnswerCorrect?.let { wasCorrect ->
            Spacer(modifier = Modifier.height(12.dp))
            val messageRes = if (wasCorrect) R.string.correct_answer else R.string.incorrect_answer
            Text(
                text = stringResource(id = messageRes),
                color = if (wasCorrect) MaterialTheme.colorScheme.primary else Color.Red,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AnswerOptionCard(
    option: AnswerOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) AnswerHighlight.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp)
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun QuizFinished(
    modifier: Modifier = Modifier,
    state: QuizState,
    onRestart: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.score, state.score, state.totalQuestions),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(id = R.string.retry))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(id = R.string.share_results))
        }
    }
}

data class AnswerOption(
    val originalIndex: Int,
    val label: String
)

private fun shareResults(context: Context, score: Int, total: Int) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.score, score, total))
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.share_results)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
