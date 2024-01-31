package com.xiaomi.com.xiaomi.kotlinBsDiff.core

import DivSufSort
import java.util.*

/*
    诱导排序获取后缀数组时间复杂度为On,空间复杂度为On
    todo 后续可以继续优化为时间on，空间o1的算法
 */
class SuffixArray(private val data: ByteArray) {
    // todo 桶排序的时候可以使用一个长度为256的数组代替treemap


    private var suffixArray: IntArray? = null

    private fun getSuffixArray(): IntArray {
        if (suffixArray == null) {
            suffixArray = getSuffixArrayByFile()
        }
        return suffixArray!!
    }

    private fun getSuffixArrayByFile(): IntArray {
        val startTime = System.currentTimeMillis()
        val temp = getSuffixArray(data)
        println("后缀数组耗时${System.currentTimeMillis() - startTime}")
        return temp
    }

    private enum class SuffixType {
        S,
        L
    }

    private class Bucket {
        var startIndex: Int = 0
        var number: Int = 0
        var currentLeft: Int = 0
        var currentRight: Int = 0
    }

    // contains left and right
    private class LMS(var left: Int, var right: Int) {
        var number: Int = 0

        override fun toString(): String {
            return "LMS{" +
                    "left=" + left +
                    ", right=" + right +
                    ", number=" + number +
                    '}'
        }
    }

    private fun getSuffixArray(line: Array<Int?>, buketSize: Int = 256): IntArray {
        val sa = IntArray(line.size + 1)
        sa[0] = line.size
        val bucket = getBucket(line, bucketSize = buketSize)
        // 判断是否可以根据首自己排序
        if (judgeCanSortByBucket(bucket, line, sa)) {
            return sa
        }
        // 获取后缀类型
        val suffixType = getSuffixType(line)
        val lmsList = getLMSList(suffixType)
        val bucketSize = sortLMS(lmsList, sa, bucket, line, suffixType)
        val temp = arrayOfNulls<Int>(lmsList.size)
        for (i in lmsList.indices) {
            temp[i] = lmsList[i].number
        }
        val sa1 = getSuffixArray(temp, bucketSize)
        inducedSort(suffixType, lmsList, sa, sa1, bucket, line)
        return sa
    }

    private fun inducedSort(
        suffixType: Array<SuffixType?>,
        lmsList: List<LMS>,
        sa: IntArray,
        sa1: IntArray,
        bucket: Array<Bucket>,
        line: Array<Int?>
    ) {
        for (i in 1 until sa.size) {
            sa[i] = -1
        }
        for (value in bucket) {
            value.currentRight = value.startIndex + value.number - 1
        }
        for (i in sa1.size - 1 downTo 1) {
            val lms = lmsList[sa1[i]]
            if (lms.left < line.size) {
                sa[bucket[line[lms.left]!!].currentRight--] = lms.left
            }
        }
        inducedSortTemp(suffixType, sa, bucket, line)
    }

    private fun inducedSortTemp(
        suffixType: Array<SuffixType?>,
        sa: IntArray,
        bucket: Array<Bucket>,
        line: Array<Int?>
    ) {
        for (value in bucket) {
            value.currentLeft = value.startIndex
            value.currentRight = value.startIndex + value.number - 1
        }
        // 诱导生成L
        for (i in sa.indices) {
            val suffix = sa[i] - 1
            if (sa[i] > 0 && suffixType[suffix] == SuffixType.L) {
                sa[bucket[line[suffix]!!].currentLeft++] = suffix
            }
        }
        // 诱导生成S
        for (i in sa.indices.reversed()) {
            val suffix = sa[i] - 1
            if (sa[i] > 0 && suffixType[suffix] == SuffixType.S) {
                sa[bucket[line[suffix]!!].currentRight--] = suffix
            }
        }
    }

    private fun getLmsMap(lmsList: List<LMS>, size: Int): Array<LMS?> {
        val record = Array<LMS?>(size) { null }
        for (lms in lmsList) {
            record[lms.left] = lms
        }
        return record
    }

    // 排序lms
    private fun sortLMS(
        lmsList: List<LMS>,
        sa: IntArray,
        bucket: Array<Bucket>,
        line: Array<Int?>,
        suffixType: Array<SuffixType?>
    ): Int {
        val tempSa1 = IntArray(lmsList.size + 1)
        for (i in 1 until lmsList.size + 1) {
            tempSa1[i] = i - 1
        }
        val lmsMap = getLmsMap(lmsList, suffixType.size)
        inducedSort(suffixType, lmsList, sa, tempSa1, bucket, line)
        var number = 0
        var last: LMS? = null
        for (index in sa) {
            lmsMap[index]?.let {
                it.number = number++
                // 相同名称lms修正
                if (judgeSame(last, it, line)) {
                    it.number--
                    number--
                }
                last = it
            }
        }
        return number
    }

    private fun judgeSame(a: LMS?, b: LMS?, line: Array<Int?>): Boolean {
        if (a == null || b == null) {
            return false
        }
        if (a.right - a.left != b.right - b.left) {
            return false
        }
        if (a.right == line.size || b.right == line.size) {
            return false
        }
        var i = 0
        while (a.left + i <= a.right) {
            if (line[a.left + i] != line[b.left + i]) {
                return false
            }
            i++
        }
        return true
    }

    private fun getLMSList(suffixType: Array<SuffixType?>): List<LMS> {
        val data: MutableList<LMS> = ArrayList()
        var left: Int? = null
        for (i in suffixType.indices) {
            if (judgeLMS(i, suffixType)) {
                if (left != null) {
                    val lms = LMS(left, i)
                    data.add(lms)
                }
                left = i
            }
        }
        data.add(LMS(suffixType.size - 1, suffixType.size - 1))
        return data
    }

    private fun judgeLMS(i: Int, suffixType: Array<SuffixType?>): Boolean {
        return i != 0 && suffixType[i] == SuffixType.S && suffixType[i - 1] == SuffixType.L
    }

    private fun judgeCanSortByBucket(bucket: Array<Bucket>, line: Array<Int?>, ans: IntArray): Boolean {
        if (bucket.size == line.size) {
            for (i in line.indices) {
                ans[bucket[line[i]!!].startIndex] = i
            }
            return true
        }
        return false
    }

    private fun getBucket(line: Array<Int?>, bucketSize: Int): Array<Bucket> {
        val bucket = Array(bucketSize) { Bucket() }
        for (item in line) {
            if (item != null) {
                bucket[item].number++
            }
        }
        var index = 1
        for (item in bucket) {
            item.startIndex = index
            index += item.number
        }
        return bucket
    }

    // 动态规划思路获取后缀类型
    private fun getSuffixType(line: Array<Int?>): Array<SuffixType?> {
        val suffixTypeArray = arrayOfNulls<SuffixType>(line.size + 1)
        suffixTypeArray[line.size] = SuffixType.S
        suffixTypeArray[line.size - 1] = SuffixType.L
        for (i in line.size - 2 downTo 0) {
            if (line[i]!! > line[i + 1]!!) {
                suffixTypeArray[i] = SuffixType.L
                continue
            }
            if (line[i]!! < line[i + 1]!!) {
                suffixTypeArray[i] = SuffixType.S
                continue
            }
            suffixTypeArray[i] = suffixTypeArray[i + 1]
        }
        return suffixTypeArray
    }

    private fun getSuffixArray(data: ByteArray): IntArray {
        val temp = IntArray(data.size)
        for (i in data.indices) {
            temp[i] = data[i] + 128
        }
        return DivSufSort().buildSuffixArray(temp, 0, data.size)
    }

    private fun getMatchLength(
        oldData: ByteArray,
        oldStartPos: Int,
        newData: ByteArray,
        newStartPos: Int
    ): Int {
        var cur = 0
        while (oldStartPos + cur < oldData.size && newStartPos + cur < newData.size) {
            if (oldData[oldStartPos + cur] != newData[newStartPos + cur]) {
                return cur
            }
            cur++
        }
        return cur
    }

    private fun compareArray(
        leftArray: ByteArray,
        rightArray: ByteArray,
        leftPos: Int,
        rightPos: Int,
        size: Int
    ): Int {
        for (i in 0..<size) {
            if (leftArray[leftPos + i] < rightArray[rightPos + i]) {
                return -1
            } else if (leftArray[leftPos + i] > rightArray[rightPos + i]) {
                return 1
            }
        }
        return 0
    }

    data class SearchResult(val pos: Int, val len: Int)

    // 二分搜索最大匹配
    fun search(
        index: Int,
        oldData: ByteArray,
        newData: ByteArray,
    ): SearchResult {
        var left = 0
        var right = oldData.size
        val suffixArray = getSuffixArray()
        while (right - left > 1) {
            val mid: Int = (left + right) / 2
            val size = (oldData.size - suffixArray[mid]).coerceAtMost(newData.size - index)
            if (compareArray(oldData, newData, suffixArray[mid], index, size) < 0) {
                left = mid
                continue
            }
            right = mid
        }
        var matchLen = getMatchLength(oldData, suffixArray[left], newData, index)
        if (right == oldData.size || right == left) {
            return SearchResult(left, matchLen)
        }
        val matchLengthRight = getMatchLength(oldData, suffixArray[right], newData, index)
        if (matchLengthRight > matchLen) {
            left = right
            matchLen = matchLengthRight
        }
        return SearchResult(suffixArray[left], matchLen)
    }

}