package dev.stade.ui.screens

import kotlin.math.ln

internal object StadeyMatcher {
    private const val MIN_SCORE = 0.35

    private val STOPWORDS = setOf(
        "how", "do", "does", "did", "is", "are", "was", "were", "can", "could", "will", "would",
        "should", "what", "why", "when", "where", "who", "which", "the", "an", "of", "to", "in",
        "on", "for", "and", "or", "but", "my", "your", "our", "their", "his", "her", "its", "it",
        "this", "that", "these", "those", "you", "we", "they", "he", "she", "me", "us", "them",
        "if", "about", "with", "without", "from", "there", "here", "be", "being", "been", "not",
        "no", "don", "doesn", "didn", "isn", "aren", "wasn", "weren", "won", "cant", "couldn",
        "wouldn", "shouldn", "hasn", "haven", "hadn", "im", "ive", "youre", "theyre", "whats",
        "wheres", "hows", "up", "out",
        "nasıl", "ne", "neden", "niçin", "mi", "mı", "mu", "mü", "bir", "ve", "veya", "ile",
        "benim", "senin", "bizim", "onların", "bu", "şu", "ben", "sen", "biz", "siz", "onlar",
        "için", "gibi", "de", "da", "ki", "ama", "fakat", "var", "yok", "olan", "ise"
    )

    private fun tokenize(text: String): List<String> {
        val sb = StringBuilder(text.length)
        for (c in text.lowercase()) {
            sb.append(if (c.isLetterOrDigit()) c else ' ')
        }
        return sb.toString().split(' ').filter { it.length > 1 && it !in STOPWORDS }
    }

    fun bestMatch(query: String, topics: List<FaqTopic>): FaqTopic? {
        if (topics.isEmpty()) return null
        val queryTerms = tokenize(query).toSet()
        if (queryTerms.isEmpty()) return null

        val topicTerms = topics.map { topic ->
            (tokenize(topic.question) + tokenize(topic.answer) + tokenize(topic.keywords)).toSet()
        }

        val documentFrequency = mutableMapOf<String, Int>()
        topicTerms.forEach { terms -> terms.forEach { term -> documentFrequency[term] = (documentFrequency[term] ?: 0) + 1 } }
        val topicCount = topicTerms.size
        fun idf(term: String): Double {
            val df = documentFrequency[term] ?: 0
            return ln((topicCount + 1.0) / (df + 1.0)) + 1.0
        }

        val totalWeight = queryTerms.sumOf { idf(it) }
        if (totalWeight <= 0.0) return null

        var bestIndex = -1
        var bestScore = 0.0
        topicTerms.forEachIndexed { index, terms ->
            val coveredWeight = queryTerms.filter { it in terms }.sumOf { idf(it) }
            val score = coveredWeight / totalWeight
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }

        return if (bestIndex >= 0 && bestScore >= MIN_SCORE) topics[bestIndex] else null
    }
}
