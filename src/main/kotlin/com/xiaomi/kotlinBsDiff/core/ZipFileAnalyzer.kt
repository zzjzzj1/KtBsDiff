package com.xiaomi.com.xiaomi.kotlinBsDiff.core

import java.io.*
import java.util.*
import java.util.zip.*

class ZipFileAnalyzer(val file: File) {

    private val fileStream = RandomAccessFile(file, "r")
    private var endOfDirectoryAnalyzeResult: EndOfDirectoryAnalyzeResult? = null
    private var fileEntryList: List<ZipFileEntry>? = null
    var firstEntryStartPos: Long = Long.MAX_VALUE

    /*
    copy from Googles patcher
     */
    private fun getLevelsByStrategy(): Map<Int, List<Int>> {
        val levelsByStrategy: MutableMap<Int, List<Int>> = HashMap()
        // The best order for the levels is simply the order of popularity in the world, which is
        // expected to be default (6), maximum compression (9), and fastest (1).
        // The rest of the levels are rarely encountered and their order is mostly irrelevant.
        levelsByStrategy[0] = Collections.unmodifiableList(mutableListOf(6, 9, 1, 4, 2, 3, 5, 7, 8))
        levelsByStrategy[1] = Collections.unmodifiableList(mutableListOf(6, 9, 4, 5, 7, 8))
        // Strategy 2 does not have the concept of levels, so vacuously call it 1.
        levelsByStrategy[2] = listOf(1)
        return Collections.unmodifiableMap(levelsByStrategy)
    }

    companion object {
        const val END_OF_DIRECTORY_SIGNATURE: Int = 0x06054b50
        const val LOCAL_FILE_HEADER_SIGNATURE: Int = 0x02014b50
        const val FILE_ENTRY_HEADER_SIGNATURE: Int = 0x04034b50
    }

    fun getFileStream(): RandomAccessFile {
        return fileStream
    }

    fun getFileLength(): Long {
        return file.length()
    }

    // 自后向前寻找目录结束标识，大概率出现在第一个32kb
    private fun findEndOfDirectoryRecord(bufferSize: Int = 1024 * 32): Long {
        val buffer = ByteArray(bufferSize)
        var currentBufferHead = Math.max(0, file.length() - bufferSize)
        var last4Bytes = 0
        while (currentBufferHead >= 0) {
            fileStream.seek(currentBufferHead)
            val read = fileStream.read(buffer, 0, bufferSize)
            for (i in read - 1 downTo 0) {
                last4Bytes = last4Bytes shl 8
                last4Bytes = last4Bytes or buffer[i].toInt()
                if (last4Bytes == END_OF_DIRECTORY_SIGNATURE) {
                    return currentBufferHead + i
                }
            }
            if (currentBufferHead == 0L) {
                break
            }
            currentBufferHead = Math.max(0, currentBufferHead - read)
        }
        return -1
    }

    private fun queryFileDeflateParams(zipEntries: List<ZipFileEntry>) {
        for (entry in zipEntries) {
            if (entry.compressFileLength == 0L) {
                continue
            }
            // ensure this file using deflate64
            if (entry.compressType == 8) {
                val tryMatchDeflateParamByEntry = tryMatchDeflateParamByEntry(entry)
                if (tryMatchDeflateParamByEntry == null) {
                    println("${entry.compressFileLength} ${entry.unCompressFileLength}")
                    break
                }
                entry.deflateParams = tryMatchDeflateParamByEntry
            }
        }
    }

    fun tryMatchDeflateParamByEntry(entry: ZipFileEntry): ZipDeflateParams? {
        val buffer = ByteArray(entry.compressFileLength.toInt())
        fileStream.seek(entry.startPos)
        fileStream.read(buffer)
        val levelMap = getLevelsByStrategy()
        for (nowrap in booleanArrayOf(true, false)) {
            val inflater = Inflater(nowrap)
            val deflater = Deflater(0, nowrap)
            for (strategy in 0 until 3) {
                deflater.setStrategy(strategy)
                for (level in levelMap.get(strategy)!!) {
                    deflater.setLevel(level)
                    inflater.reset()
                    deflater.reset()
                    if (match(buffer, inflater, deflater)) {
                        inflater.end()
                        deflater.end()
                        return ZipDeflateParams(level, nowrap, strategy)
                    }

                }
            }
            inflater.end()
            deflater.end()
        }
        return null
    }

    private class MatchOutputStream(val exceptBuffer: ByteArray) : OutputStream() {
        var cur = 0
        override fun write(byte: Int) {
            if (cur >= exceptBuffer.size) {
                throw Exception("cant match shit")
            }
            if (byte != exceptBuffer[cur++].toInt()) {
                throw Exception("cant match 1")
            }
        }

        override fun write(b: ByteArray) {
            write(b, 0, b.size)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            for (i in off until off + len) {
                if (cur >= exceptBuffer.size) {
                    throw Exception("cant match fucker")
                }
                if (b[i] != exceptBuffer[cur++]) {
                    throw Exception("cant match 2")
                }
            }
        }

        fun isEnd() {
            if (cur != exceptBuffer.size) {
                throw Exception("cant match end")
            }
        }
    }

    private fun match(buffer: ByteArray, inflater: Inflater, deflater: Deflater): Boolean {
        try {
            val copyBuffer = ByteArray(32 * 1024)
            val inputStream = InflaterInputStream(ByteArrayInputStream(buffer), inflater, copyBuffer.size)
            val matchOutputStream = MatchOutputStream(buffer)
            val outputStream = DeflaterOutputStream(matchOutputStream, deflater, copyBuffer.size)
            var numRead: Int
            while ((inputStream.read(copyBuffer).also { numRead = it }) >= 0) {
                outputStream.write(copyBuffer, 0, numRead)
            }
            outputStream.finish()
            outputStream.flush()
            matchOutputStream.isEnd()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun parseFileEntry(zipFileEntry: ZipFileEntry) {
        fileStream.seek(zipFileEntry.startPos)
        if (read32BitUnsigned() != FILE_ENTRY_HEADER_SIGNATURE.toLong()) {
            throw Exception("cant parse file entry")
        }
        fileStream.skipBytes(4)
        val compressType = read16BitUnsigned()
        zipFileEntry.compressType = compressType.toInt()
        fileStream.skipBytes(16)
        val fileLength = read16BitUnsigned()
        val extraLength = read16BitUnsigned()
        val byte = ByteArray(fileLength.toInt())
        fileStream.read(byte, 0, byte.size)
        zipFileEntry.fileName = String(byte)
        zipFileEntry.startPos += 30 + fileLength + extraLength
    }

    fun listAllEntries(matchDeflateParams: Boolean = true): List<ZipFileEntry> {
        fileEntryList?.let {
            return it
        }
        val endOfDirectoryResult = getEndOfDirectoryAnalyzeResult()
        if (
            endOfDirectoryResult.centralDirectoryNum == -1 ||
            endOfDirectoryResult.centralDirectoryPos == -1L ||
            endOfDirectoryResult.centralDirectoryLength == -1L
        ) {
            throw Exception("cant analyze zip file")
        }
        val resultList = ArrayList<ZipFileEntry>()
        fileStream.seek(endOfDirectoryResult.centralDirectoryPos)
        for (i in 0 until endOfDirectoryResult.centralDirectoryNum) {
            if (read32BitUnsigned() != LOCAL_FILE_HEADER_SIGNATURE.toLong()) {
                throw Exception("cant find local file header")
            }
            val zipFileEntry = ZipFileEntry(-1L, -1L, -1L)
            fileStream.skipBytes(16)
            zipFileEntry.compressFileLength = read32BitUnsigned()
            zipFileEntry.unCompressFileLength = read32BitUnsigned()
            var shouldSkipNumber = 0
            for (j in 0..2) {
                shouldSkipNumber += read16BitUnsigned().toInt()
            }
            fileStream.skipBytes(8)
            zipFileEntry.startPos = read32BitUnsigned()
            firstEntryStartPos = Math.min(firstEntryStartPos, zipFileEntry.startPos)
            resultList += zipFileEntry
            fileStream.skipBytes(shouldSkipNumber)
        }
        for (zipFileEntry in resultList) {
            parseFileEntry(zipFileEntry)
        }
        if (matchDeflateParams) {
            queryFileDeflateParams(resultList)
        }
        fileEntryList = resultList
        return resultList
    }

    fun getEndOfDirectoryAnalyzeResult(): EndOfDirectoryAnalyzeResult {
        endOfDirectoryAnalyzeResult?.let {
            return it
        }
        val result = EndOfDirectoryAnalyzeResult(-1, -1, -1)
        val areaStartPos = findEndOfDirectoryRecord()
        if (areaStartPos == -1L) {
            return result
        }
        fileStream.seek(areaStartPos)
        val read16BitUnsigned = read32BitUnsigned()
        if (read16BitUnsigned != END_OF_DIRECTORY_SIGNATURE.toLong()) {
            return result
        }
        fileStream.skipBytes(6)
        result.centralDirectoryNum = read16BitUnsigned().toInt()
        result.centralDirectoryLength = read32BitUnsigned()
        result.centralDirectoryPos = read32BitUnsigned()
        endOfDirectoryAnalyzeResult = result
        return result
    }

    private fun readByteOrDie(): Int {
        val result = fileStream.read()
        if (result == -1) {
            throw IOException("EOF")
        }
        return result
    }

    private fun read32BitUnsigned(): Long {
        var value: Long = readByteOrDie().toLong()
        value = value or (readByteOrDie().toLong() shl 8)
        value = value or (readByteOrDie().toLong() shl 16)
        value = value or (readByteOrDie().toLong() shl 24)
        return value
    }

    private fun read16BitUnsigned(): Long {
        var value: Long = readByteOrDie().toLong()
        value = value or (readByteOrDie().toLong() shl 8)
        return value
    }

    data class EndOfDirectoryAnalyzeResult(
        var centralDirectoryNum: Int,
        var centralDirectoryPos: Long,
        var centralDirectoryLength: Long
    )

    data class ZipFileEntry(
        var startPos: Long,
        var unCompressFileLength: Long,
        var compressFileLength: Long,
        var fileName: String = "",
        var compressType: Int = -1,
        var deflateParams: ZipDeflateParams? = null
    )

    data class ZipDeflateParams(
        var level: Int,
        var nowrap: Boolean,
        var strategy: Int
    )


}