package moe.ouom.neriplayer.core.player.queue.state
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import moe.ouom.neriplayer.core.player.queue.identity.QueueSongIdentity
import moe.ouom.neriplayer.data.model.playback.queue.PlayerQueueSessionSnapshot
import moe.ouom.neriplayer.data.model.playback.queue.PlayerQueueSnapshot
import moe.ouom.neriplayer.core.player.queue.policy.PlayerQueueNavigationOwner
import moe.ouom.neriplayer.data.model.SongItem

/** 抽到与当前已发布队列相同的顺序时最多重抽的次数（含首次抽签） */
private const val MAX_SHUFFLE_REDRAW_ATTEMPTS = 8

class PlayerQueueStateStore(private val identity: QueueSongIdentity) {
    private val lock = Any()
    @Volatile
    private var current = PlayerQueueSessionSnapshot()
    private val _playlistFlow = MutableStateFlow<List<SongItem>>(emptyList())
    val playlistFlow: StateFlow<List<SongItem>> = _playlistFlow
    private val _shuffleModeFlow = MutableStateFlow(false)
    val shuffleModeFlow: StateFlow<Boolean> = _shuffleModeFlow

    fun snapshot(): PlayerQueueSnapshot = current.queue

    fun sessionSnapshot(): PlayerQueueSessionSnapshot = current

    fun select(index: Int) {
        synchronized(lock) {
            publishLocked(current.copy(queue = current.queue.selecting(index)))
        }
    }

    fun publish(playlist: List<SongItem>, currentIndex: Int): PlayerQueueSnapshot =
        synchronized(lock) {
            publishQueueLocked(PlayerQueueSnapshot.from(playlist, currentIndex))
        }

    fun update(
        transform: (PlayerQueueSnapshot) -> PlayerQueueSnapshot?
    ): PlayerQueueSnapshot? = synchronized(lock) {
        // 变换只计算队列，播放器和磁盘操作留在锁外，避免阻塞其它队列写入
        val updated = transform(current.queue) ?: return@synchronized null
        publishQueueLocked(updated)
    }

    fun updatePlaylist(
        transform: (List<SongItem>) -> List<SongItem>?
    ): PlayerQueueSnapshot? = update { snapshot ->
        val updated = transform(snapshot.playlist) ?: return@update null
        PlayerQueueSnapshot.from(updated, snapshot.currentIndex)
    }

    fun updateSongMatching(
        song: SongItem,
        transform: (SongItem) -> SongItem?
    ): SongItem? {
        var updatedSong: SongItem? = null
        updatePlaylist { playlist ->
            val index = playlist.indexOfFirst { identity.sameIdentity(it, song) }
            if (index < 0) return@updatePlaylist null
            val updated = transform(playlist[index]) ?: return@updatePlaylist null
            updatedSong = updated
            playlist.toMutableList().also { it[index] = updated }
        }
        return updatedSong
    }

    fun projectSongs(transform: (List<SongItem>) -> List<SongItem>): Boolean = synchronized(lock) {
        val queue = projectSnapshot(current.queue, transform)
        val restore = current.shuffleRestore?.let { projectSnapshot(it, transform) }
        if (queue === current.queue && restore === current.shuffleRestore) return@synchronized false
        publishLocked(current.copy(queue = queue, shuffleRestore = restore))
        true
    }

    private fun projectSnapshot(
        snapshot: PlayerQueueSnapshot,
        transform: (List<SongItem>) -> List<SongItem>
    ): PlayerQueueSnapshot {
        val playlist = transform(snapshot.playlist)
        return if (playlist === snapshot.playlist) snapshot else PlayerQueueSnapshot.from(playlist, snapshot.currentIndex)
    }

    fun setLocalShuffle(
        enabled: Boolean,
        currentSong: SongItem?,
        shuffleRemaining: (MutableList<Int>) -> Unit = { it.shuffle() }
    ): PlayerQueueSnapshot? = synchronized(lock) {
        if (enabled == current.shuffleEnabled) return@synchronized null
        val restore = if (enabled) captureRestore(current.queue) else null
        val updated = if (enabled) {
            shuffleAvoidingPublishedOrder(current.queue, shuffleRemaining)
        } else {
            PlayerQueueNavigationOwner.restoreShuffleOrder(
                current.queue, current.shuffleRestore?.playlist, currentSong,
                current.shuffleRestore?.currentIndex ?: -1, identity
            )
        }
        publishLocked(PlayerQueueSessionSnapshot(updated ?: current.queue, enabled, restore))
        updated
    }

    fun startPlayback(
        queue: PlayerQueueSnapshot,
        shuffleLocally: Boolean,
        shuffleRemaining: (MutableList<Int>) -> Unit = { it.shuffle() }
    ): PlayerQueueSnapshot = synchronized(lock) {
        val shouldShuffle = current.shuffleEnabled && shuffleLocally
        val restore = if (shouldShuffle) captureRestore(queue) else null
        val next = if (shouldShuffle) {
            shuffleAvoidingPublishedOrder(queue, shuffleRemaining) ?: queue
        } else queue
        publishLocked(PlayerQueueSessionSnapshot(next, current.shuffleEnabled, restore))
        next
    }

    /**
     * 洗牌并避免抽到与当前已发布队列相同的顺序。
     *
     * 同一歌单连续两次「随机播放」抽到完全相同的顺序是最容易被察觉的随机性问题：上一次的顺序此时
     * 就在队列里，重抽到不同顺序为止即可（顺序规模很小时命中概率极低，上限只作为防御）。
     * 洗牌返回 null 表示这次抽签没有改变队列，按入参顺序参与比较。
     */
    private fun shuffleAvoidingPublishedOrder(
        queue: PlayerQueueSnapshot,
        shuffleRemaining: (MutableList<Int>) -> Unit
    ): PlayerQueueSnapshot? {
        if (queue.playlist.isEmpty()) return null
        val publishedOrderKeys = current.queue.playlist.map(identity::stableKey)
        var candidate = drawnOrderOrInput(queue, shuffleRemaining)
        repeat(MAX_SHUFFLE_REDRAW_ATTEMPTS - 1) {
            if (!sameSongOrder(candidate.playlist, publishedOrderKeys)) return candidate
            candidate = drawnOrderOrInput(queue, shuffleRemaining)
        }
        return candidate
    }

    private fun drawnOrderOrInput(
        queue: PlayerQueueSnapshot,
        shuffleRemaining: (MutableList<Int>) -> Unit
    ): PlayerQueueSnapshot = PlayerQueueNavigationOwner.sequentialShuffle(queue, shuffleRemaining) ?: queue

    private fun sameSongOrder(playlist: List<SongItem>, orderKeys: List<String>): Boolean {
        if (playlist.size != orderKeys.size) return false
        return playlist.withIndex().all { (index, song) -> identity.stableKey(song) == orderKeys[index] }
    }

    fun restoreSession(
        queue: PlayerQueueSnapshot,
        shuffleEnabled: Boolean,
        shuffleRestore: PlayerQueueSnapshot?
    ) = synchronized(lock) {
        val restore = shuffleRestore.takeIf { shuffleEnabled && queue.playlist.isNotEmpty() }
        publishLocked(PlayerQueueSessionSnapshot(queue, shuffleEnabled, restore))
    }

    fun setShuffleMode(enabled: Boolean, clearRestore: Boolean = false) = synchronized(lock) {
        publishLocked(current.copy(
            shuffleEnabled = enabled,
            shuffleRestore = if (clearRestore) null else current.shuffleRestore
        ))
    }

    fun clearShuffleRestore(): Boolean = synchronized(lock) {
        val hadRestore = current.shuffleRestore != null
        publishLocked(current.copy(shuffleRestore = null))
        hadRestore
    }

    private fun captureRestore(queue: PlayerQueueSnapshot): PlayerQueueSnapshot? =
        PlayerQueueNavigationOwner.captureShuffleRestore(queue)?.let {
            it.selecting(it.currentIndex.coerceAtLeast(0))
        }

    private fun publishQueueLocked(next: PlayerQueueSnapshot): PlayerQueueSnapshot {
        publishLocked(current.copy(
            queue = next,
            shuffleRestore = current.shuffleRestore.takeIf { next.playlist.isNotEmpty() }
        ))
        return next
    }

    private fun publishLocked(next: PlayerQueueSessionSnapshot) {
        current = next
        _playlistFlow.value = next.queue.playlist
        _shuffleModeFlow.value = next.shuffleEnabled
    }
}
