package com.xiaomi.com.xiaomi.kotlinBsDiff.core

import com.xiaomi.com.xiaomi.kotlinBsDiff.utils.FileUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

class BsPatch(private val oldFileName: String, patchFileName: String) {
    private var oldData = ByteArray(1)
    private val patchData = FileUtils.readDataFromFile(File(patchFileName))
    private var pos = 0

    private fun init() {
        pos = 0
    }

    fun patch(fileName: String) {
        val patchFile = FileUtils.createFile(fileName)
        val outputStream = patchFile.outputStream()
        init()
        val oldFile = File(oldFileName)
        val zipFileSolver = ZipFileSolver(ZipFileAnalyzer(oldFile))
        oldData = zipFileSolver.unCompressFile()
        patchInner(outputStream)
    }

    private fun patchInner(outputStream: OutputStream) {
        val patchedFileByte = ByteArray(readInt())
        var cur = 0
        while (cur < patchedFileByte.size) {
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
        deflateFile(patchedFileByte, pos, outputStream)
        outputStream.close()

    }

    private fun deflateFile(patchedFileByte: ByteArray, position: Int, outputStream: OutputStream) {
        var cur = 0
        for (i in position until patchData.size) {
            val entryHeader = read32BitUnsigned(patchedFileByte, cur)
            if (entryHeader != ZipFileAnalyzer.FILE_ENTRY_HEADER_SIGNATURE.toLong()) {
                throw Exception("deflate file error by can find file entry header")
            }
            val compressLength = read32BitUnsigned(patchedFileByte, cur + 18)
            val unCompressLength = read32BitUnsigned(patchedFileByte, cur + 22)
            val fileNameLength = read16BitUnsigned(patchedFileByte, cur + 26)
            val extraLength = read16BitUnsigned(patchedFileByte, cur + 28)
            val byte = patchData[i].toInt()
            val fileHeaderLength = (30 + fileNameLength + extraLength).toInt()
            // solve not zip file
            if (byte == 0) {
                val fileEntryLength = (fileHeaderLength + compressLength).toInt()
                outputStream.write(patchedFileByte, cur, fileEntryLength)
                cur += fileEntryLength
                continue
            }
            // write file header first
            outputStream.write(patchedFileByte, cur, fileHeaderLength)
            cur += fileHeaderLength
            val params = decodeDeflateParams(patchData[i])
            deflateData(patchedFileByte, cur, unCompressLength.toInt(), params, outputStream)
            cur += unCompressLength.toInt()
        }
        outputStream.write(patchedFileByte, cur, patchedFileByte.size - cur)
    }

    private fun deflateData(
        patchedFileByte: ByteArray,
        startPos: Int,
        len: Int,
        deflateParams: ZipFileAnalyzer.ZipDeflateParams,
        outputStream: OutputStream
    ) {
        val deflater = Deflater(deflateParams.level, deflateParams.nowrap)
        deflater.setStrategy(deflateParams.strategy)
        deflater.reset()
        val compressStream = ByteArrayOutputStream()
        val deflateStream = DeflaterOutputStream(compressStream, deflater)
        deflateStream.write(patchedFileByte, startPos, len)
        deflateStream.close()
        val toByteArray = compressStream.toByteArray()
        outputStream.write(toByteArray)
    }


    private fun read32BitUnsigned(data: ByteArray, cur: Int): Long {
        var value: Long = data[cur].toLong() and 0xFF
        value = value or ((data[cur + 1].toLong() and 0xFF) shl 8)
        value = value or ((data[cur + 2].toLong() and 0xFF) shl 16)
        value = value or ((data[cur + 3].toLong() and 0xFF) shl 24)
        return value
    }

    private fun read16BitUnsigned(data: ByteArray, cur: Int): Long {
        var value: Long = (data[cur].toLong() and 0xFF)
        value = value or ((data[cur + 1].toLong() and 0xFF) shl 8)
        return value
    }


    private fun readInt(): Int {
        val res = patchData[3 + pos].toInt() and 0xFF or (
                (patchData[2 + pos].toInt() and 0xFF) shl 8) or (
                (patchData[1 + pos].toInt() and 0xFF) shl 16) or (
                (patchData[0 + pos].toInt() and 0xFF) shl 24)
        pos += 4
        return res
    }

    fun decodeDeflateParams(byte: Byte): ZipFileAnalyzer.ZipDeflateParams {
        val res = ZipFileAnalyzer.ZipDeflateParams(-1, false, 0)
        var number = byte.toInt()
        val nowrapFlag: Int = number / 30
        res.nowrap = nowrapFlag == 1
        number = number % 30
        res.strategy = number / 10
        res.level = number % 10
        return res
    }
}