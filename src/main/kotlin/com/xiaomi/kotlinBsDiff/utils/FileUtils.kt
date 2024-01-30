package com.xiaomi.com.xiaomi.kotlinBsDiff.utils

import com.xiaomi.com.xiaomi.kotlinBsDiff.core.KtApkPatch
import com.xiaomi.com.xiaomi.kotlinBsDiff.core.ZipFileAnalyzer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.InflaterOutputStream

object FileUtils {

    fun readDataFromFile(file: File): ByteArray {
        val inputStream = FileInputStream(file)
        val size = inputStream.available()
        val res = ByteArray(size)
        inputStream.read(res, 0, size)
        inputStream.close()
        return res
    }

    fun createFile(patchFileName: String): File {
        val file = File(patchFileName)
        if (file.exists()) {
            file.delete()
            return File(patchFileName)
        }
        return file
    }

    private fun readByteOrDie(file: RandomAccessFile): Int {
        val result = file.read()
        if (result == -1) {
            throw IOException("EOF")
        }
        return result
    }

    private fun readByteOrDie(file: InputStream): Int {
        val result = file.read()
        if (result == -1) {
            throw IOException("EOF")
        }
        return result
    }

    fun read32BitUnsigned(file: RandomAccessFile): Long {
        var value: Long = readByteOrDie(file).toLong()
        value = value or (readByteOrDie(file).toLong() shl 8)
        value = value or (readByteOrDie(file).toLong() shl 16)
        value = value or (readByteOrDie(file).toLong() shl 24)
        return value
    }

    fun read32BitUnsigned(file: InputStream): Long {
        var value: Long = readByteOrDie(file).toLong()
        value = value or (readByteOrDie(file).toLong() shl 8)
        value = value or (readByteOrDie(file).toLong() shl 16)
        value = value or (readByteOrDie(file).toLong() shl 24)
        return value
    }

    fun read16BitUnsigned(file: RandomAccessFile): Long {
        var value: Long = readByteOrDie(file).toLong()
        value = value or (readByteOrDie(file).toLong() shl 8)
        return value
    }

    fun streamFile(
        file: RandomAccessFile,
        startPos: Long,
        length: Long,
        streamSolver: StreamSolver,
        bufferSize: Int = 32
    ) {
        val buffer = ByteArray(bufferSize * 1024)
        file.seek(startPos)
        var curRead = 0
        while (curRead < length) {
            val readSize = if ((length - curRead) < buffer.size) length - curRead else buffer.size
            curRead += file.read(buffer, 0, readSize.toInt())
            streamSolver.solveBuffer(buffer, readSize.toInt())
        }
    }

    fun copyFileByStream(file: RandomAccessFile, outputStream: OutputStream, startPos: Long, length: Long) {
        streamFile(file, startPos, length, object: StreamSolver {
            override fun solveBuffer(buffer: ByteArray, length: Int) {
                outputStream.write(buffer, 0, length)
            }
        })
    }

    fun inflateFilaByStream(file: RandomAccessFile, outputStream: OutputStream, startPos: Long, length: Long, nowrap: Boolean) {
        val inflater = Inflater(nowrap)
        val inflaterOutputStream = InflaterOutputStream(outputStream, inflater)
        streamFile(
            file,
            startPos,
            length,
            object : StreamSolver {
                override fun solveBuffer(buffer: ByteArray, length: Int) {
                    inflaterOutputStream.write(buffer, 0, length)
                }
            })
        inflaterOutputStream.finish()
    }

    fun deflateFileByStream(file: RandomAccessFile, outputStream: OutputStream, startPos: Long, length: Long, deflateParams: ZipFileAnalyzer.ZipDeflateParams) {
        val deflater = Deflater(deflateParams.level, deflateParams.nowrap)
        deflater.setStrategy(deflateParams.strategy)
        val deflaterOutputStream = DeflaterOutputStream(outputStream, deflater)
        streamFile(
            file,
            startPos,
            length,
            object : StreamSolver {
                override fun solveBuffer(buffer: ByteArray, length: Int) {
                    deflaterOutputStream.write(buffer, 0, length)
                }
            })
        deflaterOutputStream.finish()
    }

    interface StreamSolver {

        fun solveBuffer(buffer: ByteArray, length: Int)

    }

}
