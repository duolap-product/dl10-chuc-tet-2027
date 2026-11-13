package online.duolap.tetviet.game.oanquan.engine

/**
 * Các bước diễn tiến chi tiết của nước đi trong Ô ăn quan.
 * Giúp UI có thể chạy animation tuần tự trực quan (sỏi bay, rải từng viên, ăn quân, chững quan).
 */
sealed class OAnQuanStep {
    /**
     * Khi người chơi hết sạch quân trên 5 ô nhà, phải lấy 5 quân từ kho rải vào 5 ô (hoặc vay nợ).
     */
    data class ReliefStones(
        val player: PlayerSide,
        val debtAdded: Int,
        val currentScore: Int
    ) : OAnQuanStep()

    /**
     * Bốc toàn bộ quân ở một ô lên tay.
     */
    data class PickUp(
        val pitIndex: Int,
        val count: Int
    ) : OAnQuanStep()

    /**
     * Rải 1 viên sỏi vào ô chỉ định.
     */
    data class DropOne(
        val pitIndex: Int,
        val remainingInHand: Int,
        val currentPitCount: Int
    ) : OAnQuanStep()

    /**
     * Viên cuối cùng rơi ngay trước ô Quan. Không được bốc ô Quan để rải tiếp -> Dừng lượt.
     */
    data class ChungQuan(
        val quanPitIndex: Int
    ) : OAnQuanStep()

    /**
     * Ăn toàn bộ quân ở ô mục tiêu (sau một ô trống).
     */
    data class Capture(
        val targetPitIndex: Int,
        val capturedDan: Int,
        val capturedQuan: Boolean,
        val pointsAwarded: Int,
        val player: PlayerSide,
        val newPlayerScore: Int,
        val isChain: Boolean = false
    ) : OAnQuanStep()

    /**
     * Bước nhảy qua ô trống trung gian trong chuỗi ăn liên hoàn.
     */
    data class ChainGap(
        val emptyPitIndex: Int
    ) : OAnQuanStep()

    /**
     * Sau ô trống là một ô trống khác hoặc không có gì để ăn -> Lượt kết thúc.
     */
    data class DeadEnd(
        val emptyNextPit: Int,
        val emptyTargetPit: Int
    ) : OAnQuanStep()

    /**
     * Kết thúc lượt đi bình thường, chuyển lượt.
     */
    data class TurnFinished(
        val nextPlayer: PlayerSide
    ) : OAnQuanStep()

    /**
     * Kết thúc ván đấu (Hết quan tàn dân).
     */
    data class GameOver(
        val winner: PlayerSide?,
        val p1Score: Int,
        val p2Score: Int,
        val p1Harvest: Int,
        val p2Harvest: Int,
        val p1Final: Int,
        val p2Final: Int
    ) : OAnQuanStep()
}

