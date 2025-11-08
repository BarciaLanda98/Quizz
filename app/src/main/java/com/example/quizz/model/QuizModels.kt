package com.example.quizz.model

data class QuizItem(
    val question: String,
    val answers: List<String>,
    val correctAnswerIndex: Int
)

data class QuizState(
    val items: List<QuizItem> = emptyList(),
    val currentIndex: Int = 0,
    val selectedAnswerIndex: Int? = null,
    val score: Int = 0,
    val isFinished: Boolean = false,
    val lastAnswerCorrect: Boolean? = null
) {
    val currentItem: QuizItem?
        get() = items.getOrNull(currentIndex)

    val totalQuestions: Int
        get() = items.size
}
