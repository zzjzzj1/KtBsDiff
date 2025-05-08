package com.xiaomi.com.xiaomi.kotlinBsDiff.core

import com.xiaomi.com.xiaomi.kotlinBsDiff.utils.FileUtils
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.math.min

class DeflateOutputStream(
    private val outputStream: OutputStream,
    private val deflateParams: List<ZipFileAnalyzer.ZipDeflateParams?>,
    val firstEntryPos: Long,
) : OutputStream() {

    var cur = 0

    var mode = 0

    var curEntrySize = 0

    var headerBuffer = ByteArray(30)

    var headerCur = 0

    var deflaterOutputStream: DeflaterOutputStream? = null

    var deflaterBufferStream: OutputStream? = null

    var waitDeflateRemain = 0L

    var waitFileHeaderRemain = 0L

    private var curIndex = 0

    private var blockQueue = LinkedBlockingQueue<Task>()

    private var lock = Object()

    data class FileEntry(val compressSize: Long, val unCompressSize: Long, val dataStartPos: Long)

    private var isFirst = true

    private val workThread = Thread {
        while (true) {
            val task = blockQueue.take()
            if (task.type == 1) {
                outputStream.close()
                break
            }
            writeInner(task.byteArray, task.offset, task.len)
        }
        synchronized(lock) {
            lock.notifyAll()
        }
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (isFirst) {
            workThread.start()
            isFirst = false
        }
        val byteArrayCopy = ByteArray(b.size)
        System.arraycopy(b, 0, byteArrayCopy, 0, byteArrayCopy.size)
        blockQueue.add(Task(byteArrayCopy, off, len, index = curIndex++))
    }

    private fun writeInner(b: ByteArray, off: Int, len: Int) {
        if (len == 0) return

        if (cur < firstEntryPos) {
            val writeSize = min(len, (firstEntryPos - cur).toInt())
            outputStream.write(b, off, writeSize)
            cur += writeSize
            writeInner(b, off + writeSize, len - writeSize)
            return
        }

        if (curEntrySize >= deflateParams.size) {
            outputStream.write(b, off, len)
            return
        }

        if (mode == 0) {
            val writeSize = min(len, 30 - headerCur)
            System.arraycopy(b, off, headerBuffer, headerCur, writeSize)
            headerCur += writeSize
            if (headerCur >= headerBuffer.size) {
                mode = 2
                headerCur = 0
                val header = FileUtils.read32BitUnsigned(headerBuffer, 0)
                if (header != ZipFileAnalyzer.FILE_ENTRY_HEADER_SIGNATURE.toLong()) {
                    throw Exception("can find the file entry header")
                }
                val compressLength = FileUtils.read32BitUnsigned(headerBuffer, 18)
                val unCompressLength = FileUtils.read32BitUnsigned(headerBuffer, 22)
                val fileNameLength = FileUtils.read16BitUnsigned(headerBuffer, 26)
                val extraLength = FileUtils.read16BitUnsigned(headerBuffer, 28)
                outputStream.write(headerBuffer, 0, 30)
                val fileEntry = FileEntry(compressLength, unCompressLength, 30 + fileNameLength + extraLength)
                deflateParams[curEntrySize]?.let {
                    val deflater = Deflater(it.level, it.nowrap)
                    deflater.setStrategy(it.strategy)
                    deflaterOutputStream = DeflaterOutputStream(outputStream, deflater).apply {
                        deflaterBufferStream = BufferedOutputStream(this, 32 * 1024)
                    }
                    waitDeflateRemain = fileEntry.unCompressSize
                } ?: run {
                    deflaterOutputStream = null
                    deflaterBufferStream = null
                    waitDeflateRemain = fileEntry.compressSize
                }
                waitFileHeaderRemain = fileNameLength + extraLength
            }
            writeInner(b, off + writeSize, len - writeSize)
            return
        }

        if (mode == 2) {
            val writeSize = min(len, waitFileHeaderRemain.toInt())
            outputStream.write(b, off, writeSize)
            waitFileHeaderRemain -= writeSize
            if (waitFileHeaderRemain == 0L) {
                mode = 1
            }
            writeInner(b, off + writeSize, len - writeSize)
            return
        }

        if (mode == 1) {
            val writeSize = min(len, waitDeflateRemain.toInt())
            deflaterBufferStream?.write(b, off, writeSize) ?: outputStream.write(b, off, writeSize)
            waitDeflateRemain -= writeSize
            if (waitDeflateRemain == 0L) {
                mode = 0
                curEntrySize++
                deflaterBufferStream?.flush()
                deflaterOutputStream?.finish()
                deflaterOutputStream = null
            }
            writeInner(b, off + writeSize, len - writeSize)
            return
        }
    }

    override fun write(b: Int) {
        write(ByteArray(1) { b.toByte() }, 0, 1)
    }

    override fun close() {
        blockQueue.add(Task(ByteArray(0), 0, 0, 1))
        synchronized(lock) {
            lock.wait()
        }
    }

    private class Task(val byteArray: ByteArray, val offset: Int, val len: Int, val type: Int = 0, val index: Int = 0)

}