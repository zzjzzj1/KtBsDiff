package com.xiaomi

import com.xiaomi.com.xiaomi.kotlinBsDiff.core.*
import java.io.File

fun main() {
    val oldFileName = "oldFile"
    val newFileName = "newFile"
    var currentTimeMillis = System.currentTimeMillis()
    KtApkDiff.diff(oldFileName, newFileName, "patch")
    println(System.currentTimeMillis() - currentTimeMillis)
    currentTimeMillis = System.currentTimeMillis()
    KtApkPatch.patch(oldFileName, "patch", "new")
    println(System.currentTimeMillis() - currentTimeMillis)
}