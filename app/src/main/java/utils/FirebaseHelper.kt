package utils

object FirebaseHelper {
    val auth get() = com.google.firebase.auth.FirebaseAuth.getInstance()
    val db get() = com.google.firebase.database.FirebaseDatabase.getInstance().reference

    fun enablePersistence() {
        com.google.firebase.database.FirebaseDatabase
            .getInstance().setPersistenceEnabled(true)
    }

    fun currentTimestamp(): String {
        val fmt = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault())
        return fmt.format(java.util.Date())
    }

    fun isoTimestamp(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        return fmt.format(java.util.Date())
    }

    fun yesterdayDateString(): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DATE, -1)
        val fmt = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
        return fmt.format(cal.time)
    }

    fun todayDateString(): String {
        val fmt = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault())
        return fmt.format(java.util.Date())
    }
}
