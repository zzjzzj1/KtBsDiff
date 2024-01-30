package com.xiaomi.com.xiaomi.kotlinBsDiff.core

import com.xiaomi.com.xiaomi.kotlinBsDiff.utils.FileUtils
import java.io.*
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

class BsPatch(private val oldFileStream: RandomAccessFile, private val patchFileStream: InputStream) {

    fun patch(outputStream: OutputStream) {
        patchInner(outputStream)
    }

    private fun patchInner(outputStream: OutputStream) {
        val fileSize = readInt()
        var cur = 0
        while (cur < fileSize) {
            val diffLen = readInt()
            val oldFilePos = readInt()
            val buffer = readDataFromFile(oldFileStream, oldFilePos, diffLen)
            val diffPatchNumber = readInt()
            for (i in 0..<diffPatchNumber) {
                val diffStartPos = readInt()
                val diffPatchLen = readInt()
                val bufferNew = patchFileStream.readNBytes(diffPatchLen)
                for (j in 0..<diffPatchLen) {
                    buffer[diffStartPos + j] = (buffer[diffStartPos + j] + bufferNew[j]).toByte()
                }
            }
            outputStream.write(buffer)
            cur += buffer.size
            val extraLen = readInt()
            val extraData = ByteArray(extraLen)
            patchFileStream.read(extraData)
            outputStream.write(extraData)
            cur += extraData.size
        }
    }

    private fun readDataFromFile(file: RandomAccessFile, startPos: Int, length: Int): ByteArray {
        val buffer = ByteArray(length)
        file.seek(startPos.toLong())
        file.read(buffer)
        return buffer
    }


    fun readInt(): Int {
        return FileUtils.read32BitUnsigned(patchFileStream).toInt()
    }
}