package com.xiaomi

import com.xiaomi.com.xiaomi.kotlinBsDiff.core.BsDiff
import com.xiaomi.com.xiaomi.kotlinBsDiff.core.BsPatch

fun main() {
    val bsDiff = BsDiff("oldFile", "newFile")
    var startTime = System.currentTimeMillis()
    bsDiff.diff("patch_file")
    println("diff耗时${System.currentTimeMillis() - startTime}")
    startTime = System.currentTimeMillis()
    val bsPatch = BsPatch("oldFile", "patch_file")
    bsPatch.patch("newFileByPatch")
    println("patch耗时${System.currentTimeMillis() - startTime}")
}