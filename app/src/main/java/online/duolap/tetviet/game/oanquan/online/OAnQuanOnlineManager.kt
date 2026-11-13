package online.duolap.tetviet.game.oanquan.online

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import online.duolap.tetviet.game.oanquan.engine.MoveDirection
import online.duolap.tetviet.game.oanquan.engine.OAnQuanBoardState
import online.duolap.tetviet.game.oanquan.engine.PlayerSide
import kotlin.random.Random

data class OAnQuanRoomData(
    val roomId: String = "",
    val hostUid: String = "",
    val hostName: String = "Chủ phòng",
    val hostScore: Int = 0,
    val guestUid: String? = null,
    val guestName: String? = null,
    val guestScore: Int = 0,
    val status: String = STATUS_WAITING, // WAITING, PLAYING, FINISHED
    val pits: List<Long> = listOf(5, 5, 5, 5, 5, 0, 5, 5, 5, 5, 5, 0),
    val hasQuan0: Boolean = true,
    val hasQuan1: Boolean = true,
    val currentTurnUid: String = "",
    val lastMovePit: Int = -1,
    val lastMoveDirection: String = "",
    val lastMoveTimestamp: Long = 0L,
    val winnerUid: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_WAITING = "WAITING"
        const val STATUS_PLAYING = "PLAYING"
        const val STATUS_FINISHED = "FINISHED"
    }

    fun toBoardState(localUid: String): OAnQuanBoardState {
        val isLocalHost = localUid == hostUid
        val pitInts = pits.map { it.toInt() }.toIntArray()
        val currentTurnSide = if (currentTurnUid == hostUid) {
            if (isLocalHost) PlayerSide.PLAYER_1 else PlayerSide.PLAYER_2
        } else {
            if (isLocalHost) PlayerSide.PLAYER_2 else PlayerSide.PLAYER_1
        }

        return OAnQuanBoardState(
            pits = pitInts,
            hasQuanPiece = booleanArrayOf(hasQuan0, hasQuan1),
            player1Score = if (isLocalHost) hostScore else guestScore,
            player2Score = if (isLocalHost) guestScore else hostScore,
            currentTurn = currentTurnSide,
            isGameOver = status == STATUS_FINISHED,
            winner = if (winnerUid == null) null else if (winnerUid == localUid) PlayerSide.PLAYER_1 else PlayerSide.PLAYER_2
        )
    }
}

class OAnQuanOnlineManager {

    private val firestore = FirebaseFirestore.getInstance()
    private val roomsCollection = firestore.collection("oanquan_rooms")

    suspend fun createRoom(hostUid: String, hostName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Tạo mã phòng 6 chữ số ngẫu nhiên
            val roomId = (Random.nextInt(100000, 999999)).toString()
            val roomData = OAnQuanRoomData(
                roomId = roomId,
                hostUid = hostUid,
                hostName = hostName,
                status = OAnQuanRoomData.STATUS_WAITING,
                currentTurnUid = hostUid,
                updatedAt = System.currentTimeMillis()
            )
            roomsCollection.document(roomId).set(roomData).await()
            roomId
        }
    }

    suspend fun joinRoom(roomId: String, guestUid: String, guestName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = roomsCollection.document(roomId).get().await()
            if (!doc.exists()) {
                throw IllegalStateException("Phòng $roomId không tồn tại!")
            }
            val room = doc.toObject(OAnQuanRoomData::class.java)
                ?: throw IllegalStateException("Dữ liệu phòng lỗi!")

            if (room.status != OAnQuanRoomData.STATUS_WAITING && room.guestUid != guestUid) {
                throw IllegalStateException("Phòng đã đủ 2 người chơi hoặc đã bắt đầu!")
            }

            // Cập nhật người chơi 2 và chuyển trạng thái PLAYING
            roomsCollection.document(roomId).update(
                mapOf(
                    "guestUid" to guestUid,
                    "guestName" to guestName,
                    "status" to OAnQuanRoomData.STATUS_PLAYING,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
            Unit
        }
    }

    suspend fun quickMatch(uid: String, name: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Tìm các phòng đang WAITING
            val query = roomsCollection
                .whereEqualTo("status", OAnQuanRoomData.STATUS_WAITING)
                .limit(5)
                .get()
                .await()

            val availableRoom = query.documents.mapNotNull { it.toObject(OAnQuanRoomData::class.java) }
                .firstOrNull { it.hostUid != uid }

            if (availableRoom != null) {
                // Tham gia phòng có sẵn
                joinRoom(availableRoom.roomId, uid, name).getOrThrow()
                availableRoom.roomId
            } else {
                // Tự tạo phòng mới
                createRoom(uid, name).getOrThrow()
            }
        }
    }

    fun listenRoom(
        roomId: String,
        onUpdate: (OAnQuanRoomData) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return roomsCollection.document(roomId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.toObject(OAnQuanRoomData::class.java)
                if (data != null) {
                    onUpdate(data)
                }
            }
        }
    }

    suspend fun sendMove(
        roomId: String,
        moverUid: String,
        nextTurnUid: String,
        pitIndex: Int,
        direction: MoveDirection,
        boardState: OAnQuanBoardState,
        isHost: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pitsList = boardState.pits.map { it.toLong() }
            val updates = mutableMapOf<String, Any>(
                "pits" to pitsList,
                "hasQuan0" to boardState.hasQuanPiece[0],
                "hasQuan1" to boardState.hasQuanPiece[1],
                "currentTurnUid" to nextTurnUid,
                "lastMovePit" to pitIndex,
                "lastMoveDirection" to direction.name,
                "lastMoveTimestamp" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )

            if (isHost) {
                updates["hostScore"] = boardState.player1Score
                updates["guestScore"] = boardState.player2Score
            } else {
                updates["hostScore"] = boardState.player2Score
                updates["guestScore"] = boardState.player1Score
            }

            if (boardState.isGameOver) {
                updates["status"] = OAnQuanRoomData.STATUS_FINISHED
                val winnerUid = when (boardState.winner) {
                    PlayerSide.PLAYER_1 -> if (isHost) moverUid else nextTurnUid
                    PlayerSide.PLAYER_2 -> if (isHost) nextTurnUid else moverUid
                    null -> ""
                }
                updates["winnerUid"] = winnerUid
            }

            roomsCollection.document(roomId).set(updates, SetOptions.merge()).await()
            Unit
        }
    }

    suspend fun leaveRoom(roomId: String, uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            roomsCollection.document(roomId).delete().await()
            Unit
        }
    }
}

