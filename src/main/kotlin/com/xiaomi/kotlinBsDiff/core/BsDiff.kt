package com.xiaomi.com.xiaomi.kotlinBsDiff.core

import com.xiaomi.com.xiaomi.kotlinBsDiff.exception.FileNotFoundException
import com.xiaomi.com.xiaomi.kotlinBsDiff.utils.FileUtils
import java.io.File
import java.io.FileOutputStream
import kotlin.collections.ArrayList

class BsDiff(oldFileName: String, newFileName: String) {
    private var oldFile: File = File(oldFileName)
    private var newFile: File = File(newFileName)
    private val newData = FileUtils.readDataFromFile(newFile)
    private val oldData = FileUtils.readDataFromFile(oldFile)
    private var diffRecord = ArrayList<Byte>()

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


    fun diff(patchFileName: String) {
        val patchFile = FileUtils.createFile(patchFileName)
        diffRecord = ArrayList()
        writeData(newData.size)
        // 根据旧文件创建后缀数组
        val suffixArray = SuffixArray(oldData)
        val outputStream = FileOutputStream(patchFile)
        bsDiffInner(suffixArray)
        val res = ByteArray(diffRecord.size)
        for (i in res.indices) {
            res[i] = diffRecord[i]
        }
        outputStream.write(res)
        outputStream.close()
    }

    private fun judgeHaveMatchExtension(oldScore: Int, len: Int): Boolean {
        // 神秘启发式条件!!!!
        if (len == oldScore && len != 0) {
            return true
        }
        // 超参数,这个会影响算法的匹配
        return len > oldScore + 8
    }

    private fun compareDataByOffset(cur: Int): Boolean {
        return cur + lastOffset < oldData.size && oldData[cur + lastOffset] == newData[cur]
    }


    private fun bsDiffInner(
        suffixArray: SuffixArray
    ) {
        // 核心原理就是使用启发式的搜索算法不断寻找大概率重合的字段
        // HDiff算法对于此处进行了优化，使得包体减小百分之10
        // todo 查看HDiff算法此处搜索逻辑
        while (scan < newData.size) {
            var oldScore = 0
            scan += len
            var cur = scan
            while (scan < newData.size) {
                // 从当前获取最大匹配项
                val search = suffixArray.search(scan, oldData, newData)
                len = search.len
                pos = search.pos
                while (cur < scan + len) {
                    if (compareDataByOffset(cur)) {
                        oldScore++
                    }
                    cur++
                }
                if (judgeHaveMatchExtension(oldScore, len)) {
                    break
                }
                if (compareDataByOffset(scan)) {
                    oldScore--
                }
                scan++
            }
            if (len != oldScore || scan == newData.size) {
                solveMatchExtension()
            }
        }
    }

    // 向前搜索匹配项，它这里有一个启发式的判断条件
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
            // 启发式判断
            if (currentNumber * 2 - i > lastNumber * 2 - forwardExtensionLen) {
                lastNumber = currentNumber
                forwardExtensionLen = i
            }
        }
        return forwardExtensionLen
    }

    // 向后搜索匹配项，基本原理通上
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

    // 可能会有交叉部分
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
            // 判断属于前半段还是后半段
            if (currentNumber > lastNumber) {
                lastNumber = currentNumber
                len = i + 1
            }
        }
        res[0] = res[0] + len - overlap
        res[1] = res[1] - len
        return res
    }

    private fun recordResult(
        forwardExtensionLength: Int,
        backwardExtensionLength: Int,
    ) {
        // 记录diff文件
        // todo 保存diff文件，这个地方压缩算法可以优化，从而降低包体大小
        writeData(forwardExtensionLength)
        writeData(lastPos)
        val tempArray = ByteArray(forwardExtensionLength)
        val posRecord = ArrayList<Int>()
        for (i in 0..<forwardExtensionLength) {
            tempArray[i] = (newData[lastScan + i] - oldData[lastPos + i]).toByte()
        }
        for (i in 0..<forwardExtensionLength) {
            if (tempArray[i].toInt() == 0) {
                continue
            }
            if (i == 0 || tempArray[i - 1].toInt() == 0) {
                posRecord.add(i)
            }
            if (i + 1 == forwardExtensionLength || tempArray[i + 1].toInt() == 0) {
                posRecord.add(i + 1)
            }
        }
        writeData(posRecord.size / 2)
        var cur = 0
        while (cur < posRecord.size) {
            val startPos = posRecord[cur++]
            val endPos = posRecord[cur++]
            writeData(startPos)
            writeData(endPos - startPos)
            for (i in startPos..<endPos) {
                diffRecord.add(tempArray[i])
            }
        }
        // 记录extra区段
        val extraLen = (scan - backwardExtensionLength) - (lastScan + forwardExtensionLength)
        writeData(extraLen)
        for (i in 0..<extraLen) {
            diffRecord.add(newData[lastScan + forwardExtensionLength + i])
        }
    }


    private fun solveMatchExtension() {
        val matchLengthArray = solveBackwardAndForwardOverlap(getForwardExtensionLen(), getBackwardExtensionLen())
        val forwardMatchLen = matchLengthArray[0]
        val backwardMatchLen = matchLengthArray[1]
        // 记录变更
        recordResult(forwardMatchLen, backwardMatchLen)
        // 修改位置
        lastScan = scan - backwardMatchLen
        lastPos = pos - backwardMatchLen
        // 上面那个地方加上这个其实就相当于后缀匹配段后一个字符
        lastOffset = pos - scan
    }

    private fun writeData(a: Int) {
        diffRecord.addAll(intToByte(a))
    }

    private fun intToByte(a: Int): List<Byte> {
        return listOf(
            ((a shr 24) and 0xFF).toByte(),
            ((a shr 16) and 0xFF).toByte(),
            ((a shr 8) and 0xFF).toByte(),
            (a and 0xFF).toByte()
        )
    }

}