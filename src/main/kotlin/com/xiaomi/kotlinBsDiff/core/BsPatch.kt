package com.xiaomi.com.xiaomi.kotlinBsDiff.core

import com.xiaomi.com.xiaomi.kotlinBsDiff.utils.FileUtils
import java.io.File
import java.io.OutputStream

class BsPatch(oldFileName: String, patchFileName: String) {
    private val oldData = FileUtils.readDataFromFile(File(oldFileName))
    private val patchData = FileUtils.readDataFromFile(File(patchFileName))
    private var pos = 0

    private fun init() {
        pos = 0
    }

    fun patch(fileName: String) {
        val patchFile = FileUtils.createFile(fileName)
        val outputStream = patchFile.outputStream()
        init()
        patchInner(outputStream)
    }

    private fun patchInner(outputStream: OutputStream) {
        val patchedFileByte = ByteArray(readInt())
        var cur = 0
        while (pos < patchData.size) {
            val diffLen = readInt()
            val oldFilePos = readInt()
            val recordCur = cur
            for (i in oldFilePos..<oldFilePos + diffLen) {
                patchedFileByte[cur++] = oldData[i]
            }
            val diffPatchNumber = readInt()
            for (i in 0..<diffPatchNumber) {
                val diffStartPos = readInt()
                val diffPatchLen = readInt()
                for (j in 0..<diffPatchLen) {
                    val waitChangePos = recordCur + diffStartPos + j
                    patchedFileByte[waitChangePos] = (patchedFileByte[waitChangePos] + patchData[pos++]).toByte()
                }
            }
            val extraLen = readInt()
            for (i in 0..<extraLen) {
                patchedFileByte[cur++] = patchData[pos++]
            }
        }
        outputStream.write(patchedFileByte)
        outputStream.close()

    }


    private fun readInt(): Int {
        val res = patchData[3 + pos].toInt() and 0xFF or (
                (patchData[2 + pos].toInt() and 0xFF) shl 8) or (
                (patchData[1 + pos].toInt() and 0xFF) shl 16) or (
                (patchData[0 + pos].toInt() and 0xFF) shl 24)
        pos += 4
        return res
    }
}