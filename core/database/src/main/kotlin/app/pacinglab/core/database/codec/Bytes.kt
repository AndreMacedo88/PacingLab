package app.pacinglab.core.database.codec

/** Growable byte buffer with LEB128 varint writing. */
internal class ByteWriter(initialCapacity: Int = 256) {
    private var buffer = ByteArray(initialCapacity)
    private var size = 0

    fun byte(value: Int) {
        ensure(1)
        buffer[size++] = value.toByte()
    }

    /** Unsigned LEB128 of [value] interpreted as an unsigned 64-bit number. */
    fun varLong(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            byte(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        byte(v.toInt())
    }

    fun signedVarLong(value: Long) = varLong(zigZag(value))

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensure(extra: Int) {
        if (size + extra > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
    }
}

internal class ByteReader(private val data: ByteArray) {
    private var position = 0

    val hasRemaining: Boolean get() = position < data.size

    fun byte(): Int {
        if (position >= data.size) throw CorruptStreamException("Unexpected end of data at byte $position")
        return data[position++].toInt() and 0xFF
    }

    fun varLong(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (shift > 63) throw CorruptStreamException("Varint longer than 10 bytes at byte $position")
            val b = byte()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }

    fun signedVarLong(): Long = unZigZag(varLong())

    fun count(): Int {
        val n = varLong()
        if (n < 0 || n > MAX_COUNT) throw CorruptStreamException("Implausible element count $n")
        return n.toInt()
    }

    private companion object {
        const val MAX_COUNT = 50_000_000L
    }
}

internal fun zigZag(n: Long): Long = (n shl 1) xor (n shr 63)

internal fun unZigZag(z: Long): Long = (z ushr 1) xor -(z and 1)

class CorruptStreamException(message: String) : IllegalStateException(message)
