package fr.jarodkohler.antiscroll.engine.observation

data class ObservationBaselinePolicy(val requiredReliableDays: Int) {
    init {
        require(requiredReliableDays > 0) {
            "Observation baseline must require at least one reliable day"
        }
    }
}
