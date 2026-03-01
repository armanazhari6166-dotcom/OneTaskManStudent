package data

data class Task(
    val TaskName: String = "",
    val TeacherName: String = "",
    val DueDate: String = "",
    val AssignedAt: String = "",
    val LastUpdate: String = "",
    val Status: String = "Pending",
    val CurrentStep: Int = 0,
    val Steps: List<String> = emptyList(),
    val SkippedSteps: List<Int> = emptyList(),
    val FinishedAt: Long = 0
)
