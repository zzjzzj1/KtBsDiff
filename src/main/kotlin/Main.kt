package com.xiaomi

import com.xiaomi.com.xiaomi.kotlinBsDiff.core.BsDiff
import com.xiaomi.com.xiaomi.kotlinBsDiff.core.BsPatch

fun main() {
    val bsDiff = BsDiff("oldFile", "newFile")
    bsDiff.diff("patch_file")
    val bsPatch = BsPatch("oldFile", "patch_file")
    bsPatch.patch("newFileByPatch")
}