package online.duolap.tetviet.game.oanquan

import online.duolap.tetviet.game.oanquan.ai.AIDifficulty
import online.duolap.tetviet.game.oanquan.engine.OAnQuanPreferences
import online.duolap.tetviet.game.oanquan.engine.OAnQuanRewards
import org.junit.Assert.assertEquals
import org.junit.Test

class OAnQuanRewardsTest {

    @Test
    fun aiRewardScalesWithDifficulty() {
        assertEquals(15, OAnQuanRewards.winCoins(OAnQuanPreferences.MODE_AI, AIDifficulty.EASY))
        assertEquals(30, OAnQuanRewards.winCoins(OAnQuanPreferences.MODE_AI, AIDifficulty.MEDIUM))
        assertEquals(50, OAnQuanRewards.winCoins(OAnQuanPreferences.MODE_AI, AIDifficulty.HARD))
    }

    @Test
    fun onlineRewardIgnoresBotDifficulty() {
        AIDifficulty.entries.forEach {
            assertEquals(40, OAnQuanRewards.winCoins(OAnQuanPreferences.MODE_ONLINE, it))
        }
    }

    @Test
    fun passAndPlayGivesNoCoins() {
        AIDifficulty.entries.forEach {
            assertEquals(0, OAnQuanRewards.winCoins(OAnQuanPreferences.MODE_PASS_AND_PLAY, it))
        }
    }
}
