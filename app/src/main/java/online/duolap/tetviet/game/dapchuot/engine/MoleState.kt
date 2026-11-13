package online.duolap.tetviet.game.dapchuot.engine

data class MoleState(
    val holeIndex: Int,
    val type: MoleType,
    var isVisible: Boolean = false,
    var isHit: Boolean = false,
    var appearTimeMs: Long = 0L,
    var stayDurationMs: Long = 1200L
)
