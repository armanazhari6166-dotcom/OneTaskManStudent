package com.onetaskman.student.data

object HeroRank {
    fun calculate(xp: Int): String {
        return when {
            xp >= 5000 -> "Legend"
            xp >= 2500 -> "Champion"
            xp >= 1000 -> "Guardian"
            xp >= 400 -> "Warrior"
            xp >= 100 -> "Disciple"
            else -> "Civilian"
        }
    }
}