package io.torvox.bridge

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

// ── Wire encoding helpers ──────────────────────────────────────────────

internal class WireWriter {
    private val buf = mutableListOf<Byte>()

    fun writeByte(v: Byte) {
        buf.add(v)
    }

    fun writeI32(v: Int) {
        buf.add((v and 0xFF).toByte())
        buf.add(((v shr 8) and 0xFF).toByte())
        buf.add(((v shr 16) and 0xFF).toByte())
        buf.add(((v shr 24) and 0xFF).toByte())
    }

    fun writeU32(v: UInt) = writeI32(v.toInt())

    fun writeBool(v: Boolean) = writeByte(if (v) 1 else 0)

    fun writeString(v: String) {
        val bytes = v.toByteArray(Charsets.UTF_8)
        writeI32(bytes.size)
        buf.addAll(bytes.toList())
    }

    fun writeBytes(v: ByteArray) {
        writeI32(v.size)
        buf.addAll(v.toList())
    }

    fun toByteArray(): ByteArray = buf.toByteArray()
}

class WireReader(
    data: ByteArray,
) {
    private val data = data
    private var pos = 0

    fun readByte(): Byte {
        val v = data[pos]
        pos += 1
        return v
    }

    fun readI32(): Int {
        val v =
            (data[pos].toInt() and 0xFF) or
                ((data[pos + 1].toInt() and 0xFF) shl 8) or
                ((data[pos + 2].toInt() and 0xFF) shl 16) or
                ((data[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4
        return v
    }

    fun readU32(): UInt = readI32().toUInt()

    fun readBool(): Boolean = readByte() != 0.toByte()

    fun readString(): String {
        val len = readI32()
        if (len == 0) return ""
        val bytes = data.copyOfRange(pos, pos + len)
        pos += len
        return String(bytes, Charsets.UTF_8)
    }

    fun readBytes(): ByteArray {
        val len = readI32()
        if (len == 0) return ByteArray(0)
        val bytes = data.copyOfRange(pos, pos + len)
        pos += len
        return bytes
    }

    fun <T> readResult(
        okReader: (WireReader) -> T,
        errReader: (WireReader) -> T,
    ): Result<T> {
        val tag = readByte()
        return when (tag) {
            0.toByte() -> Result.success(okReader(this))
            1.toByte() -> Result.failure(RuntimeException(errReader(this) as? String ?: "error"))
            else -> Result.failure(RuntimeException("Unknown result tag: $tag"))
        }
    }

    fun <T> readOptional(reader: (WireReader) -> T): T? {
        val tag = readByte()
        return if (tag != 0.toByte()) reader(this) else null
    }

    fun <T> readList(reader: (WireReader) -> T): List<T> {
        val len = readI32()
        return (0 until len).map { reader(this) }
    }
}

// ── Data types matching Rust #[boltffi::data] ─────────────────────────

sealed class Shell {
    object SystemDefault : Shell()

    data class Custom(
        val path: String,
    ) : Shell()

    internal fun wireEncode(w: WireWriter) {
        when (this) {
            is SystemDefault -> {
                w.writeI32(0)
            }

            is Custom -> {
                w.writeI32(1)
                w.writeString(path)
            }
        }
    }
}

data class BridgeTheme(
    val name: String = "",
    val bg: Int = 0,
    val fg: Int = 0,
    val cursor: Int = 0,
    val selectionBg: Int = 0,
    val ansi0: Int = 0,
    val ansi1: Int = 0,
    val ansi2: Int = 0,
    val ansi3: Int = 0,
    val ansi4: Int = 0,
    val ansi5: Int = 0,
    val ansi6: Int = 0,
    val ansi7: Int = 0,
    val ansi8: Int = 0,
    val ansi9: Int = 0,
    val ansi10: Int = 0,
    val ansi11: Int = 0,
    val ansi12: Int = 0,
    val ansi13: Int = 0,
    val ansi14: Int = 0,
    val ansi15: Int = 0,
) {
    internal fun wireEncode(w: WireWriter) {
        w.writeString(name)
        w.writeI32(bg)
        w.writeI32(fg)
        w.writeI32(cursor)
        w.writeI32(selectionBg)
        w.writeI32(ansi0)
        w.writeI32(ansi1)
        w.writeI32(ansi2)
        w.writeI32(ansi3)
        w.writeI32(ansi4)
        w.writeI32(ansi5)
        w.writeI32(ansi6)
        w.writeI32(ansi7)
        w.writeI32(ansi8)
        w.writeI32(ansi9)
        w.writeI32(ansi10)
        w.writeI32(ansi11)
        w.writeI32(ansi12)
        w.writeI32(ansi13)
        w.writeI32(ansi14)
        w.writeI32(ansi15)
    }

    companion object {
        fun wireDecode(r: WireReader): BridgeTheme =
            BridgeTheme(
                name = r.readString(),
                bg = r.readI32(),
                fg = r.readI32(),
                cursor = r.readI32(),
                selectionBg = r.readI32(),
                ansi0 = r.readI32(),
                ansi1 = r.readI32(),
                ansi2 = r.readI32(),
                ansi3 = r.readI32(),
                ansi4 = r.readI32(),
                ansi5 = r.readI32(),
                ansi6 = r.readI32(),
                ansi7 = r.readI32(),
                ansi8 = r.readI32(),
                ansi9 = r.readI32(),
                ansi10 = r.readI32(),
                ansi11 = r.readI32(),
                ansi12 = r.readI32(),
                ansi13 = r.readI32(),
                ansi14 = r.readI32(),
                ansi15 = r.readI32(),
            )
    }
}

data class TerminalConfig(
    val shell: Shell = Shell.SystemDefault,
    val rows: UInt = 24u,
    val cols: UInt = 80u,
    val scrollbackLines: UInt = 50000u,
    val font_size_tenths: UInt = 140u,
    val theme: BridgeTheme = BridgeTheme(),
) {
    fun wireEncode(): ByteArray {
        val w = WireWriter()
        shell.wireEncode(w)
        w.writeU32(rows)
        w.writeU32(cols)
        w.writeU32(scrollbackLines)
        w.writeU32(font_size_tenths)
        theme.wireEncode(w)
        return w.toByteArray()
    }

    companion object {
        fun wireDecode(r: WireReader): TerminalConfig =
            TerminalConfig(
                shell = decodeShell(r),
                rows = r.readU32(),
                cols = r.readU32(),
                scrollbackLines = r.readU32(),
                font_size_tenths = r.readU32(),
                theme = BridgeTheme.wireDecode(r),
            )

        private fun decodeShell(r: WireReader): Shell =
            when (r.readI32()) {
                0 -> Shell.SystemDefault
                1 -> Shell.Custom(r.readString())
                else -> Shell.SystemDefault
            }
    }
}

// ── JNA native interface ──────────────────────────────────────────────

private interface TorvoxNative : Library {
    fun boltffi_torvox_bridge_new(
        config_ptr: ByteArray?,
        config_len: Int,
    ): Long

    fun boltffi_torvox_bridge_free(handle: Long)

    fun boltffi_torvox_bridge_ping(handle: Long): Pointer?

    fun boltffi_torvox_bridge_spawn_terminal(
        handle: Long,
        rows: Int,
        cols: Int,
    ): Pointer?

    fun boltffi_torvox_bridge_set_native_window(
        handle: Long,
        window_ptr: Long,
    ): Pointer?

    fun boltffi_torvox_bridge_render(handle: Long): Pointer?

    fun boltffi_torvox_bridge_resize(
        handle: Long,
        rows: Int,
        cols: Int,
    ): Pointer?

    fun boltffi_torvox_bridge_release_surface(handle: Long)

    fun boltffi_torvox_bridge_scrollback_len(handle: Long): Pointer?

    fun boltffi_torvox_bridge_scrollback_line(
        handle: Long,
        index: Int,
    ): Pointer?

    fun boltffi_torvox_bridge_get_config(handle: Long): Pointer?

    fun boltffi_torvox_bridge_write_to_pty(
        handle: Long,
        data: ByteArray?,
        data_len: Int,
    ): Pointer?

    fun boltffi_torvox_bridge_set_font_size(
        handle: Long,
        size_tenths: Int,
    ): Pointer?

    fun boltffi_torvox_bridge_get_theme_names(handle: Long): Pointer?

    fun boltffi_torvox_bridge_list_fonts(handle: Long): Pointer?

    fun boltffi_last_error_message(): ByteArray?
}

private lateinit var nativeLib: TorvoxNative

private fun ensureLib() {
    if (!::nativeLib.isInitialized) {
        nativeLib = Native.load("torvox_android", TorvoxNative::class.java)
    }
}

// ── FfiBuf reader ─────────────────────────────────────────────────────

private data class FfiBuf(
    val ptr: Long,
    val len: Int,
)

private fun readFfiBuf(p: Pointer?): FfiBuf {
    val ptrVal = p!!.getLong(0)
    val lenVal = p.getLong(8)
    return FfiBuf(ptrVal, lenVal.toInt())
}

private fun readWireBytes(buf: FfiBuf): ByteArray {
    if (buf.ptr == 0L || buf.len == 0) return ByteArray(0)
    return Pointer(buf.ptr).getByteArray(0, buf.len)
}

// ── TorvoxBridge ──────────────────────────────────────────────────────

class TorvoxBridge(
    private val handle: Long,
) : AutoCloseable {
    private var closed = false

    fun ping(): String {
        val p = nativeLib.boltffi_torvox_bridge_ping(handle) ?: return ""
        val bytes = readWireBytes(readFfiBuf(p))
        val r = WireReader(bytes)
        val tag = r.readByte()
        return if (tag == 0.toByte()) r.readString() else throw RuntimeException(r.readString())
    }

    fun spawnTerminal(
        rows: UInt,
        cols: UInt,
    ): Int {
        val p = nativeLib.boltffi_torvox_bridge_spawn_terminal(handle, rows.toInt(), cols.toInt())!!
        val bytes = readWireBytes(readFfiBuf(p))
        val r = WireReader(bytes)
        val tag = r.readByte()
        return if (tag == 0.toByte()) r.readI32() else throw RuntimeException(r.readString())
    }

    fun setNativeWindow(windowPtr: Long) {
        val p = nativeLib.boltffi_torvox_bridge_set_native_window(handle, windowPtr)!!
        val bytes = readWireBytes(readFfiBuf(p))
        val r = WireReader(bytes)
        val tag = r.readByte()
        if (tag != 0.toByte()) throw RuntimeException(r.readString())
    }

    fun render() {
        val p = nativeLib.boltffi_torvox_bridge_render(handle)!!
        val bytes = readWireBytes(readFfiBuf(p))
        val r = WireReader(bytes)
        val tag = r.readByte()
        if (tag != 0.toByte()) throw RuntimeException(r.readString())
    }

    fun resize(
        rows: UInt,
        cols: UInt,
    ) {
        val p = nativeLib.boltffi_torvox_bridge_resize(handle, rows.toInt(), cols.toInt())!!
        val bytes = readWireBytes(readFfiBuf(p))
        val r = WireReader(bytes)
        val tag = r.readByte()
        if (tag != 0.toByte()) throw RuntimeException(r.readString())
    }

    fun releaseSurface() {
        nativeLib.boltffi_torvox_bridge_release_surface(handle)
    }

    fun scrollbackLen(): UInt {
        val p = nativeLib.boltffi_torvox_bridge_scrollback_len(handle)!!
        return WireReader(readWireBytes(readFfiBuf(p))).readU32()
    }

    fun scrollbackLine(index: UInt): String? {
        val p = nativeLib.boltffi_torvox_bridge_scrollback_line(handle, index.toInt())!!
        return WireReader(readWireBytes(readFfiBuf(p))).readOptional { it.readString() }
    }

    fun writeToPty(data: ByteArray) {
        val p = nativeLib.boltffi_torvox_bridge_write_to_pty(handle, data, data.size)!!
        val bytes = readWireBytes(readFfiBuf(p))
        val r = WireReader(bytes)
        val tag = r.readByte()
        if (tag != 0.toByte()) throw RuntimeException(r.readString())
    }

    fun setFontSize(sizeTenths: UInt) {
        val p = nativeLib.boltffi_torvox_bridge_set_font_size(handle, sizeTenths.toInt())!!
        val bytes = readWireBytes(readFfiBuf(p))
        val r = WireReader(bytes)
        val tag = r.readByte()
        if (tag != 0.toByte()) throw RuntimeException(r.readString())
    }

    fun getConfig(): TerminalConfig {
        val p = nativeLib.boltffi_torvox_bridge_get_config(handle)!!
        val bytes = readWireBytes(readFfiBuf(p))
        val r = WireReader(bytes)
        val tag = r.readByte()
        return if (tag == 0.toByte()) TerminalConfig.wireDecode(r) else throw RuntimeException(r.readString())
    }

    fun getThemeNames(): List<String> {
        val p = nativeLib.boltffi_torvox_bridge_get_theme_names(handle)!!
        return WireReader(readWireBytes(readFfiBuf(p))).readList { it.readString() }
    }

    fun listFonts(): List<String> {
        val p = nativeLib.boltffi_torvox_bridge_list_fonts(handle)!!
        return WireReader(readWireBytes(readFfiBuf(p))).readList { it.readString() }
    }

    override fun close() {
        if (!closed) {
            closed = true
            nativeLib.boltffi_torvox_bridge_free(handle)
        }
    }

    protected fun finalize() = close()
}

fun createBridge(config: TerminalConfig): TorvoxBridge {
    ensureLib()
    val wireBytes = config.wireEncode()
    val handle = nativeLib.boltffi_torvox_bridge_new(wireBytes, wireBytes.size)
    if (handle == 0L) {
        val errMsg = nativeLib.boltffi_last_error_message()?.toString(Charsets.UTF_8) ?: "unknown"
        throw RuntimeException("Failed to create TorvoxBridge: $errMsg")
    }
    return TorvoxBridge(handle)
}
