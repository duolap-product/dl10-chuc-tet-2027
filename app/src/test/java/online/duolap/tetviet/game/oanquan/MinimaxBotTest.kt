package online.duolap.tetviet.game.oanquan

import kotlinx.coroutines.runBlocking
import online.duolap.tetviet.game.oanquan.ai.AIDifficulty
import online.duolap.tetviet.game.oanquan.ai.MinimaxBot
import online.duolap.tetviet.game.oanquan.engine.OAnQuanEngine
import online.duolap.tetviet.game.oanquan.engine.PlayerSide
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinimaxBotTest {

    @Test
    fun testBotFindsMoveAtAllDifficulties() = runBlocking {
        val bot = MinimaxBot(PlayerSide.PLAYER_2)
        val state = OAnQuanEngine.createInitialState()
        state.currentTurn = PlayerSide.PLAYER_2

        for (diff in AIDifficulty.entries) {
            val move = bot.findBestMove(state, diff)
            assertNotNull("Bot phải tìm được nước đi ở cấp $diff", move)
            assertTrue("Nước đi phải thuộc ô của Player 2", PlayerSide.PLAYER_2.ownsPit(move!!.pitIndex))
            assertTrue("Ô được chọn phải có quân", state.pits[move.pitIndex] > 0)
        }
    }
}

