package online.duolap.tetviet.game.oanquan.engine

enum class MoveDirection(val delta: Int) {
    COUNTER_CLOCKWISE(1), // Thuận chiều kim đồng hồ / Tăng index (0 -> 1 -> ... -> 5(Quan) -> 6 -> ...)
    CLOCKWISE(-1);        // Ngược chiều kim đồng hồ / Giảm index (4 -> 3 -> ... -> 0 -> 11(Quan) -> 10 -> ...)

    fun nextIndex(currentIndex: Int): Int {
        return (currentIndex + delta + 12) % 12
    }
}

