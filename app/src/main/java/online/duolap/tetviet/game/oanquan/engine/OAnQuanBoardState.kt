package online.duolap.tetviet.game.oanquan.engine

/**
 * Trạng thái bàn cờ Ô ăn quan tại một thời điểm.
 * Bàn cờ gồm 12 ô:
 *  - 0, 1, 2, 3, 4: 5 Ô Dân của Người chơi 1 (Phía dưới)
 *  - 5: Ô Quan Hữu (Bên phải)
 *  - 6, 7, 8, 9, 10: 5 Ô Dân của Người chơi 2 (Phía trên)
 *  - 11: Ô Quan Tả (Bên trái)
 */
data class OAnQuanBoardState(
    val pits: IntArray = IntArray(12) { i ->
        if (i == 5 || i == 11) 0 else 5 // 10 ô dân mỗi ô 5 viên sỏi ban đầu
    },
    val hasQuanPiece: BooleanArray = booleanArrayOf(true, true), // Quan Hữu (5), Quan Tả (11)
    var player1Score: Int = 0,
    var player2Score: Int = 0,
    var player1Debt: Int = 0,
    var player2Debt: Int = 0,
    var currentTurn: PlayerSide = PlayerSide.PLAYER_1,
    var isGameOver: Boolean = false,
    var winner: PlayerSide? = null
) {
    companion object {
        const val QUAN_HUU_INDEX = 5
        const val QUAN_TA_INDEX = 11
        const val QUAN_VALUE = 10 // Giá trị điểm của 1 quân Quan lớn

        fun isQuanPit(index: Int): Boolean = index == QUAN_HUU_INDEX || index == QUAN_TA_INDEX
    }

    fun hasQuanPieceAt(index: Int): Boolean {
        return when (index) {
            QUAN_HUU_INDEX -> hasQuanPiece[0]
            QUAN_TA_INDEX -> hasQuanPiece[1]
            else -> false
        }
    }

    fun removeQuanPieceAt(index: Int) {
        when (index) {
            QUAN_HUU_INDEX -> hasQuanPiece[0] = false
            QUAN_TA_INDEX -> hasQuanPiece[1] = false
        }
    }

    fun isPitEmpty(index: Int): Boolean {
        return if (isQuanPit(index)) {
            !hasQuanPieceAt(index) && pits[index] == 0
        } else {
            pits[index] == 0
        }
    }

    /**
     * Tổng điểm có thể ăn được tại 1 ô (nếu là ô Quan thì cộng cả giá trị quan lớn).
     */
    fun getPointsInPit(index: Int): Int {
        val danPoints = pits[index]
        val quanPoints = if (hasQuanPieceAt(index)) QUAN_VALUE else 0
        return danPoints + quanPoints
    }

    /**
     * Kiểm tra xem cả 2 ô Quan đã bị ăn hết sạch hay chưa ("Hết quan").
     */
    fun areBothQuansCaptured(): Boolean {
        val quanHuuEmpty = !hasQuanPiece[0] && pits[QUAN_HUU_INDEX] == 0
        val quanTaEmpty = !hasQuanPiece[1] && pits[QUAN_TA_INDEX] == 0
        return quanHuuEmpty && quanTaEmpty
    }

    /**
     * Tạo bản sao độc lập (dùng cho thuật toán tìm kiếm AI / Minimax).
     */
    fun deepCopy(): OAnQuanBoardState {
        return OAnQuanBoardState(
            pits = pits.clone(),
            hasQuanPiece = hasQuanPiece.clone(),
            player1Score = player1Score,
            player2Score = player2Score,
            player1Debt = player1Debt,
            player2Debt = player2Debt,
            currentTurn = currentTurn,
            isGameOver = isGameOver,
            winner = winner
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OAnQuanBoardState
        if (!pits.contentEquals(other.pits)) return false
        if (!hasQuanPiece.contentEquals(other.hasQuanPiece)) return false
        if (player1Score != other.player1Score) return false
        if (player2Score != other.player2Score) return false
        if (player1Debt != other.player1Debt) return false
        if (player2Debt != other.player2Debt) return false
        if (currentTurn != other.currentTurn) return false
        if (isGameOver != other.isGameOver) return false
        if (winner != other.winner) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pits.contentHashCode()
        result = 31 * result + hasQuanPiece.contentHashCode()
        result = 31 * result + player1Score
        result = 31 * result + player2Score
        result = 31 * result + player1Debt
        result = 31 * result + player2Debt
        result = 31 * result + currentTurn.hashCode()
        result = 31 * result + isGameOver.hashCode()
        result = 31 * result + (winner?.hashCode() ?: 0)
        return result
    }
}

