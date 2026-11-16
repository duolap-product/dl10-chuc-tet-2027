package online.duolap.tetviet.data.wishing

import java.text.Normalizer

/**
 * Lọc nội dung điều ước trước khi treo công khai lên cây (kiểm duyệt mức A, chạy trong app).
 *
 * Chặn: từ tục tĩu / xúc phạm (kể cả viết không dấu, viết tắt, chèn dấu chấm hay khoảng trắng giữa các chữ),
 * đường link, số điện thoại, email, và chuỗi ký tự lặp vô nghĩa. Bổ sung cho cơ chế báo cáo: lá bị báo cáo
 * đủ [WishingTreeRepository.REPORTS_TO_HIDE] lần thì tự ẩn. Kotlin thuần, có unit test.
 */
object WishContentFilter {

    enum class Rejection { EMPTY, TOO_LONG, BAD_WORD, LINK, PHONE, EMAIL, SPAM }

    const val MAX_LENGTH = 100

    /**
     * So khớp nguyên văn (chữ thường, có dấu), theo ranh giới từ. Dùng cho những từ mà bỏ dấu đi sẽ trùng chữ
     * bình thường ("lồn"/"lớn", "cặc"/"các", "buồi"/"buổi", "đéo"/"đeo"...).
     */
    private val EXACT = listOf(
        "lồn", "buồi", "cặc", "địt", "đéo", "đếch", "đụ", "nứng", "đĩ", "loz", "l0n", "cak", "djt", "đjt",
        "đm", "đmm", "dm", "dmm", "đcm", "dcm", "đkm", "dkm", "vcl", "vkl", "vl", "cl", "clm", "cmm", "đb", "dbrr",
        "đù má", "đù mé", "đụ má", "đụ mẹ", "địt mẹ", "chó đẻ", "đồ chó", "thằng chó", "óc chó", "mẹ mày", "bố mày",
        "con đĩ", "chết đi", "giết"
    )

    /** So khớp sau khi bỏ dấu (bắt kiểu gõ không dấu): chỉ những cụm không thể trùng câu chúc bình thường. */
    private val PLAIN = listOf(
        "dit me", "dit con me", "du ma", "du me", "dmm", "dcm", "dkm", "vcl", "vkl", "clm", "djt", "loz",
        "fuck", "fucking", "fck", "shit", "bitch", "pussy", "cunt", "asshole", "bastard", "porn", "nigger"
    )

    /** Bắt kiểu chèn ký tự giữa các chữ ("f.u.c.k", "đ ị t m ẹ"): so trên chuỗi đã bỏ dấu và bỏ khoảng trắng. */
    private val SQUASHED = listOf("ditme", "ditconme", "duma", "fuck", "shit", "bitch", "pussy", "cunt", "asshole", "porn")

    private val LINK = Regex("""(https?://|www\.|\b[a-z0-9-]+\.(com|vn|net|org|info|xyz|io|me|ly|link|top|club|shop)\b)""", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("""[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}""", RegexOption.IGNORE_CASE)
    private val REPEAT = Regex("""(.)\1{6,}""")

    /** null = được phép treo; khác null = lý do bị chặn. */
    fun check(raw: String): Rejection? {
        val text = raw.trim()
        if (text.isEmpty()) return Rejection.EMPTY
        if (text.length > MAX_LENGTH) return Rejection.TOO_LONG
        if (EMAIL.containsMatchIn(text)) return Rejection.EMAIL
        if (LINK.containsMatchIn(text)) return Rejection.LINK
        if (looksLikePhone(text)) return Rejection.PHONE
        if (REPEAT.containsMatchIn(text.lowercase())) return Rejection.SPAM
        if (hasBlockedWord(text)) return Rejection.BAD_WORD
        return null
    }

    private fun looksLikePhone(text: String): Boolean {
        // 9 chữ số trở lên, cho phép xen dấu cách / chấm / gạch (vd. 0912 345 678, +84.912.345.678)
        var digits = 0
        for (c in text) {
            when {
                c.isDigit() -> { digits++; if (digits >= 9) return true }
                c == ' ' || c == '.' || c == '-' || c == '+' || c == '(' || c == ')' -> Unit
                else -> digits = 0
            }
        }
        return false
    }

    private fun hasBlockedWord(text: String): Boolean {
        val lower = text.lowercase()
        if (EXACT.any { containsWord(lower, it) }) return true
        val plain = " ${normalize(lower)} "
        if (PLAIN.any { plain.contains(" $it ") }) return true
        // Chỉ xét kiểu chèn ký tự khi có ít nhất 3 "chữ" một ký tự đứng liền nhau (f u c k, đ.ị.t)
        val tokens = plain.trim().split(' ')
        var singles = 0
        var maxSingles = 0
        for (t in tokens) { if (t.length == 1) { singles++; maxSingles = maxOf(maxSingles, singles) } else singles = 0 }
        if (maxSingles >= 3) {
            val squashed = tokens.joinToString("")
            if (SQUASHED.any { squashed.contains(it) }) return true
        }
        return false
    }

    private fun containsWord(text: String, word: String): Boolean {
        var from = 0
        while (true) {
            val i = text.indexOf(word, from)
            if (i < 0) return false
            val before = if (i == 0) ' ' else text[i - 1]
            val afterIdx = i + word.length
            val after = if (afterIdx >= text.length) ' ' else text[afterIdx]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            from = i + 1
        }
    }

    /** Bỏ dấu tiếng Việt, đ→d, số giống chữ (0→o, 1→i, 3→e, 4→a, @→a), bỏ ký tự lạ. */
    fun normalize(text: String): String {
        val noAccent = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd').replace('Đ', 'd')
            .lowercase()
        val sb = StringBuilder(noAccent.length)
        for (c in noAccent) {
            sb.append(
                when (c) {
                    '0' -> 'o'; '1' -> 'i'; '3' -> 'e'; '4' -> 'a'; '@' -> 'a'; '$' -> 's'
                    in 'a'..'z' -> c
                    else -> ' '
                }
            )
        }
        return sb.toString().replace(Regex(" +"), " ").trim()
    }
}
