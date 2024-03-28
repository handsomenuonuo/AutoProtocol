package org.hf.autoprotocol.bean

import org.hf.autoprotocol.annotations.Endian
import org.hf.autoprotocol.annotations.Verify
import javax.lang.model.element.Element

/**********************************
 * @Name:         ProtocolBean
 * @Copyright：  Antoco
 * @CreateDate： 2024/1/31 14:14
 * @author:      huang feng
 * @Version：    1.0
 * @Describe:
 **********************************/
data class ProtocolAnnotationsBean(
    val length: Int,
    val typeElement: Element
){
    val properties = mutableListOf<ProtocolPropertyBean>()
    val verifies = mutableListOf<ProtocolVerifyBean>()
}

data class ProtocolPropertyBean(
    val propertyElement:Element,
    val offset: Int,
    val length: Int,
    val step :Int = 1,//长度
    val endian: Endian = Endian.BIG_ENDIAN,
    val multiple: Float = 1f,
    val shr : Int = 0,
    val mask: Long = -1L,
)

data class ProtocolVerifyBean(
    val propertyElement:Element,
    val offset :Int,//偏移量
    val length :Int,//长度
    val verifyStart :Int,//偏移量
    val verifyLength :Int,//长度
    val endian : Endian = Endian.BIG_ENDIAN,//大小端
    val type : Verify,
    val isCustom :Boolean = false,
)
