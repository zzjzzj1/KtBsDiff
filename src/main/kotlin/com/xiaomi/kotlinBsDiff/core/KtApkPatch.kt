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
        patcher.patch(tmpOutputStream)
        tmpOutputStream.close()
        deflateFile(patchFileStream, RandomAccessFile(tmp, "r"), outputStream)
        outputStream.close()
        tmpOldFile.delete()
        tmp.delete()
    }

    fun deflateFile(patchFileStream: InputStream, tmpFileStream: RandomAccessFile, outputStream: OutputStream) {
        var deflateParams: Int
        val firstEntryStartPos = FileUtils.read32BitUnsigned(patchFileStream)
        FileUtils.copyFileByStream(
            tmpFileStream,
            outputStream,
            0,
            firstEntryStartPos
        )
        var cur = firstEntryStartPos
        while (true) {
            deflateParams = patchFileStream.read()
            if (deflateParams == -1) {
                break
            }
            val fileEntry = parseFileEntryByPos(tmpFileStream, cur)
            if (deflateParams == 0) {
                FileUtils.copyFileByStream(
                    tmpFileStream,
                    outputStream,
                    cur,
                    fileEntry.dataStartPos + fileEntry.compressSize
                )
                cur += fileEntry.dataStartPos + fileEntry.compressSize
                continue
            }
            FileUtils.copyFileByStream(
                tmpFileStream,
                outputStream,
                cur,
                fileEntry.dataStartPos
            )
            FileUtils.deflateFileByStream(
                tmpFileStream,
                outputStream,
                cur + fileEntry.dataStartPos,
                fileEntry.unCompressSize,
                decodeDeflateParams(deflateParams.toByte())
            )
            cur += fileEntry.dataStartPos + fileEntry.unCompressSize
        }

        FileUtils.copyFileByStream(
            tmpFileStream,
            outputStream,
            cur,
            tmpFileStream.length() - cur
        )

    }

    data class FileEntry(val compressSize: Long, val unCompressSize: Long, val dataStartPos: Long)

    fun parseFileEntryByPos(fileStream: RandomAccessFile, pos: Long): FileEntry {
        fileStream.seek(pos)
        val header = FileUtils.read32BitUnsigned(fileStream)
        if (header != ZipFileAnalyzer.FILE_ENTRY_HEADER_SIGNATURE.toLong()) {
            throw Exception("can find the file entry header")
        }
        fileStream.skipBytes(14)
        val compressLength = FileUtils.read32BitUnsigned(fileStream)
        val unCompressLength = FileUtils.read32BitUnsigned(fileStream)
        val fileNameLength = FileUtils.read16BitUnsigned(fileStream)
        val extraLength = FileUtils.read16BitUnsigned(fileStream)
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
        number = number % 30
        res.strategy = number / 10
        res.level = number % 10
        return res
    }

}