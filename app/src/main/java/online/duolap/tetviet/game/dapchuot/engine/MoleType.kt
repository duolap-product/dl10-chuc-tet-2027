package online.duolap.tetviet.game.dapchuot.engine

enum class MoleType(val points: Int, val displayName: String) {
    NORMAL(10, "Chuột thường"),
    GOLDEN(30, "Chuột vàng"),
    BOMB(-15, "Bom")
}
