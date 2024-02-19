package com.xiaomi

import com.xiaomi.com.xiaomi.kotlinBsDiff.core.*

fun main() {
    val oldFileName = "oldFile"
    val newFileName = "newFile"
    var startTime = System.currentTimeMillis()
    KtApkDiff.diff(oldFileName, newFileName, "patch")
    println("diff总耗时: ${System.currentTimeMillis() - startTime}ms")
    startTime = System.currentTimeMillis()
    KtApkPatch.patch(oldFileName, "patch", "new")
    println("patch总耗时: ${System.currentTimeMillis() - startTime}ms")
}