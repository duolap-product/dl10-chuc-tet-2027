package online.duolap.tetviet.game.oanquan.engine

enum class PlayerSide {
    PLAYER_1, // Người chơi 1 (Phía dưới: các ô 0, 1, 2, 3, 4)
    PLAYER_2; // Người chơi 2 (Phía trên: các ô 6, 7, 8, 9, 10)

    val opponent: PlayerSide
        get() = if (this == PLAYER_1) PLAYER_2 else PLAYER_1

    val ownedPits: IntRange
        get() = if (this == PLAYER_1) 0..4 else 6..10

    fun ownsPit(index: Int): Boolean {
        return index in ownedPits
    }
}

