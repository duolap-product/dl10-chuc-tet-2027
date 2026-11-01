package online.duolap.tetviet.ext

fun String?.ifNullOrEmpty(default: () -> String): String {
    if (this.isNullOrEmpty()) return default.invoke()
    return this
}
