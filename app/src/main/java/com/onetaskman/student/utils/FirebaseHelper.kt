package com.onetaskman.student.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object FirebaseHelper {
    val db get() = FirebaseDatabase.getInstance().reference
    val auth get() = FirebaseAuth.getInstance()

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE // YYYY-MM-DD

    fun todayDateString(): String {
        return LocalDate.now().format(formatter)
    }

    fun yesterdayDateString(): String {
        return LocalDate.now().minusDays(1).format(formatter)
    }

    fun currentTimestamp(): Long {
        return System.currentTimeMillis()
    }
}