package com.xiaomi.com.xiaomi.kotlinBsDiff.core

import com.xiaomi.com.xiaomi.kotlinBsDiff.utils.FileUtils
import java.io.*

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
            oldFileStream.seek(oldFilePos.toLong())
            val buffer = ByteArray(32 * 1024)
            val bufferDiff = ByteArray(32 * 1024)
            var numRead = 0
            while (numRead < diffLen) {
                val currentRead = if ((diffLen - numRead) > buffer.size) buffer.size else (diffLen - numRead)
                val realRead = oldFileStream.read(buffer, 0, currentRead)
                if (currentRead != realRead) {
                    throw Exception("error when read file")
                }
                patchFileStream.read(bufferDiff, 0, realRead)
                for (i in 0 until realRead) {
                    buffer[i] = (buffer[i] + bufferDiff[i]).toByte()
                }
                outputStream.write(buffer, 0, realRead)
                numRead += realRead
            }
            cur += diffLen
            val extraLen = readInt()
            FileUtils.streamFile(patchFileStream, extraLen.toLong(), object: FileUtils.StreamSolver{
                override fun solveBuffer(buffer: ByteArray, length: Int) {
                    outputStream.write(buffer, 0, length)
                }
            })
            cur += extraLen
        }
    }


    fun readInt(): Int {
        return FileUtils.read32BitUnsigned(patchFileStream).toInt()
    }
}