package fr.jarodkohler.antiscroll.domain.application

/** Stable application identity used by the domain and persistence layers. */
@JvmInline
value class ApplicationPackageName(val value: String) {
    init {
        require(value.isNotBlank()) { "Application package name must not be blank" }
        require(value.none { character -> character.isWhitespace() }) {
            "Application package name must not contain whitespace"
        }
    }

    override fun toString(): String = value
}
