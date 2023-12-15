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
        var i = 0
        var file = File(patchFileName)
        while (file.exists()) {
            file = File("${patchFileName}_${i++}")
        }
        return file
    }

}
