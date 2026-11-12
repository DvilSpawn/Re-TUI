package ohi.andre.consolelauncher.managers.mediaplayer

import java.util.Collections
import java.util.IdentityHashMap
import kotlin.random.Random

/** What plays next: one ordered list of items and the place in it. Items are compared by identity. */
internal class PlayQueue<T : Any> {
    enum class Repeat { OFF, ALL, ONE }

    private val items = ArrayList<T>()
    private var original: List<T>? = null

    @get:Synchronized var index = 0
        private set

    @get:Synchronized @set:Synchronized var repeat = Repeat.ALL

    @get:Synchronized val size: Int get() = items.size
    @get:Synchronized val current: T? get() = items.getOrNull(index)
    @get:Synchronized val isLast: Boolean get() = index >= items.lastIndex
    @get:Synchronized val shuffled: Boolean get() = original != null

    @Synchronized fun snapshot(): List<T> = ArrayList(items)

    @Synchronized fun replace(list: List<T>, at: Int = 0) {
        items.clear()
        items.addAll(list)
        original = null
        index = at.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }

    @Synchronized fun jumpTo(at: Int): Boolean {
        if (at !in items.indices) return false
        index = at
        return true
    }

    @Synchronized fun jumpTo(item: T): Boolean {
        val at = items.indexOfFirst { it === item }
        return at != -1 && jumpTo(at)
    }

    /** What [next] would move to, or null when it would stop or stay on the same item. */
    @Synchronized fun peekNext(): T? = when {
        items.size < 2 || repeat == Repeat.ONE -> null
        index < items.lastIndex -> items[index + 1]
        repeat == Repeat.OFF -> null
        else -> items[0]
    }

    /** Steps forward; false when the queue has ended. */
    @Synchronized fun next(): Boolean {
        if (items.isEmpty()) return false
        if (index < items.lastIndex) index++
        else if (repeat == Repeat.OFF) return false
        else index = 0
        return true
    }

    /** Steps back; false when already at the start with nothing to wrap to. */
    @Synchronized fun previous(): Boolean {
        if (items.isEmpty()) return false
        if (index > 0) index--
        else if (repeat == Repeat.OFF) return false
        else index = items.lastIndex
        return true
    }

    @Synchronized fun addNext(item: T) {
        if (items.isEmpty()) items.add(item) else items.add(index + 1, item)
    }

    @Synchronized fun addLast(item: T) {
        items.add(item)
    }

    /** Removes one item. Removing the current item makes the one after it current. */
    @Synchronized fun removeAt(at: Int) {
        if (at !in items.indices) return
        items.removeAt(at)
        if (at < index) index--
        index = index.coerceAtMost((items.size - 1).coerceAtLeast(0))
    }

    /** Moves one item, keeping the same item current. */
    @Synchronized fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val playing = items[index]
        items.add(to, items.removeAt(from))
        index = items.indexOfFirst { it === playing }
    }

    @Synchronized fun clear() = replace(emptyList())

    @Synchronized fun shuffle(random: Random = Random.Default, keepCurrent: Boolean = true) {
        if (items.isEmpty()) return
        if (original == null) original = ArrayList(items)
        val playing = items[index]
        if (keepCurrent) {
            val rest = items.filterNot { it === playing }.shuffled(random)
            items.clear()
            items.add(playing)
            items.addAll(rest)
        } else {
            items.shuffle(random)
        }
        index = 0
    }

    /** Back to the order before shuffling; anything added since goes at the end. */
    @Synchronized fun unshuffle() {
        val before = original ?: return
        val playing = current
        val present = identitySet(items)
        val known = identitySet(before)
        val restored = before.filter { it in present } + items.filterNot { it in known }
        replace(restored)
        if (playing != null) jumpTo(playing)
    }

    private fun identitySet(list: List<T>): MutableSet<T> =
        Collections.newSetFromMap(IdentityHashMap<T, Boolean>()).apply { addAll(list) }
}
