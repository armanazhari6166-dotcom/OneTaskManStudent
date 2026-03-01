package com.onetaskman.student.data

import com.google.firebase.database.IgnoreExtraProperties

// This annotation tells Firebase to ignore any fields in the database that
// are not defined in this class. This prevents crashes if the data structure changes.
@IgnoreExtraProperties
data class StudentProfile(
    val displayName: String = "",
    val pin: String = "",
    val email: String = "",
    // enrolledClasses will be parsed manually to ensure stability
)