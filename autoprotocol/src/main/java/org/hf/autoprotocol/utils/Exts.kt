package org.hf.autoprotocol.utils

import kotlin.experimental.xor

/**********************************
 * @Name:         Exts
 * @Copyright：  Antoco
 * @CreateDate： 2024/1/31 17:42
 * @author:      huang feng
 * @Version：    1.0
 * @Describe:
 **********************************/
fun ByteArray.toBigLong():Long{
    val length = this.size
    var result = 0L
    for(i in 0 until length){
        result = result or (this[i].toLong() and 0xff shl (length - i - 1) * 8)
    }
    return result
}

fun ByteArray.toLittleLong():Long{
    val length = this.size
    var result = 0L
    for(i in 0 until length){
        result = result or (this[i].toLong() and 0xff shl i * 8)
    }
    return result
}

fun ByteArray.toBigInt():Int{
    val length = this.size
    var result = 0
    for(i in 0 until length){
        result = result or (this[i].toInt() and 0xff shl (length - i - 1) * 8)
    }
    return result
}

fun ByteArray.toLittleInt():Int{
    val length = this.size
    var result = 0
    for(i in 0 until length){
        result = result or (this[i].toInt() and 0xff shl i * 8)
    }
    return result
}

/**
 * 和校验
 * √
 * @return Int
 */
fun ByteArray.getSUM8(): Int {
    var result = 0
    for (i in this) {
        result += i.toInt()
    }
    return result
}

/**
 * 和校验
 * √
 * @return Int
 */
fun ByteArray.getSUM16(): Int {
    var result = 0
    for (i in this) {
        result += i.toInt()
    }
    return result
}

/**
 * 计算CRC8校验码
 * √
 * @return Byte
 */
fun ByteArray.getCRC8(): Byte
{
    var crc = 0x00
    for (data in this){
        crc = crc xor (data.toInt() and 0xff)
        for (j in 0 until 8)
        {
            if ((crc and 0x80) > 0)
            {
                crc = ((crc shl 1) ) xor 0x07
            }
            else
            {
                crc  = crc shl 1
            }
        }
    }
    return crc.toByte()
}

/**
 * 计算CRC8校验码
 * √
 * @return Byte
 */
fun ByteArray.getCRC8_ITU(): Byte {
    var crc = 0
    for (i in this){
        val bs = i
        crc = crc xor (bs.toInt() and 0xff)
        for (j in 0 until 8){
            if (crc and 0x80 != 0){
                crc = crc shl 1
                crc = crc xor 0x07
            }else{
                crc = crc shl 1
            }
        }
    }
    return (crc.toByte()) xor 0x55
}

/**
 * 计算CRC8校验码
 * √
 * @return Byte
 */
fun ByteArray.getCRC8_ROHC(): Byte {
    var crc = 0xff
    for (i in this){
        val bs = i
        crc = crc xor (bs.toInt() and 0xff)
        for (j in 0 until 8){
            if (crc and 0x01 != 0){
                crc = crc shr 1
                crc = crc xor 0xe0
            }else{
                crc = crc shr 1
            }
        }
    }
    return crc.toByte()
}

/**
 * 计算CRC8校验码
 *  √
 * @return Int
 */
fun ByteArray.getCRC8_MAXIM():Byte{
    var crc = 0
    for (i in this){
        crc = crc xor (i.toInt() and 0xff)
        for (j in 0 until 8){
            if (crc and 0x01 != 0){
                crc = crc shr 1
                crc = crc xor 0x8c
            }else{
                crc = crc shr 1
            }
        }
    }
    return crc.toByte()
}

/**
 * 计算CRC16校验码
 * √
 * @return Int
 */
fun ByteArray.getCRC16_IBM(): Int {
    var crc = 0
    for (i in this){
        crc = crc xor (i.toInt() and 0xff)
        for (j in 0 until 8){
            if (crc and 0x01 != 0){
                crc = crc shr 1
                crc = crc xor 0xa001
            }else{
                crc = crc shr 1
            }
        }
    }
    return crc
}

/**
 * 计算CRC16校验码
 * √
 * @return Int
 */
fun ByteArray.getCRC16_MODBUS(): Int {
    var crc = 0xffff
    for (i in this){
        crc = crc xor (i.toInt() and 0xff)
        for (j in 0 until 8){
            if (crc and 0x01 != 0){
                crc = crc shr 1
                crc = crc xor 0xa001
            }else{
                crc = crc shr 1
            }
        }
    }
    return crc
}

/**
 * 计算CRC16校验码
 * √
 * @return Int
 */
fun ByteArray.getCRC16_USB(): Int {
    var crc = 0xFFFF

    for (byte in this) {
        crc = crc xor (byte.toInt() and 0xFF)
        for (bit in 0 until 8) {
            if ((crc and 1) != 0) {
                crc = (crc ushr 1) xor 0xa001
            } else {
                crc = crc ushr 1
            }
        }
    }

    return crc xor 0xffff // 返回校验结果
}

/**
 * 计算CRC16校验码
 * √
 * @return Int
 */
fun ByteArray.getCRC16_CCITT(): Int {
    var crc = 0

    for (byte in this) {
        crc = crc xor (byte.toInt() and 0xFF)
        for (bit in 0 until 8) {
            if ((crc and 1) != 0) {
                crc = (crc ushr 1) xor 0x8408
            } else {
                crc = crc ushr 1
            }
        }
    }
    return crc
}

/**
 * 计算CRC16校验码
 * √
 * @return Int
 */
fun ByteArray.getCRC16_CCITT_FALSE(): Int {
    var crc = 0xFFFF // 初始值为0xFFFF

    for (byte in this) {
        crc = crc xor (byte.toInt() and 0xFF shl 8)
        for (bit in 0 until 8) {
            if ((crc and 0x8000) != 0) {
                crc = ((crc shl 1) xor 0x1021)
            } else {
                crc = crc shl 1
            }
        }
    }
    return crc and 0xffff
}

/**
 * 计算CRC16校验码
 * √
 * @return Int
 */
fun ByteArray.getCRC16_MAXIM(): Int {
    var crc = 0

    for (byte in this) {
        crc = crc xor (byte.toInt() and 0xFF)
        for (bit in 0 until 8) {
            if ((crc and 1) != 0) {
                crc = (crc ushr 1) xor 0xa001
            } else {
                crc = crc ushr 1
            }
        }
    }
    return crc xor 0xffff
}

/**
 * 计算CRC16校验码
 * √
 * @return Int
 */
fun ByteArray.getCRC16_X25(): Int {
    var crc = 0xffff

    for (byte in this) {
        crc = crc xor (byte.toInt() and 0xFF)
        for (bit in 0 until 8) {
            if ((crc and 1) != 0) {
                crc = (crc shr 1) xor 0x8408
            } else {
                crc = crc shr 1
            }
        }
    }
    return crc xor 0xffff
}

/**
 * 计算CRC16校验码
 * √
 * @return Int
 */
fun ByteArray.getCRC16_XMODEM(): Int {
    var crc = 0

    for (byte in this) {
        crc = crc xor (byte.toInt() and 0xFF shl 8)
        for (bit in 0 until 8) {
            if ((crc and 0x8000) != 0) {
                crc = (crc shl 1) xor 0x1021
            } else {
                crc = crc shl 1
            }
        }
    }
    return crc and 0xffff
}

/**
 * 计算CRC16校验码
 * √
 * @return Int
 */
fun ByteArray.getCRC16_DNP(): Int {
    var crc = 0

    for (byte in this) {
        crc = crc xor (byte.toInt() and 0xFF)
        for (bit in 0 until 8) {
            if ((crc and 1) != 0) {
                crc = (crc shr 1) xor 0xa6bc
            } else {
                crc = crc shr 1
            }
        }
    }
    return crc xor 0xffff
}

/**
 * 计算CRC32校验码
 * √
 * @return Long
 */
fun ByteArray.getCRC32(): Long {
    val crc32 = java.util.zip.CRC32()
    crc32.update(this)
    return crc32.value
}

/**
 * 计算CRC32校验码
 * √
 * @return Long
 */
fun ByteArray.getCRC32_MPEG2(): Long {
    var crc = 0xffffffffL
    for (i in this) {
        crc = crc xor (i.toLong() and 0xff shl 24)
        for (j in 0 until 8) {
            if (crc and 0x80000000 != 0L) {
                crc = crc shl 1
                crc = crc xor 0x04c11db7
            } else {
                crc = crc shl 1
            }
        }
    }
    return crc and 0xffffffff
}

//写一个LRC(256-所有数据相加结果取低字节)校验算法
fun ByteArray.getLRC(): Byte {
    var result = 0
    for (i in this) {
        result += i.toInt()
    }
    return (256 - result).toByte()
}

/**
 * 异或校验
 * √
 * @return Int
 */
fun ByteArray.getBCC(): Int {
    var result = 0
    for (i in this) {
        result = result xor i.toInt()
    }
    return result
}


fun main() {
    val bs = byteArrayOf(
        0x55,0x55,//帧头
        0x35,//长度
        0x00, 0x28.toByte(),//量程
        0x01,//使能状态
        0x0a,//增益
        0x02,//fpga
        0x00,0x00,//角度值

        0x00, 0x01,//距离值
        0x00, 0x02,
        0x00, 0x03,
        0x00, 0x04,
        0x00, 0x05,
        0x00, 0x06,
        0x00, 0x00,
        0x00, 0x00,
        0x00, 0x00,

        0x00, 0x06,//能量值
        0x00, 0x05,
        0x00, 0x04,
        0x00, 0x03,
        0x00, 0x02,
        0x00, 0x01,
        0x00, 0x00,
        0x00, 0x00,
        0x00, 0x00,

        0x00,0xf0.toByte(),//电压
        0x00,0x48,
        0x00, 0x31,
        0x00,0x21,

        0x01,0x3b,//温度
        0xaa.toByte(),0xaa.toByte(),//帧尾
    )

    println("========和校验===========")
    println(bs.getSUM8())
    println(bs.getSUM16())
    println("========crc8校验===========")
    println(bs.getCRC8())
    println(bs.getCRC8_ITU())
    println(bs.getCRC8_ROHC())
    println(bs.getCRC8_MAXIM())
    println("========crc16校验===========")
    println(bs.getCRC16_IBM())
    println(bs.getCRC16_MODBUS())
    println(bs.getCRC16_USB())
    println(bs.getCRC16_CCITT())
    println(bs.getCRC16_CCITT_FALSE())
    println(bs.getCRC16_MAXIM())
    println(bs.getCRC16_X25())
    println(bs.getCRC16_XMODEM())
    println(bs.getCRC16_DNP())
    println("========crc32校验===========")
    println(bs.getCRC32())
    println(bs.getCRC32_MPEG2())
    println("========LRC校验===========")
    println(bs.getLRC())
    println("========bcc校验===========")
    println(bs.getBCC())
}