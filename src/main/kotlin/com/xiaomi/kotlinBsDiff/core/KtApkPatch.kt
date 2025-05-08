package com.xiaomi.com.xiaomi.kotlinBsDiff.core

import com.github.luben.zstd.ZstdInputStream
import com.xiaomi.com.xiaomi.kotlinBsDiff.utils.FileUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

object KtApkPatch {

    fun patch(oldFileName: String, patchFileName: String, newFileName: String) {
        val file = FileUtils.createFile(newFileName)
        val fileOutputStream = BufferedOutputStream(file.outputStream(), 32 * 1024)
        val oldFile = File(oldFileName)
        val patchFile = File(patchFileName)
        if (!oldFile.exists() || !patchFile.exists()) {
            throw Exception("cant find file")
        }
        val patchFileStream = BufferedInputStream(ZstdInputStream(FileInputStream(patchFile)), 32 * 1024)
        var startTime = System.currentTimeMillis()
        val tmpOldFile = solveOldFile(oldFile, patchFileStream)
        println("解压耗时: ${System.currentTimeMillis() - startTime}ms")
        val oldFileStream = RandomAccessFile(tmpOldFile, "r")
        val patcher = BsPatch(oldFileStream, patchFileStream)
        startTime = System.currentTimeMillis()
        val outputStream =
            BufferedOutputStream(createDeflateOutputStream(patchFileStream, fileOutputStream), 32 * 1024)
        patcher.patch(outputStream)
        outputStream.close()
        println("bsPatch耗时: ${System.currentTimeMillis() - startTime}ms")
        tmpOldFile.delete()
    }

    private fun createDeflateOutputStream(patchFileStream: InputStream, outputStream: OutputStream): DeflateOutputStream {
        val startPos = FileUtils.read32BitUnsigned(patchFileStream)
        val entrySize = FileUtils.read32BitUnsigned(patchFileStream)
        val paramList = ArrayList<ZipFileAnalyzer.ZipDeflateParams?>()
        for (i in 0 until entrySize) {
            val deflateParams = patchFileStream.read()
            paramList += if (deflateParams == 0) {
                null
            } else {
                decodeDeflateParams(deflateParams.toByte())
            }
        }
        return DeflateOutputStream(outputStream, paramList, startPos)
    }

    private fun solveOldFile(oldFile: File, patchFileStream: InputStream): File {
        val zipFileAnalyzer = ZipFileAnalyzer(oldFile)
        val file = FileUtils.createFile("tmpOld")
        val outputStream = file.outputStream()
        val fileEntries = zipFileAnalyzer.listAllEntries(false)
        val fileStream = zipFileAnalyzer.getFileStream()
        fileStream.seek(0)
        for (entry in fileEntries) {
            val cur = fileStream.filePointer
            val read = patchFileStream.read()
            if (read != 0) {
                FileUtils.copyFileByStream(fileStream, outputStream, cur, entry.startPos - cur)
                FileUtils.inflateFilaByStream(
                    fileStream,
                    outputStream,
                    entry.startPos,
                    entry.compressFileLength,
                    read == 1
                )
                continue
            }
            FileUtils.copyFileByStream(fileStream, outputStream, cur, entry.startPos - cur + entry.compressFileLength)
        }
        FileUtils.copyFileByStream(fileStream, outputStream, fileStream.filePointer, fileStream.length() - fileStream.filePointer)
        outputStream.close()
        return file
    }

    private fun decodeDeflateParams(byte: Byte): ZipFileAnalyzer.ZipDeflateParams {
        val res = ZipFileAnalyzer.ZipDeflateParams(-1, false, 0)
        var number = byte.toInt()
        val nowrapFlag: Int = number / 30
        res.nowrap = nowrapFlag == 1
        number %= 30
        res.strategy = number / 10
        res.level = number % 10
        return res
    }

}