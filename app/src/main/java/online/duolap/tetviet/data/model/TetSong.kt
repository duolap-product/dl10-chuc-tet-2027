package online.duolap.tetviet.data.model

import androidx.annotation.RawRes

/**
 * Đối tượng bài hát nhạc nền Tết Việt.
 * @property id Mã định danh duy nhất của bài hát
 * @property title Tên bài hát
 * @property artist Tác giả / Nhạc sĩ / Nghệ sĩ thể hiện
 * @property source Nguồn xuất xứ của bài hát (nhạc truyền thống, thư viện audio, YouTube...)
 * @property url Link phát nhạc trực tuyến (URL)
 * @property rawResId ID tài nguyên cục bộ nếu có trong app (R.raw.*) để phát offline
 * @property durationText Chuỗi hiển thị thời lượng bài hát
 */
data class TetSong(
    val id: String,
    val title: String,
    val artist: String,
    val source: String,
    val url: String,
    @param:RawRes val rawResId: Int? = null,
    val durationText: String = ""
)

