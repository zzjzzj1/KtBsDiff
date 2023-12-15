package com.xiaomi.com.xiaomi.kotlinBsDiff.core

import com.xiaomi.com.xiaomi.kotlinBsDiff.exception.FileNotFoundException
import com.xiaomi.com.xiaomi.kotlinBsDiff.utils.FileUtils
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class BsDiff(oldFileName: String, newFileName: String) {
    private var oldFile: File = File(oldFileName)
    private var newFile: File = File(newFileName)
    private val newData = FileUtils.readDataFromFile(newFile)
    private val oldData = FileUtils.readDataFromFile(oldFile)
    private var diffData = IntArray(newData.size)
    private val extraData = ByteArray(newData.size)
    private var diffLen = 0
    private var extraLen = 0

    // newData right
    private var scan = 0

    // newData left
    private var lastScan = 0

    // oldData right
    private var pos = 0

    // newData left
    private var lastPos = 0

    // 当前匹配的长度
    private var len = 0

    //
    private var lastOffset = 0

    init {
        if (!oldFile.exists()) {
            throw FileNotFoundException("can not find old file")
        }
        if (!newFile.exists()) {
            throw FileNotFoundException("can not find new file")
        }

    }


    fun bsDiff(patchFileName: String) {
//        val patchFile = FileUtils.createFile(patchFileName)
        // 根据旧文件创建后缀数组
        val suffixArray = SuffixArray(oldData)
//        val outputStream = FileOutputStream(patchFile)
        bsDiffInner(null, suffixArray)
    }

    private fun bsDiffInner(
        outputStream: OutputStream?,
        suffixArray: SuffixArray
    ) {
        while (scan < newData.size) {
            var oldScore = 0
            scan += len
            val recordScan = scan
            while (scan < newData.size) {
                // 从当前获取最大匹配项
                val search = suffixArray.search(scan, oldData, newData)
                len = search.len
                pos = search.pos
                for (cur in recordScan..<scan + len) {
                    if (cur + lastOffset < oldData.size && oldData[cur + lastOffset] == newData[cur]) {
                        oldScore++
                    }
                }
                if (len == oldScore && len != 0) {
                    break
                }
                if (len > oldScore + 8) {
                    break
                }
                if (scan + lastOffset < oldData.size && oldData[scan + lastOffset] == newData[scan]) {
                    oldScore--
                }
                scan++
            }
            if (len != oldScore || scan == newData.size) {
                println("$lastPos $pos $lastScan $scan")
                solveMatchExtension()
            }
            scan++
        }
    }

    private fun getForwardExtensionLen(): Int {
        var currentNumber = 0
        var lastNumber = 0
        var forwardExtensionLen = 0
        var i = 0
        while (lastScan + i < scan && lastPos + i < oldData.size) {
            if (oldData[lastPos + i] == newData[lastScan + i]) {
                currentNumber++
            }
            i++
            if (currentNumber * 2 - i > lastNumber * 2 - forwardExtensionLen) {
                lastNumber = currentNumber
                forwardExtensionLen = i
            }
        }
        return forwardExtensionLen
    }

    private fun getBackwardExtensionLen(): Int {
        if (scan >= newData.size) {
            return 0
        }
        var currentNumber = 0
        var lastNumber = 0
        var backwardExtensionLength = 0
        for (i in 1..(scan - lastScan).coerceAtMost(pos)) {
            if (newData[scan - i] == oldData[pos - i]) {
                currentNumber++
            }
            if (2 * currentNumber - i > lastNumber * 2 - backwardExtensionLength) {
                lastNumber = currentNumber
                backwardExtensionLength = i
            }
        }
        return backwardExtensionLength
    }

    private fun solveBackwardAndForwardOverlap(
        forwardExtensionLength: Int,
        backwardExtensionLength: Int
    ): IntArray {
        val res = IntArray(2)
        res[0] = forwardExtensionLength
        res[1] = backwardExtensionLength
        if (lastScan + forwardExtensionLength < scan - backwardExtensionLength) {
            return res
        }
        val overlap = (lastScan + forwardExtensionLength) - (scan - backwardExtensionLength)
        var currentNumber = 0
        var lastNumber = 0
        var len = 0
        for (i in 0..<overlap) {
            val temp = forwardExtensionLength - overlap + i
            if (newData[lastScan + temp] == oldData[lastPos + temp]) {
                currentNumber++
            }
            if (newData[scan - backwardExtensionLength + i] == oldData[pos - backwardExtensionLength + i]) {
                currentNumber--
            }
            if (currentNumber > lastNumber) {
                lastNumber = currentNumber
                len = i + 1
            }
        }
        res[0] = res[0] + len - overlap
        res[1] = res[1] - len
        return res
    }

    private fun recordResult(forwardExtensionLength: Int, backwardExtensionLength: Int) {
        for (i in 0..<forwardExtensionLength) {
            diffData[diffLen + i] = newData[lastScan + i] - oldData[lastPos + i]
        }
        diffLen += forwardExtensionLength
        for (i in 0..<(scan - backwardExtensionLength) - (lastScan + forwardExtensionLength)) {
            extraData[extraLen + i] = newData[lastScan + forwardExtensionLength + i]
        }
        extraLen += (scan - backwardExtensionLength) - (lastScan + forwardExtensionLength)
    }


    private fun solveMatchExtension() {
        val matchLengthArray = solveBackwardAndForwardOverlap(getForwardExtensionLen(), getBackwardExtensionLen())
        val forwardMatchLen = matchLengthArray[0]
        val backwardMatchLen = matchLengthArray[1]
        recordResult(forwardMatchLen, backwardMatchLen)
        lastScan = scan - backwardMatchLen
        lastPos = pos - backwardMatchLen
        lastOffset = pos - scan
    }

    private fun toPositive(byte: Byte): Int {
        return byte + 128
    }

}