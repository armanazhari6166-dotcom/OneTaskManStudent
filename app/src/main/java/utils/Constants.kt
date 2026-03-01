package utils


object HeroRank {
    val RANKS = listOf(
        Triple(5000, "Legend", "#FFD700"),
        Triple(2500, "Champion", "#FF4500"),
        Triple(1000, "Guardian", "#9370DB"),
        Triple(400,  "Warrior",  "#4682B4"),
        Triple(100,  "Disciple", "#3CB371"),
        Triple(0,    "Civilian", "#B0B0B0")
    )

    fun calculate(xp: Int): String {
        return RANKS.firstOrNull { xp >= it.first }?.second ?: "Civilian"
    }
}
