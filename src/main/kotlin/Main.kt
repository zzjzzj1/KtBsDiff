package com.xiaomi

import com.xiaomi.com.xiaomi.kotlinBsDiff.core.*
import com.xiaomi.com.xiaomi.kotlinBsDiff.utils.FileUtils
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.Random

fun main() {
//    val oldFileName = "com.tencent.mm_8.0.45.apk"
//    val newFileName = "com.tencent.mm_8.0.46.apk"
    val oldFileName = "oldFile"
    val newFileName = "newFile"
    KtApkDiff.diff(oldFileName, newFileName, "patch")
    val currentTimeMillis = System.currentTimeMillis()
    KtApkPatch.patch(oldFileName, "patch", "new")
    println(System.currentTimeMillis() - currentTimeMillis)
}