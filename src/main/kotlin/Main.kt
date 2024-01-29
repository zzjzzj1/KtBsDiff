package com.xiaomi

import com.xiaomi.com.xiaomi.kotlinBsDiff.core.*
import com.xiaomi.com.xiaomi.kotlinBsDiff.utils.FileUtils
import java.io.File

fun main() {
    val oldFileName = "oldFile"
    val newFileName = "newFile"
    KtApkDiff.diff(oldFileName, newFileName, "patch")
    val startTime = System.currentTimeMillis()
    val bsPatch = BsPatch("oldFile", "patch")
    bsPatch.patch("new")
    println(System.currentTimeMillis() - startTime)
}