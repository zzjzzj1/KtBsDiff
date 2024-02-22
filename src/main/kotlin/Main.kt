package com.xiaomi

import com.xiaomi.com.xiaomi.kotlinBsDiff.core.*

fun main() {
    // xiaomi 应用商店4.65
    val oldFileName = "oldFile"
    // xiaomi 应用商店4.65
    val newFileName = "newFile"
    var startTime = System.currentTimeMillis()
    // 生成大约2.5m 的补丁包（生成的补丁包还未使用任何压缩算法，用户可以自行进行压缩)
    KtApkDiff.diff(oldFileName, newFileName, "patch")
    println("diff总耗时: ${System.currentTimeMillis() - startTime}ms")
    startTime = System.currentTimeMillis()
    KtApkPatch.patch(oldFileName, "patch", "new")
    println("patch总耗时: ${System.currentTimeMillis() - startTime}ms")
}