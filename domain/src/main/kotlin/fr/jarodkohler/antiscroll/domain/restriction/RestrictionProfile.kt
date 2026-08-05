package fr.jarodkohler.antiscroll.domain.restriction

import java.time.Duration

@JvmInline
value class RestrictionProfileIdentifier(val value: String) {
    init {
        require(value.isNotBlank()) { "Restriction profile identifier must not be blank" }
        require(value.none { character -> character.isWhitespace() }) {
            "Restriction profile identifier must not contain whitespace"
        }
    }

    override fun toString(): String = value
}

data class SharedSessionPolicy(
    val maximumDuration: Duration,
    val inactivityTimeout: Duration
) {
    init {
        require(maximumDuration > Duration.ZERO) {
            "Shared session maximum duration must be positive"
        }
        require(!inactivityTimeout.isNegative) {
            "Shared session inactivity timeout must not be negative"
        }
    }
}

data class RestrictionProfile(
    val identifier: RestrictionProfileIdentifier,
    val version: Int,
    val sharedSessionPolicy: SharedSessionPolicy?
) {
    init {
        require(version > 0) { "Restriction profile version must be positive" }
    }
}
