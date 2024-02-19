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
        val oldFileAnalyzer = ZipFileAnalyzer(oldFile)
        val oldZipFileSolver = ZipFileSolver(oldFileAnalyzer)
        val newFileAnalyzer = ZipFileAnalyzer(newFile)
        val newZipFileSolver = ZipFileSolver(newFileAnalyzer)
        val outputStream = FileOutputStream(file)
        val startTime = System.currentTimeMillis()
        val oldData = oldZipFileSolver.unCompressFile()
        val bsDiff = BsDiff(oldData, newZipFileSolver.unCompressFile())
        println("生成解压计划耗时: ${System.currentTimeMillis() - startTime}ms")
        // 解压计划
        for (entry in oldFileAnalyzer.listAllEntries()) {
            outputStream.write(encodeNowrapParam(entry).toInt())
        }
        bsDiff.diff(outputStream)
        val listAllEntries = newFileAnalyzer.listAllEntries()
        val buffer = ByteArray(listAllEntries.size)
        for (i in listAllEntries.indices) {
            buffer[i] = encodeDeflateParams(listAllEntries[i].deflateParams)
        }
        // 注明第一个文件块开始的位置
        outputStream.write(intToByte(newFileAnalyzer.firstEntryStartPos.toInt()))
        outputStream.write(buffer)
        outputStream.close()
    }

    private fun encodeDeflateParams(zipDeflateParams: ZipFileAnalyzer.ZipDeflateParams?): Byte {
        zipDeflateParams?.let {
            return (zipDeflateParams.level + zipDeflateParams.strategy * 10 + (if (zipDeflateParams.nowrap) 1 else 0) * 30).toByte()
        }
        return 0
    }

    private fun encodeNowrapParam(zipEntry: ZipFileAnalyzer.ZipFileEntry): Byte {
        if (zipEntry.deflateParams == null) {
            return 0
        }
        return if (zipEntry.deflateParams!!.nowrap) 1 else 2
    }

    private fun intToByte(a: Int): ByteArray {
        return byteArrayOf(
            ((a shr 24)).toByte(),
            ((a shr 16)).toByte(),
            ((a shr 8)).toByte(),
            (a).toByte()
        ).reversedArray()
    }

}