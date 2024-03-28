package org.hf.autoprotocol.bean

/**********************************
 * @Name:         BaseProtocolBean
 * @Copyright：  Antoco
 * @CreateDate： 2024/1/31 14:53
 * @author:      huang feng
 * @Version：    1.0
 * @Describe:
 **********************************/
open class BaseProtocolBean(
    open val src : ByteArray
){
    var next : BaseProtocolBean? = null
    var flags = 0


    /***
     * 自定义校验
     */
    open fun customVerify():Boolean{
        return true
    }
}
