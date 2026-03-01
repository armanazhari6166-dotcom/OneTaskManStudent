package data

data class StudentProfile(
    val displayName: String = "",
    val email: String = "",
    val pin: String = "",
    val emailVerified: Boolean = false,
    val enrolledClasses: List<String> = emptyList(),
    val stats: StudentStats = StudentStats()
)
