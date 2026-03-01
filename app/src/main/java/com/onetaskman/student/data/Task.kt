package com.onetaskman.student.data

import com.google.gson.Gson

data class Task(
    val TaskName: String = "",
    val TeacherName: String = "",
    val DueDate: String = "",
    val Status: String = "Pending",
    val CurrentStep: Int = 0,
    val Steps: List<String> = emptyList(),
    val FinishedAt: Long = 0,
    val SkippedSteps: List<Int> = emptyList()
) {
    fun toJson(): String = Gson().toJson(this)

    fun toMap(): Map<String, Any> {
        return mapOf(
            "TaskName" to TaskName,
            "TeacherName" to TeacherName,
            "DueDate" to DueDate,
            "Status" to Status,
            "CurrentStep" to CurrentStep,
            "Steps" to Steps,
            "FinishedAt" to FinishedAt,
            "SkippedSteps" to SkippedSteps
        )
    }

    companion object {
        fun fromJson(json: String): Task = Gson().fromJson(json, Task::class.java)
    }
}