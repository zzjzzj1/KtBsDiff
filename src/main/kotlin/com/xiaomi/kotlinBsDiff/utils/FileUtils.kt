package com.xiaomi.com.xiaomi.kotlinBsDiff.utils

import java.io.File
import java.io.FileInputStream

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

}
