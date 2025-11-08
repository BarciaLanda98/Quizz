package com.example.quizz

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quizz.model.QuizState
import com.example.quizz.pdf.PdfQuizParser
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuizViewModel : ViewModel() {

    private val parser = PdfQuizParser()
    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun loadFromPdf(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        parser.parse(context.applicationContext, input)
                    } ?: throw IOException("No se pudo abrir el PDF seleccionado")
                }
            }.onSuccess { items ->
                if (items.isNullOrEmpty()) {
                    _errorMessage.value = "No se encontraron preguntas en el PDF"
                    _state.value = QuizState()
                } else {
                    _errorMessage.value = null
                    _state.value = QuizState(items = items)
                }
            }.onFailure { throwable ->
                _errorMessage.value = throwable.message ?: "Error al procesar el PDF"
                _state.value = QuizState()
            }
        }
    }

    fun selectAnswer(answerIndex: Int) {
        val current = _state.value
        if (current.isFinished || current.currentItem == null) return
        _state.value = current.copy(selectedAnswerIndex = answerIndex, lastAnswerCorrect = null)
    }

    fun submitAnswer(isCorrect: Boolean) {
        val current = _state.value
        if (current.isFinished || current.currentItem == null) return
        val updatedScore = if (isCorrect) current.score + 1 else current.score
        val nextIndex = current.currentIndex + 1
        if (nextIndex >= current.items.size) {
            _state.value = current.copy(
                score = updatedScore,
                selectedAnswerIndex = null,
                currentIndex = nextIndex,
                isFinished = true,
                lastAnswerCorrect = isCorrect
            )
        } else {
            _state.value = current.copy(
                score = updatedScore,
                selectedAnswerIndex = null,
                currentIndex = nextIndex,
                lastAnswerCorrect = isCorrect
            )
        }
    }

    fun restart() {
        val current = _state.value
        if (current.items.isEmpty()) return
        _state.value = QuizState(items = current.items)
        _errorMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
