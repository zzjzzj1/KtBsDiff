package com.xiaomi

import com.xiaomi.com.xiaomi.kotlinBsDiff.core.*
import java.io.File

fun main() {
    val oldFileName = "com.tencent.mm_8.0.45.apk"
    val newFileName = "com.tencent.mm_8.0.46.apk"
//    val oldFileName = "oldFile"
//    val newFileName = "newFile"
    KtApkDiff.diff(oldFileName, newFileName, "patch_2")
    val currentTimeMillis = System.currentTimeMillis()
//    KtApkPatch.patch(oldFileName, "patch_2", "new")
    println(System.currentTimeMillis() - currentTimeMillis)
//    val zipFileAnalyzer = ZipFileAnalyzer(File(newFileName))
//    val zipFileSolver = ZipFileSolver(zipFileAnalyzer)
//    val unCompressFile = zipFileSolver.unCompressFile()
//    println(unCompressFile.size)
}