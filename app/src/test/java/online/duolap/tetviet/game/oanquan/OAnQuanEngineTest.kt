package online.duolap.tetviet.game.oanquan

import online.duolap.tetviet.game.oanquan.engine.MoveDirection
import online.duolap.tetviet.game.oanquan.engine.OAnQuanBoardState
import online.duolap.tetviet.game.oanquan.engine.OAnQuanEngine
import online.duolap.tetviet.game.oanquan.engine.OAnQuanStep
import online.duolap.tetviet.game.oanquan.engine.PlayerSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OAnQuanEngineTest {

    @Test
    fun testInitialBoardSetup() {
        val state = OAnQuanEngine.createInitialState()

        // 10 ô dân mỗi ô có 5 viên
        for (i in 0..4) {
            assertEquals("Ô $i của Player 1 phải có 5 quân", 5, state.pits[i])
        }
        for (i in 6..10) {
            assertEquals("Ô $i của Player 2 phải có 5 quân", 5, state.pits[i])
        }

        // 2 ô quan ban đầu có 0 quân dân, nhưng có 1 quân quan lớn
        assertEquals(0, state.pits[5])
        assertEquals(0, state.pits[11])
        assertTrue(state.hasQuanPieceAt(5))
        assertTrue(state.hasQuanPieceAt(11))

        assertEquals(PlayerSide.PLAYER_1, state.currentTurn)
        assertEquals(0, state.player1Score)
        assertEquals(0, state.player2Score)
        assertFalse(state.isGameOver)
    }

    @Test
    fun testMoveValidation() {
        val state = OAnQuanEngine.createInitialState()

        // Player 1 có thể đi từ các ô 0..4
        for (i in 0..4) {
            assertTrue("Player 1 phải đi được ô $i", OAnQuanEngine.isMoveValid(state, PlayerSide.PLAYER_1, i))
        }

        // Player 1 không được đi từ các ô của Player 2 (6..10)
        for (i in 6..10) {
            assertFalse("Player 1 không được đi ô $i", OAnQuanEngine.isMoveValid(state, PlayerSide.PLAYER_1, i))
        }

        // Không ai được bốc từ ô quan (5, 11)
        assertFalse(OAnQuanEngine.isMoveValid(state, PlayerSide.PLAYER_1, 5))
        assertFalse(OAnQuanEngine.isMoveValid(state, PlayerSide.PLAYER_1, 11))
    }

    @Test
    fun testChungQuanStep() {
        // Tạo bàn cờ tùy biến: Player 1 bốc ô 3 có 1 quân rải sang ô 4 (ngay trước Quan 5)
        val state = OAnQuanEngine.createInitialState()
        // Reset sạch để test rõ ràng
        for (i in 0..11) state.pits[i] = 0
        state.pits[3] = 1 // 1 quân tại ô 3, đi Counter-Clockwise sang ô 4
        state.hasQuanPiece[0] = true // Quan 5 có quân

        val steps = OAnQuanEngine.applyMove(state, 3, MoveDirection.COUNTER_CLOCKWISE)

        // Viên cuối rơi tại ô 4, ô kế tiếp là Quan 5 -> Chững Quan!
        val chungQuanStep = steps.filterIsInstance<OAnQuanStep.ChungQuan>().firstOrNull()
        assertNotNull("Phải có bước Chững Quan", chungQuanStep)
        assertEquals(5, chungQuanStep?.quanPitIndex)

        // Lượt chơi chuyển sang Player 2
        assertEquals(PlayerSide.PLAYER_2, state.currentTurn)
    }

    @Test
    fun testSingleCapture() {
        val state = OAnQuanEngine.createInitialState()
        for (i in 0..11) state.pits[i] = 0

        // Ô 0 có 1 quân, ô 1 rỗng, ô 2 có 6 quân
        state.pits[0] = 1
        state.pits[1] = 0
        state.pits[2] = 6

        // Player 1 bốc ô 0 đi COUNTER_CLOCKWISE (+1):
        // Rải 1 viên vào ô 1 -> ô 1 có 1 quân.
        // Hết sỏi! Ô tiếp theo là ô 2 (có 6 quân) -> bốc ô 2 rải tiếp!
        // Thay vào đó, hãy test: ô 0 có 1 quân, ô 1 rỗng, ô 2 rỗng, ô 3 có 4 quân:
        // Đi ô 4 có 1 quân (rải vào 5), Quan 5 là ô quan -> Chững quan.

        // Setup chuẩn cho Capture:
        // Ô 3 có 1 quân. Ô 4 (rơi viên cuối).
        // Ô 5 (Quan) rỗng hoàn toàn (đã bị ăn trước đó).
        // Ô 6 có 8 quân.
        state.pits[3] = 1
        state.pits[4] = 0
        state.pits[5] = 0
        state.hasQuanPiece[0] = false // Quan 5 rỗng
        state.pits[6] = 8
        state.pits[7] = 5 // Player 2 còn quân ở ô khác nên không bị kích hoạt cứu trợ rải quân

        // Player 1 bốc ô 3 (1 quân) đi COUNTER_CLOCKWISE:
        // Rải vào ô 4. Hết sỏi.
        // Ô tiếp theo là 5 (rỗng!).
        // Ô tiếp theo sau 5 là 6 (có 8 quân) -> ĂN Ô 6!
        val steps = OAnQuanEngine.applyMove(state, 3, MoveDirection.COUNTER_CLOCKWISE)

        val captureStep = steps.filterIsInstance<OAnQuanStep.Capture>().firstOrNull()
        assertNotNull("Phải ăn được quân ô 6", captureStep)
        assertEquals(6, captureStep?.targetPitIndex)
        assertEquals(8, captureStep?.pointsAwarded)
        assertEquals(8, state.player1Score)
        assertEquals(0, state.pits[6])
    }

    @Test
    fun testChainCapture() {
        val state = OAnQuanEngine.createInitialState()
        for (i in 0..11) state.pits[i] = 0

        // Bốc ô 0 (1 quân) -> rơi vào ô 1.
        // Ô 2 trống.
        // Ô 3 có 4 quân (Ăn lần 1).
        // Ô 4 trống.
        // Ô 5 (Quan) có 1 Quan lớn (Ăn lần 2 - Ăn Quan liên hoàn!).
        state.pits[0] = 1
        state.pits[1] = 0
        state.pits[2] = 0
        state.pits[3] = 4
        state.pits[4] = 0
        state.pits[5] = 2 // 2 quân dân trong Quan
        state.hasQuanPiece[0] = true // Quan lớn (10đ)

        val steps = OAnQuanEngine.applyMove(state, 0, MoveDirection.COUNTER_CLOCKWISE)

        val captures = steps.filterIsInstance<OAnQuanStep.Capture>()
        assertEquals("Phải ăn liên tiếp 2 lần", 2, captures.size)

        // Ăn lần 1: Ô 3 được 4 điểm
        assertEquals(3, captures[0].targetPitIndex)
        assertEquals(4, captures[0].pointsAwarded)
        assertFalse(captures[0].isChain)

        // Ăn lần 2: Ô 5 (Quan) được 2 (dân) + 10 (quan) = 12 điểm
        assertEquals(5, captures[1].targetPitIndex)
        assertEquals(12, captures[1].pointsAwarded)
        assertTrue(captures[1].capturedQuan)
        assertTrue(captures[1].isChain)

        assertEquals(16, state.player1Score)
        assertFalse(state.hasQuanPieceAt(5))
        assertEquals(0, state.pits[5])
    }

    @Test
    fun testReliefWhenHomeEmpty() {
        val state = OAnQuanEngine.createInitialState()
        for (i in 0..4) state.pits[i] = 0 // Player 1 cạn sạch quân
        state.player1Score = 12 // Có 12 điểm trong kho

        val reliefSteps = OAnQuanEngine.checkAndApplyReliefIfNeeded(state, PlayerSide.PLAYER_1)
        assertEquals(1, reliefSteps.size)

        val relief = reliefSteps[0] as OAnQuanStep.ReliefStones
        assertEquals(0, relief.debtAdded)
        assertEquals(7, state.player1Score) // 12 - 5 = 7

        // Mỗi ô 0..4 đã được rải 1 viên
        for (i in 0..4) {
            assertEquals(1, state.pits[i])
        }
    }

    @Test
    fun testReliefWithDebtWhenScoreInsufficient() {
        val state = OAnQuanEngine.createInitialState()
        for (i in 0..4) state.pits[i] = 0
        state.player1Score = 2 // Chỉ có 2 điểm trong kho

        val reliefSteps = OAnQuanEngine.checkAndApplyReliefIfNeeded(state, PlayerSide.PLAYER_1)
        val relief = reliefSteps[0] as OAnQuanStep.ReliefStones

        assertEquals(3, relief.debtAdded) // Cần vay 3 viên
        assertEquals(3, state.player1Debt)
        assertEquals(0, state.player1Score)

        for (i in 0..4) {
            assertEquals(1, state.pits[i])
        }
    }

    @Test
    fun testGameOverHetQuanTanDan() {
        val state = OAnQuanEngine.createInitialState()
        for (i in 0..11) state.pits[i] = 0

        // Quan 5 đã bị ăn trước đó
        state.hasQuanPiece[0] = false
        state.pits[5] = 0

        // Quan 11 chuẩn bị bị ăn nốt
        state.hasQuanPiece[1] = true
        state.pits[11] = 0

        // Player 1 đi từ ô 2 (1 quân) -> rơi vào ô 3 (trống).
        // Ô 4 trống.
        // Ô 5 rỗng hoàn toàn -> không ăn được.
        // Hãy test Player 1 đi từ ô 8 (nếu là P2) hoặc P1 đi ngược chiều:
        // Ô 2 có 1 quân, ô 1 trống, ô 0 trống -> rải vào 1, 0 trống, 11 (Quan) có quan -> ăn quan 11!
        state.pits[2] = 1
        state.pits[1] = 0 // Rơi vào ô 1
        state.pits[0] = 0 // Ô trống đệm
        // Quan 11 bị ăn!

        // Trên sân nhà Player 1 còn 3 quân tại ô 4
        state.pits[4] = 3
        // Trên sân nhà Player 2 còn 2 quân tại ô 7
        state.pits[7] = 2

        val steps = OAnQuanEngine.applyMove(state, 2, MoveDirection.CLOCKWISE)

        // Quan 11 bị ăn -> cả 2 Quan đều hết -> HẾT QUAN TÀN DÂN
        assertTrue("Trò chơi phải kết thúc", state.isGameOver)

        val gameOverStep = steps.filterIsInstance<OAnQuanStep.GameOver>().firstOrNull()
        assertNotNull("Phải có bước GameOver", gameOverStep)

        // Quân trên sân nhà được thu về: P1 được 4 (ô 1 có 1 viên vừa rải + ô 4 có 3 viên), P2 được 2 (ô 7 có 2 viên)
        assertEquals(4, gameOverStep?.p1Harvest)
        assertEquals(2, gameOverStep?.p2Harvest)
        // Ô 1, ô 4 và ô 7 trên bàn cờ đã được thu về 0
        assertEquals(0, state.pits[1])
        assertEquals(0, state.pits[4])
        assertEquals(0, state.pits[7])

        // Player 1 thắng do ăn Quan 11 (10đ) + harvest (4đ) = 14đ vs P2 (2đ)
        assertEquals(PlayerSide.PLAYER_1, state.winner)
    }
}

