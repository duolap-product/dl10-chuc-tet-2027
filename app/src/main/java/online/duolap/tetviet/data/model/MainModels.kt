package online.duolap.tetviet.data.model

data class MondayCountdownInfo(
    val mondaysRemaining: Int,
    val nextMondayText: String,
    val lastMondayText: String,
    val isTodayMonday: Boolean,
    val motivationalText: String
)
