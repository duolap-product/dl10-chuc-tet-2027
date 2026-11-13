package online.duolap.tetviet.game.oanquan.engine

import kotlin.math.max

object OAnQuanEngine {

    /**
     * Khởi tạo bàn cờ mới tiêu chuẩn: 10 ô dân mỗi ô 5 viên sỏi, 2 ô quan mỗi ô 1 quan lớn.
     */
    fun createInitialState(): OAnQuanBoardState {
        return OAnQuanBoardState()
    }

    /**
     * Kiểm tra xem một nước đi từ ô [pitIndex] có hợp lệ với người chơi [player] hay không.
     */
    fun isMoveValid(state: OAnQuanBoardState, player: PlayerSide, pitIndex: Int): Boolean {
        if (state.isGameOver) return false
        if (state.currentTurn != player) return false
        if (!player.ownsPit(pitIndex)) return false
        return state.pits[pitIndex] > 0
    }

    /**
     * Lấy toàn bộ các nước đi hợp lệ của [player] ở trạng thái hiện tại.
     * Mỗi ô dân có quân sẽ có 2 hướng đi: COUNTER_CLOCKWISE và CLOCKWISE.
     */
    fun getValidMoves(state: OAnQuanBoardState, player: PlayerSide): List<Pair<Int, MoveDirection>> {
        if (state.isGameOver || state.currentTurn != player) return emptyList()

        val validMoves = mutableListOf<Pair<Int, MoveDirection>>()
        for (pit in player.ownedPits) {
            if (state.pits[pit] > 0) {
                validMoves.add(Pair(pit, MoveDirection.COUNTER_CLOCKWISE))
                validMoves.add(Pair(pit, MoveDirection.CLOCKWISE))
            }
        }
        return validMoves
    }

    /**
     * Kiểm tra và thực hiện cứu trợ rải quân (Vay quân) nếu toàn bộ 5 ô dân của [player] đều hết sạch sỏi.
     */
    fun checkAndApplyReliefIfNeeded(state: OAnQuanBoardState, player: PlayerSide): List<OAnQuanStep> {
        if (state.isGameOver) return emptyList()

        val isAllHomeEmpty = player.ownedPits.all { state.pits[it] == 0 }
        if (!isAllHomeEmpty) return emptyList()

        val steps = mutableListOf<OAnQuanStep>()
        val stonesNeeded = 5

        val currentScore = if (player == PlayerSide.PLAYER_1) state.player1Score else state.player2Score
        val available = max(0, currentScore)

        val debtAdded: Int
        if (available >= stonesNeeded) {
            if (player == PlayerSide.PLAYER_1) {
                state.player1Score -= stonesNeeded
            } else {
                state.player2Score -= stonesNeeded
            }
            debtAdded = 0
        } else {
            debtAdded = stonesNeeded - available
            if (player == PlayerSide.PLAYER_1) {
                state.player1Score = 0
                state.player1Debt += debtAdded
            } else {
                state.player2Score = 0
                state.player2Debt += debtAdded
            }
        }

        // Rải 1 viên vào mỗi ô của người chơi
        for (pit in player.ownedPits) {
            state.pits[pit] = 1
        }

        val newScore = if (player == PlayerSide.PLAYER_1) state.player1Score else state.player2Score
        steps.add(OAnQuanStep.ReliefStones(player, debtAdded, newScore))

        return steps
    }

    /**
     * Thực hiện một nước đi: bốc quân tại [startPit] và rải theo hướng [direction].
     * Trả về danh sách toàn bộ các bước diễn tiến chi tiết ([OAnQuanStep]) phục vụ animation.
     */
    fun applyMove(
        state: OAnQuanBoardState,
        startPit: Int,
        direction: MoveDirection
    ): List<OAnQuanStep> {
        val player = state.currentTurn
        require(isMoveValid(state, player, startPit)) {
            "Nước đi không hợp lệ: Người chơi $player không thể đi từ ô $startPit"
        }

        val steps = mutableListOf<OAnQuanStep>()
        var currentPos = startPit
        var safetyCounter = 0
        val maxSubTurns = 120 // Tránh vòng lặp vô hạn ngoài ý muốn

        var continueTurn = true
        while (continueTurn && safetyCounter++ < maxSubTurns) {
            // Bốc quân tại currentPos
            var handStones = state.pits[currentPos]
            state.pits[currentPos] = 0
            steps.add(OAnQuanStep.PickUp(currentPos, handStones))

            // Rải từng viên
            while (handStones > 0) {
                currentPos = direction.nextIndex(currentPos)
                state.pits[currentPos] += 1
                handStones -= 1
                steps.add(OAnQuanStep.DropOne(currentPos, handStones, state.pits[currentPos]))
            }

            // Đã rải hết viên cuối cùng trên tay tại currentPos. Xét ô liền kề tiếp theo:
            val nextPit = direction.nextIndex(currentPos)

            if (!OAnQuanBoardState.isQuanPit(nextPit) && state.pits[nextPit] > 0) {
                // TRƯỜNG HỢP 1: Ô liền kề là Ô Dân và có quân -> Bốc tiếp rải cùng hướng!
                currentPos = nextPit
                continue
            } else if (OAnQuanBoardState.isQuanPit(nextPit) && !state.isPitEmpty(nextPit)) {
                // TRƯỜNG HỢP 2: Ô liền kề là Ô Quan có quân -> Chững Quan (Dừng lượt)
                steps.add(OAnQuanStep.ChungQuan(nextPit))
                continueTurn = false
            } else if (state.isPitEmpty(nextPit)) {
                // TRƯỜNG HỢP 3: Ô liền kề là Ô Trống -> Xét xem có ăn được quân hay không
                var emptyPit = nextPit
                var targetPit = direction.nextIndex(emptyPit)
                var hasCapturedAny = false

                // Vòng lặp ăn liên hoàn (ăn cách quãng / chẹt)
                while (state.isPitEmpty(emptyPit) && !state.isPitEmpty(targetPit)) {
                    val capturedDan = state.pits[targetPit]
                    val capturedQuan = state.hasQuanPieceAt(targetPit)
                    val points = state.getPointsInPit(targetPit)

                    // Thu quân
                    state.pits[targetPit] = 0
                    if (capturedQuan) {
                        state.removeQuanPieceAt(targetPit)
                    }

                    if (player == PlayerSide.PLAYER_1) {
                        state.player1Score += points
                    } else {
                        state.player2Score += points
                    }

                    val currentScore = if (player == PlayerSide.PLAYER_1) state.player1Score else state.player2Score
                    steps.add(
                        OAnQuanStep.Capture(
                            targetPitIndex = targetPit,
                            capturedDan = capturedDan,
                            capturedQuan = capturedQuan,
                            pointsAwarded = points,
                            player = player,
                            newPlayerScore = currentScore,
                            isChain = hasCapturedAny
                        )
                    )
                    hasCapturedAny = true

                    // Kiểm tra chuỗi tiếp theo: ô kế tiếp targetPit có trống không, và ô sau đó có quân không?
                    val nextEmptyCandidate = direction.nextIndex(targetPit)
                    val nextTargetCandidate = direction.nextIndex(nextEmptyCandidate)

                    if (state.isPitEmpty(nextEmptyCandidate) && !state.isPitEmpty(nextTargetCandidate)) {
                        steps.add(OAnQuanStep.ChainGap(nextEmptyCandidate))
                        emptyPit = nextEmptyCandidate
                        targetPit = nextTargetCandidate
                    } else {
                        break
                    }
                }

                if (!hasCapturedAny) {
                    // Sau ô trống là ô trống khác hoặc không có gì để ăn -> Chết nước
                    steps.add(OAnQuanStep.DeadEnd(emptyPit, targetPit))
                }

                continueTurn = false
            } else {
                // Trường hợp ngoại lệ khác (ví dụ ô Quan rỗng)
                continueTurn = false
            }
        }

        // Hậu kiểm tra sau lượt đi:
        // 1. Kiểm tra điều kiện "Hết quan tàn dân"
        if (state.areBothQuansCaptured()) {
            finalizeGameOver(state, steps)
            return steps
        }

        // 2. Chuyển lượt sang đối thủ
        val nextPlayer = player.opponent
        state.currentTurn = nextPlayer
        steps.add(OAnQuanStep.TurnFinished(nextPlayer))

        // 3. Kiểm tra xem người chơi kế tiếp có bị hết quân ở 5 ô nhà không
        val isOpponentEmpty = nextPlayer.ownedPits.all { state.pits[it] == 0 }
        if (isOpponentEmpty) {
            val totalNextPlayerResources = (if (nextPlayer == PlayerSide.PLAYER_1) state.player1Score else state.player2Score) -
                    (if (nextPlayer == PlayerSide.PLAYER_1) state.player1Debt else state.player2Debt)

            // Nếu đối phương hoàn toàn không còn bất kỳ quân nào trên bàn cờ lẫn trong kho điểm để rải -> Kết thúc
            val allPitsEmpty = (0..11).all { state.isPitEmpty(it) }
            if (allPitsEmpty) {
                finalizeGameOver(state, steps)
            } else {
                val reliefSteps = checkAndApplyReliefIfNeeded(state, nextPlayer)
                steps.addAll(reliefSteps)
            }
        }

        return steps
    }

    /**
     * Kết thúc ván đấu khi "Hết quan tàn dân":
     * - Thu toàn bộ quân dân còn lại trên 5 ô nhà của mỗi người chơi.
     * - Trừ các khoản nợ vay quân (nếu có).
     * - Phân định người chiến thắng.
     */
    private fun finalizeGameOver(state: OAnQuanBoardState, steps: MutableList<OAnQuanStep>) {
        state.isGameOver = true

        val p1Harvest = (0..4).sumOf { state.pits[it] }
        (0..4).forEach { state.pits[it] = 0 }

        val p2Harvest = (6..10).sumOf { state.pits[it] }
        (6..10).forEach { state.pits[it] = 0 }

        state.player1Score += p1Harvest
        state.player2Score += p2Harvest

        val p1Final = state.player1Score - state.player1Debt
        val p2Final = state.player2Score - state.player2Debt

        state.winner = when {
            p1Final > p2Final -> PlayerSide.PLAYER_1
            p2Final > p1Final -> PlayerSide.PLAYER_2
            else -> null // Hòa cờ
        }

        steps.add(
            OAnQuanStep.GameOver(
                winner = state.winner,
                p1Score = state.player1Score,
                p2Score = state.player2Score,
                p1Harvest = p1Harvest,
                p2Harvest = p2Harvest,
                p1Final = p1Final,
                p2Final = p2Final
            )
        )
    }
}

