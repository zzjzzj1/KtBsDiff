package com.xiaomi.com.xiaomi.kotlinBsDiff.core

import com.xiaomi.com.xiaomi.kotlinBsDiff.utils.FileUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

object KtApkPatch {

    fun patch(oldFileName: String, patchFileName: String, newFileName: String) {
        val file = FileUtils.createFile(newFileName)
        val tmp = FileUtils.createFile("tmpNew")
        val tmpOutputStream = tmp.outputStream()
        val outputStream = file.outputStream()
        val oldFile = File(oldFileName)
        val patchFile = File(patchFileName)
        if (!oldFile.exists() || !patchFile.exists()) {
            throw Exception("cant find file")
        }
        val patchFileStream = BufferedInputStream(FileInputStream(patchFile), 32 * 1024)
        val tmpOldFile = solveOldFile(oldFile, patchFileStream)
        val oldFileStream = RandomAccessFile(tmpOldFile, "r")
        val patcher = BsPatch(oldFileStream, patchFileStream)
        var startTime = System.currentTimeMillis()
        patcher.patch(tmpOutputStream)
        println("bsPatch耗时: ${System.currentTimeMillis() - startTime}ms")
        tmpOutputStream.close()
        startTime = System.currentTimeMillis()
        deflateFile(patchFileStream, FileInputStream(tmp), outputStream)
        println("重新压缩耗时: ${System.currentTimeMillis() - startTime}ms")
        outputStream.close()
        tmpOldFile.delete()
        tmp.delete()
    }

    private fun deflateFile(patchFileStream: InputStream, tmpFileStream: InputStream, outputStream: OutputStream) {
        var deflateParams: Int
        val firstEntryStartPos = FileUtils.read32BitUnsigned(patchFileStream)
        FileUtils.copyFileByStream(
            tmpFileStream,
            outputStream,
            firstEntryStartPos
        )
        while (true) {
            deflateParams = patchFileStream.read()
            if (deflateParams == -1) {
                break
            }
            val fileEntry = parseFileEntryByPos(tmpFileStream, outputStream)
            if (deflateParams == 0) {
                FileUtils.copyFileByStream(
                    tmpFileStream,
                    outputStream,
                    fileEntry.compressSize
                )
                continue
            }
            FileUtils.deflateFileByStream(
                tmpFileStream,
                outputStream,
                fileEntry.unCompressSize,
                decodeDeflateParams(deflateParams.toByte())
            )
        }

        FileUtils.copyFileByStream(
            tmpFileStream,
            outputStream,
        )

    }

    data class FileEntry(val compressSize: Long, val unCompressSize: Long, val dataStartPos: Long)

    private fun parseFileEntryByPos(fileStream: InputStream, outputStream: OutputStream): FileEntry {
        val headerBuffer = ByteArray(30)
        val read = fileStream.read(headerBuffer, 0, headerBuffer.size)
        if (read != 30) {
            throw Exception("can find the file entry header")
        }
        outputStream.write(headerBuffer, 0, headerBuffer.size)
        val header = FileUtils.read32BitUnsigned(headerBuffer, 0)
        if (header != ZipFileAnalyzer.FILE_ENTRY_HEADER_SIGNATURE.toLong()) {
            throw Exception("can find the file entry header")
        }
        val compressLength = FileUtils.read32BitUnsigned(headerBuffer, 18)
        val unCompressLength = FileUtils.read32BitUnsigned(headerBuffer, 22)
        val fileNameLength = FileUtils.read16BitUnsigned(headerBuffer, 26)
        val extraLength = FileUtils.read16BitUnsigned(headerBuffer, 28)
        // copy header
        FileUtils.copyFileByStream(fileStream, outputStream, fileNameLength + extraLength)
        return FileEntry(compressLength, unCompressLength, 30 + fileNameLength + extraLength)
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