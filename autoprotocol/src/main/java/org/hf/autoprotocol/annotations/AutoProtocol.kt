package org.hf.autoprotocol.annotations

/**********************************
 * @Name:        AutoProtocol
 * @Copyright：  Antoco
 * @CreateDate： 2024/1/31 10:23
 * @author:      huang feng
 * @Version：    1.0
 * @Describe:
 **********************************/
/**
 * 用于实体类上，点击rebuild时，根据此注解，生成 ${className}DeUtils.kt 的工具类
 * 此工具类根据 [ProtocolProperty] 和 [ProtocolVerify] 生成对应的解析方法
 * 在此工具类中，使用了对象池，采用了一个简单的享元设计模式，每次使用完数据，需要执行recycle方法，将数据放回池中
 * @param length 协议长度
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class AutoDeProtocol(
     val length :Int
)

/**
 * 用于实体类上，点击rebuild时，根据此注解，生成 ${className}EnUtils.kt 的工具类
 * 此工具类根据 [ProtocolProperty] 和 [ProtocolVerify] 生成对应的encode方法
 * @param length 协议长度
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class AutoEnProtocol(
    val length :Int
)

/**
 * 协议属性
 * @param offset 偏移量，此数据在总协议中的起始位
 * @param length 长度，此数据的长度
 * @param step 步长,用于数组，当数据类型是数组时，此值表示数组中每个数据，在协议中所占的byte个数，例如 offset = 10，length = 2,step = 3,则表示数组中每个数据在协议中占3个byte，第一个数据在10-12，第二个数据在13-15。。。
 * @param endian [Endian]大小端 默认大端
 * @param multiple 倍数，默认1，用于数据需要乘以某个倍数
 * @param shr 右移位数，用于某些数据只是其中部分bit位，先执行右移，再执行mask，用于取出其中的部分bit位，例如offset = 0, length = 1, mask = 0x1 取出第一个字节的第一位，offset = 0, length = 1, shr = 1,mask = 0x01，取出第一个字节的第二位。。。
 * @param mask 掩码，可用于某些数据只是其中部分bit位，先执行右移，再执行mask，用于取出其中的部分bit位
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
annotation class ProtocolProperty(
    val offset :Int,//偏移量，byte boolean char 只取第一位
    val length :Int,//长度
    val step :Int = 0,//步长
    val endian : Endian = Endian.BIG_ENDIAN,//大小端
    val multiple: Float = 1f,//倍数
    //以下两个属性用于取出其中的部分bit位，编码时亦然
    val shr : Int = 0,//右移位数，用于某些数据只是其中部分bit位,先执行右移，再执行mask
    val mask: Long = -1,//掩码，可用于某些数据只是其中部分bit位，先执行右移，再执行mask
)

/**
 * 校验数据,用于协议中的检验数据，在解析时自动校验
 * @param offset 偏移量，此数据在总协议中的起始位
 * @param length 长度，此数据的长度
 * @param verifyStart 校验开始位置，需要校验的数据在协议中的起始位
 * @param verifyLength 校验长度，需要校验的数据的长度
 * @param endian [Endian]大小端 默认大端
 * @param type [Verify]校验类型
 * @param isCustom 是否自定义校验,默认false,如果为true,则需要实现 [org.hf.autoprotocol.bean.BaseProtocolBean.customVerify] 方法
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
annotation class ProtocolVerify(
    val offset :Int,//偏移量
    val length :Int,//长度
    val verifyStart :Int,//偏移量
    val verifyLength :Int,//长度
    val endian : Endian = Endian.BIG_ENDIAN,//大小端
    val type :Verify,
    val isCustom :Boolean = false,
)

/**
 * 大小端
 */
enum class Endian{
    BIG_ENDIAN,
    LITTLE_ENDIAN
}

/**
 * 校验类型
 */
enum class Verify{
    SUM8,
    SUM16,

    CRC8,
    CRC8_ITU,
    CRC8_ROHC,
    CRC8_MAXIM,

    CRC16_IBM,
    CRC16_MODBUS,
    CRC16_USB,
    CRC16_CCITT,
    CRC16_CCITT_FALSE,
    CRC16_MAXIM,
    CRC16_X25,
    CRC16_XMODEM,
    CRC16_DNP,

    CRC32,
    CRC32_MPEG2,

    LRC,

    BCC,
    XOR,
}