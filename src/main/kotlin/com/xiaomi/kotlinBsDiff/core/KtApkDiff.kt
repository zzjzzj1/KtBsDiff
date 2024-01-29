package com.xiaomi.com.xiaomi.kotlinBsDiff.core

import com.xiaomi.com.xiaomi.kotlinBsDiff.utils.FileUtils
import java.io.File
import java.io.FileOutputStream

object KtApkDiff {

    fun diff(oldFileName: String, newFileName: String, patchFileName: String) {
        val oldFile = File(oldFileName)
        val newFile = File(newFileName)
        if (!oldFile.exists()) {
            throw Exception("cant find file named $oldFileName")
        }
        if (!newFile.exists()) {
            throw Exception("cant find file named $newFileName")
        }
        val file = FileUtils.createFile(patchFileName)
        val oldZipFileSolver = ZipFileSolver(ZipFileAnalyzer(oldFile))
        val zipFileAnalyzer = ZipFileAnalyzer(newFile)
        val newZipFileSolver = ZipFileSolver(zipFileAnalyzer)
        val outputStream = FileOutputStream(file)
        val bsDiff = BsDiff(oldZipFileSolver.unCompressFile(), newZipFileSolver.unCompressFile())
        bsDiff.diff(outputStream)
        val listAllEntries = zipFileAnalyzer.listAllEntries()
        val buffer = ByteArray(listAllEntries.size)
        for (i in listAllEntries.indices) {
            buffer[i] = encodeDeflateParams(listAllEntries[i].deflateParams)
        }
        if (!listAllEntries.isEmpty()) {
            if (listAllEntries[0].compressFileLength != 0L) {
                outputStream.write(0)
            }
        }
        outputStream.write(buffer)
        outputStream.close()
    }

    fun encodeDeflateParams(zipDeflateParams: ZipFileAnalyzer.ZipDeflateParams?): Byte {
        zipDeflateParams?.let {
            return (zipDeflateParams.level + zipDeflateParams.strategy * 10 + (if (zipDeflateParams.nowrap) 1 else 0) * 30).toByte()
        }
        return 0
    }

}