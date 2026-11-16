package online.duolap.tetviet.data.wishing

import online.duolap.tetviet.data.wishing.WishContentFilter.Rejection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WishContentFilterTest {

    private fun ok(text: String) = assertNull("\"$text\" phải được phép", WishContentFilter.check(text))
    private fun bad(text: String, reason: Rejection) = assertEquals("\"$text\"", reason, WishContentFilter.check(text))

    @Test
    fun normalWishesPass() {
        ok("Gia đình luôn khỏe mạnh, bình an")
        ok("Con lớn lên học giỏi, ngoan ngoãn")       // "lớn" không phải từ tục
        ok("Chúc các bạn năm mới vui vẻ")              // "các"
        ok("Buổi sáng nào cũng tràn đầy năng lượng")   // "buổi"
        ok("Mong được đeo nhẫn cưới năm nay")          // "đeo"
        ok("Thi đỗ đại học Bách Khoa 2027")
        ok("Kiếm được 100 triệu để mua xe cho bố mẹ")
        ok("Happy new year, wish you all the best")
    }

    @Test
    fun emptyAndTooLong() {
        bad("   ", Rejection.EMPTY)
        bad("a".repeat(WishContentFilter.MAX_LENGTH + 1).replace("aaaaaaa", "abcdefg"), Rejection.TOO_LONG)
    }

    @Test
    fun badWordsAreBlockedWithOrWithoutAccents() {
        bad("đm năm nay xui quá", Rejection.BAD_WORD)
        bad("dit me cuoc doi", Rejection.BAD_WORD)
        bad("Đụ má thằng sếp", Rejection.BAD_WORD)
        bad("vcl luôn", Rejection.BAD_WORD)
        bad("f u c k you", Rejection.BAD_WORD)
        bad("đ.ị.t m.ẹ", Rejection.BAD_WORD)
        bad("Mẹ mày", Rejection.BAD_WORD)
        bad("what the shit", Rejection.BAD_WORD)
    }

    @Test
    fun contactInfoAndLinksAreBlocked() {
        bad("Liên hệ 0912 345 678 nhé", Rejection.PHONE)
        bad("Zalo +84.912.345.678", Rejection.PHONE)
        bad("Vào www.example.com nhận quà", Rejection.LINK)
        bad("Xem https://bit.ly/abc", Rejection.LINK)
        bad("Ghé shopcuatoi.vn", Rejection.LINK)
        bad("Mail toi: abc@gmail.com", Rejection.EMAIL)
    }

    @Test
    fun repeatedCharactersAreSpam() {
        bad("aaaaaaaaaaaa", Rejection.SPAM)
        bad("Năm mới vui !!!!!!!!!", Rejection.SPAM)
        ok("Năm mới vui!!!")
    }
}
