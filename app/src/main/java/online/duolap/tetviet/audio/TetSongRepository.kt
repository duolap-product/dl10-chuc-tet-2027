package online.duolap.tetviet.audio

import online.duolap.tetviet.data.model.TetSong

object TetSongRepository {

    val defaultSong = TetSong(
        id = "bgm_tet_celebration",
        title = "Tết bình an (Remix)",
        artist = "u_exvhfptbxo",
        source = "pixabay",
        url = "https://duolap.online/resources/audio/tet_binh_an_remix.mp3",
        durationText = "03:34"
    )

    private val songs = listOf(
        defaultSong,
        TetSong(
            id = "bgm_tet_celebration",
            title = "Tết Nguyên Đán Việt Nam",
            artist = "AudioBay_BGM",
            source = "pixabay",
            url = "https://duolap.online/resources/audio/tet_nguyen_dan_vn.mp3",
            durationText = "02:28"
        )
    )

    fun getAllSongs(): List<TetSong> = songs

    fun getSongById(id: String): TetSong {
        return songs.firstOrNull { it.id == id } ?: defaultSong
    }
}

