package com.xiaomi.com.xiaomi.kotlinBsDiff.core

import java.io.RandomAccessFile
import java.util.zip.Inflater

class ZipFileSolver(val zipFileAnalyzer: ZipFileAnalyzer) {

    fun unCompressFile(): ByteArray {
        var unCompressSize = 0L
        val zipEntryList = zipFileAnalyzer.listAllEntries()
        for (zipFileEntry in zipEntryList) {
            unCompressSize += zipFileEntry.unCompressFileLength - zipFileEntry.compressFileLength
        }
        val unCompressedData = ByteArray((zipFileAnalyzer.getFileLength() + unCompressSize).toInt())
        val fileRandomStream = zipFileAnalyzer.getFileStream()
        fileRandomStream.seek(0)
        var unCompressedFilePos = 0
        var oldFilePos = 0
        for (zipEntry in zipEntryList) {
            // this is not zip file, keep it
            if (zipEntry.deflateParams == null) {
                val numRead = fileRandomStream.read(
                    unCompressedData,
                    unCompressedFilePos,
                    (zipEntry.startPos - oldFilePos + zipEntry.compressFileLength).toInt()
                )
                unCompressedFilePos += numRead
                oldFilePos = (zipEntry.startPos + zipEntry.compressFileLength).toInt()
                continue
            }
            // pending head data
            val numRead = fileRandomStream.read(
                unCompressedData, unCompressedFilePos,
                (zipEntry.startPos - oldFilePos).toInt()
            )
            unCompressedFilePos += numRead
            unCompressedFilePos += unCompressFileEntry(
                fileRandomStream,
                zipEntry,
                unCompressedData,
                unCompressedFilePos
            )
            // start read next file entry
            oldFilePos = (zipEntry.startPos + zipEntry.compressFileLength).toInt()
        }
        fileRandomStream.read(unCompressedData, unCompressedFilePos, unCompressedData.size - unCompressedFilePos)
        return unCompressedData
    }

    private fun unCompressFileEntry(
        fileStream: RandomAccessFile,
        zipEntry: ZipFileAnalyzer.ZipFileEntry,
        unCompressedData: ByteArray,
        currentPos: Int
    ): Int {
        val waitUnCompressData = ByteArray(zipEntry.compressFileLength.toInt())
        fileStream.read(waitUnCompressData)
        val inflater = Inflater(zipEntry.deflateParams!!.nowrap)
        inflater.reset()
        inflater.setInput(waitUnCompressData)
        val write = inflater.inflate(unCompressedData, currentPos, zipEntry.unCompressFileLength.toInt())
        if (write != zipEntry.unCompressFileLength.toInt() || !inflater.finished()) {
            println("error when un compress file")
        }
        return write
    }

}