package org.hf.autoprotocol

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import org.hf.autoprotocol.annotations.AutoDeProtocol
import org.hf.autoprotocol.annotations.AutoEnProtocol
import org.hf.autoprotocol.annotations.Endian
import org.hf.autoprotocol.annotations.ProtocolProperty
import org.hf.autoprotocol.annotations.ProtocolVerify
import org.hf.autoprotocol.annotations.Verify
import org.hf.autoprotocol.bean.ProtocolAnnotationsBean
import org.hf.autoprotocol.bean.ProtocolPropertyBean
import org.hf.autoprotocol.bean.ProtocolVerifyBean
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.TypeKind
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic


/**********************************
 * @Name:         AutoProtocolProcesser
 * @Copyright：  Antoco
 * @CreateDate： 2024/1/31 10:46
 * @author:      huang feng
 * @Version：    1.0
 * @Describe:
 **********************************/
@AutoService(Processor::class)
class AutoDeProtocolProcessor : AbstractProcessor() {

    private lateinit var messager: Messager
    private lateinit var elementsUtils: Elements
    private lateinit var filer: Filer
    private lateinit var typeUtils: Types

    private val decodeMap = mutableMapOf<String,ProtocolAnnotationsBean>()

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        typeUtils = processingEnv.typeUtils
        elementsUtils = processingEnv.elementUtils
        filer = processingEnv.filer
        messager = processingEnv.messager

    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun getSupportedOptions(): MutableSet<String> {
        return super.getSupportedOptions()
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        val set = mutableSetOf<String>()
        set.add(AutoDeProtocol::class.java.canonicalName)
        set.add(ProtocolProperty::class.java.canonicalName)
        set.add(ProtocolVerify::class.java.canonicalName)
        return set
    }

    override fun process(set: MutableSet<out TypeElement>, roundEnvironment : RoundEnvironment): Boolean {
        if(set.isNotEmpty()){
            getDeProtocolBean(roundEnvironment)
            getProtocolProperty(roundEnvironment)
            getProtocolVerify(roundEnvironment)
            createDeFile(decodeMap.entries)
        }
        return true
    }

    private fun getDeProtocolBean(roundEnvironment: RoundEnvironment){
        val set = roundEnvironment.getElementsAnnotatedWith(AutoDeProtocol::class.java)
        set.forEach{
            if(it is TypeElement){
                val superClassName = it.superclass.asTypeName().toString()
                if(superClassName != "org.hf.autoprotocol.bean.BaseProtocolBean") {
                    messager.printMessage(Diagnostic.Kind.ERROR,"${it.simpleName}必须继承 BaseProtocolBean")
                }
                val length = it.getAnnotation(AutoDeProtocol::class.java).length
                if(length <=0) messager.printMessage(Diagnostic.Kind.ERROR,"${it.simpleName}注解的length不能小于0")
                val protocolBean = ProtocolAnnotationsBean(length,it)
                decodeMap[it.simpleName.toString()] = protocolBean
            }
        }
    }

    private fun getProtocolProperty(roundEnvironment: RoundEnvironment){
        val set = roundEnvironment.getElementsAnnotatedWith(ProtocolProperty::class.java)
        set.forEach{ it ->
            val annotation = it.getAnnotation(ProtocolProperty::class.java)
            val offset = annotation.offset
            val length = annotation.length
            val step = annotation.step
            val endian = annotation.endian
            val multiple = annotation.multiple
            val shr = annotation.shr
            val mask = annotation.mask
            val superClass = it.enclosingElement.simpleName.toString()

            val protocolPropertyBean = ProtocolPropertyBean(it,offset,length,step,endian,multiple,shr,mask)
            val protocolAnnotationsDeBean = decodeMap[superClass]
            protocolAnnotationsDeBean?.let { pb ->
                pb.properties.add(protocolPropertyBean)
            }
        }
    }

    private fun getProtocolVerify(roundEnvironment: RoundEnvironment){
        val set = roundEnvironment.getElementsAnnotatedWith(ProtocolVerify::class.java)
        set.forEach{ it ->
            val annotation = it.getAnnotation(ProtocolVerify::class.java)
            val offset = annotation.offset
            val length = annotation.length
            val verifyStart = annotation.verifyStart
            val verifyLength = annotation.verifyLength
            val endian = annotation.endian
            val type = annotation.type
            val isCustom = annotation.isCustom
            val superClass = it.enclosingElement.simpleName.toString()

            val protocolVerifyBean = ProtocolVerifyBean(it,offset,length,verifyStart,verifyLength,endian,type,isCustom)
            val protocolAnnotationsBean = decodeMap[superClass]
            protocolAnnotationsBean?.let { pb ->
                pb.verifies.add(protocolVerifyBean)
            }
        }
    }

    /**
     * 创建文件
     * @param entries
     */
    private fun createDeFile(entries : Set<Map.Entry<String, ProtocolAnnotationsBean>> ) {
        entries.forEach {
            val protocolBean = it.value
            //获取包名
            val packageName = elementsUtils.getPackageOf(protocolBean.typeElement).qualifiedName.toString()
            //获取类名
            val className = protocolBean.typeElement.simpleName.toString()
            val newClassName = className + "DeUtil"

            val decodeFunBuilder = FunSpec.builder("decode")
                .addModifiers(KModifier.PUBLIC)
                .addParameter("bytes", ByteArray::class)
                .returns(protocolBean.typeElement.asType().asTypeName().copy(nullable = true))
                .addStatement("if(bytes.size != ${protocolBean.length}) return null")


            val cloneFunBuilder = FunSpec.builder("clone")
                .receiver(ClassName(packageName,className))
                .returns(protocolBean.typeElement.asType().asTypeName())
                .addModifiers(KModifier.PUBLIC)
                .addStatement("val data = $newClassName.obtain(this.src)")

            val ktFileBuilder = FileSpec.builder(packageName,newClassName)

            if(protocolBean.verifies.isEmpty()) {
                decodeFunBuilder.addStatement("val result = $newClassName.obtain(bytes)")
            }
            protocolBean.verifies.forEach {protocolVerifyBean ->
                val propertyElement = protocolVerifyBean.propertyElement
                val pName = propertyElement.simpleName
                if(protocolVerifyBean.isCustom){
                    decodeFunBuilder.addStatement("if(result.customVerify())return null")
                }else{
                    if(propertyElement.asType().kind != TypeKind.BYTE
                        &&propertyElement.asType().kind != TypeKind.SHORT
                        &&propertyElement.asType().kind != TypeKind.INT
                        &&propertyElement.asType().kind != TypeKind.LONG){
                        messager.printMessage(Diagnostic.Kind.ERROR,"$pName 校验码只能是Byte、Short、Int、Long类型")
                    }
                    decodeFunBuilder.addStatement("val verify = bytes.copyOfRange(${
                        protocolVerifyBean.offset
                    },${
                        protocolVerifyBean.offset
                    } + ${
                        protocolVerifyBean.length
                    }).${when(protocolVerifyBean.endian){
                        Endian.BIG_ENDIAN->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "toBigLong")
                            "toBigLong()"
                        }
                        Endian.LITTLE_ENDIAN->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "toLittleLong")
                            "toLittleLong()"
                        }
                    }
                    }.${when(propertyElement.asType().kind){
                        TypeKind.BYTE->{
                            "toByte()"
                        }
                        TypeKind.SHORT->{
                            "toShort()"
                        }
                        TypeKind.INT->{
                            "toInt()"
                        }
                        TypeKind.LONG->{
                            "toLong()"
                        }
                        else->{
                            "使用了一个不支持的校验返回类型"
                        }
                    }}")
                    decodeFunBuilder.addStatement("val verifyCheck = bytes.copyOfRange(${
                        protocolVerifyBean.verifyStart
                    },${
                        protocolVerifyBean.verifyStart
                    } + ${
                        protocolVerifyBean.verifyLength
                    }).${ when(protocolVerifyBean.type){
                        Verify.SUM8->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getSUM8")
                            "getSUM8()"
                        }
                        Verify.SUM16->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getSUM16")
                            "getSUM16()"
                        }
                        Verify.CRC8->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC8")
                            "getCRC8()"
                        }
                        Verify.CRC8_ITU->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC8_ITU")
                            "getCRC8_ITU()"
                        }
                        Verify.CRC8_ROHC->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC8_ROHC")
                            "getCRC8_ROHC()"
                        }
                        Verify.CRC8_MAXIM->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC8_MAXIM")
                            "getCRC8_MAXIM()"
                        }
                        Verify.CRC16_IBM->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_IBM")
                            "getCRC16_IBM()"
                        }
                        Verify.CRC16_MODBUS->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_MODBUS")
                            "getCRC16_MODBUS()"
                        }
                        Verify.CRC16_USB->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_USB")
                            "getCRC16_USB()"
                        }
                        Verify.CRC16_CCITT->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_CCITT")
                            "getCRC16_CCITT()"
                        }
                        Verify.CRC16_CCITT_FALSE->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_CCITT_FALSE")
                            "getCRC16_CCITT_FALSE()"
                        }
                        Verify.CRC16_MAXIM->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_MAXIM")
                            "getCRC16_MAXIM()"
                        }
                        Verify.CRC16_X25->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_X25")
                            "getCRC16_X25()"
                        }
                        Verify.CRC16_XMODEM->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_XMODEM")
                            "getCRC16_XMODEM()"
                        }
                        Verify.CRC16_DNP->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_DNP")
                            "getCRC16_DNP()"
                        }
                        Verify.CRC32->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC32")
                            "getCRC32()"
                        }
                        Verify.CRC32_MPEG2->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC32_MPEG2")
                            "getCRC32_MPEG2()"
                        }
                        Verify.LRC->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getLRC")
                            "getLRC()"
                        }
                        Verify.XOR,Verify.BCC->{
                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getBCC")
                            "getBCC()"
                        }
                    }}.${when(propertyElement.asType().kind){
                        TypeKind.BYTE->{
                            "toByte()"
                        }
                        TypeKind.SHORT->{
                            "toShort()"
                        }
                        TypeKind.INT->{
                            "toInt()"
                        }
                        TypeKind.LONG->{
                            "toLong()"
                        }
                        else->{
                            "使用了一个不支持的校验返回类型"
                        }
                    }}")
                    decodeFunBuilder.addStatement("if(verifyCheck != verify)return null")
                        .addStatement("val result = $newClassName.obtain(bytes)")
                        .addStatement("result.${pName} = verify")
                }
            }

            protocolBean.properties.forEach { protocolPropertyBean ->
                val propertyElement = protocolPropertyBean.propertyElement

                if(propertyElement.asType().kind == TypeKind.ARRAY){
                    cloneFunBuilder.addStatement(
                        "this.${propertyElement.simpleName}?.let {\n" +
                                "if(data.${propertyElement.simpleName} == null) {\n" +
                                "   data.${propertyElement.simpleName} = it.clone()\n" +
                                "}else{\n" +
                                "   for(i in 0 until it.size){\n" +
                                "       data.${propertyElement.simpleName}!![i] = it[i]\n" +
                                "   }\n" +
                                "}\n}")
                    if(protocolPropertyBean.step == 0)messager.printMessage(Diagnostic.Kind.ERROR,"$className 的${propertyElement.simpleName}数组，必须定义step属性")
                    val count = protocolPropertyBean.length / protocolPropertyBean.step
                    decodeFunBuilder.addStatement(
                        "result.${propertyElement.simpleName} = result.${propertyElement.simpleName} ?: ${
                            when((propertyElement.asType() as ArrayType).componentType.kind){
                                TypeKind.BOOLEAN->{
                                    "Boolean"
                                }
                                TypeKind.BYTE->{
                                    "Byte"
                                }
                                TypeKind.SHORT->{
                                    "Short"
                                }
                                TypeKind.INT->{
                                    "Int"
                                }
                                TypeKind.LONG->{
                                    "Long"
                                }
                                TypeKind.CHAR->{
                                    "Char"
                                }
                                TypeKind.FLOAT->{
                                    "Float"
                                }
                                TypeKind.DOUBLE->{
                                    "Double"
                                }
                                else->{
                                    "使用了一个不支持的数组类型"
                                }
                            }
                        }Array(${count})"
                    )
                    decodeFunBuilder.addStatement(
                        "for(i in 0 until $count){\nresult.${propertyElement.simpleName}!![i] = ${
                            generatePropertyCode(ktFileBuilder,protocolPropertyBean,"${protocolPropertyBean.offset}+i*${protocolPropertyBean.step}","${protocolPropertyBean.step}")
                        }\n.${
                            when((propertyElement.asType() as ArrayType).componentType.kind){
                                TypeKind.BOOLEAN->{
                                    "toInt() == 1"
                                }
                                TypeKind.BYTE->{
                                    "toInt().toByte()"
                                }
                                TypeKind.SHORT->{
                                    "toInt().toShort()"
                                }
                                TypeKind.INT->{
                                    "toInt()"
                                }
                                TypeKind.LONG->{
                                    "toLong()"
                                }
                                TypeKind.CHAR->{
                                    "toInt().toChar()"
                                }
                                TypeKind.FLOAT->{
                                    "toFloat()"
                                }
                                TypeKind.DOUBLE->{
                                    "toDouble()"
                                }
                                else->{
                                    "使用了一个不支持的返回类型"
                                }
                            }
                        }\n}"
                    )
                    return@forEach
                }
                cloneFunBuilder.addStatement("data.${propertyElement.simpleName} = this.${propertyElement.simpleName}")
                val ps =  generatePropertyCode(ktFileBuilder,protocolPropertyBean,protocolPropertyBean.offset.toString(),protocolPropertyBean.length.toString())
                decodeFunBuilder.addStatement(
                    "result.${propertyElement.simpleName} = ${ps}.${
                        when(propertyElement.asType().kind){
                            TypeKind.BOOLEAN->{
                                "toInt() == 1"
                            }
                            TypeKind.BYTE->{
                                "toInt().toByte()"
                            }
                            TypeKind.SHORT->{
                                "toInt().toShort()"
                            }
                            TypeKind.INT->{
                                "toInt()"
                            }
                            TypeKind.LONG->{
                                "toLong()"
                            }
                            TypeKind.CHAR->{
                                "toInt().toChar()"
                            }
                            TypeKind.FLOAT->{
                                "toFloat()"
                            }
                            TypeKind.DOUBLE->{
                                "toDouble()"
                            }
                            else->{
                                "使用了一个不支持的返回类型"
                            }
                        }
                    }"
                )
            }
            decodeFunBuilder.addStatement("return result")

            val type = TypeSpec.objectBuilder(newClassName)
                .addModifiers(KModifier.FINAL)
                .addFunction(decodeFunBuilder.build())
                .addProperty(PropertySpec.builder("sPool", protocolBean.typeElement.asType().asTypeName().copy(nullable = true))
                    .mutable()
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("null")
                    .build())
                .addProperty(PropertySpec.builder("sPoolSync", Any::class)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("Any()")
                    .build())
                .addProperty(PropertySpec.builder("sPoolSize", Int::class)
                    .mutable()
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("0")
                    .build())
                .addProperty(PropertySpec.builder("FLAG_IN_USE", Int::class)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("1")
                    .build())
                .addProperty(PropertySpec.builder("MAX_POOL_SIZE", Int::class)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("100")
                    .build())
                .addFunction(FunSpec.builder("obtain")
                    .addModifiers(KModifier.PUBLIC)
                    .addParameter("bytes", ByteArray::class)
                    .returns(protocolBean.typeElement.asType().asTypeName())
                    .addStatement(" synchronized(sPoolSync) {\n" +
                            "   if (sPool != null) {\n" +
                            "       val m: $className = sPool!!\n" +
                            "       sPool = m.next as $className?\n" +
                            "       m.next = null\n" +
                            "       m.flags = 0\n" +
                            "       sPoolSize--\n" +
                            "       return m\n" +
                            "   }\n" +
                            "}\n" +
                            "return $className(bytes)")
                    .build())
                .addFunction(FunSpec.builder("isInUse")
                    .addParameter("bean", protocolBean.typeElement.asType().asTypeName())
                    .addModifiers(KModifier.PRIVATE)
                    .returns(Boolean::class)
                    .addStatement("return bean.flags and FLAG_IN_USE == FLAG_IN_USE")
                    .build())
                .addFunction(FunSpec.builder("recycle")
                    .addParameter("bean", protocolBean.typeElement.asType().asTypeName())
                    .addModifiers(KModifier.PUBLIC)
                    .addStatement("check(!isInUse(bean)) {\n" +
                            "   \"This message cannot be recycled because it is still in use.\"\n" +
                            "}\n" +
                            "recycleUnchecked(bean)")
                    .build())
                .addFunction(FunSpec.builder("recycleUnchecked")
                    .addParameter("bean", protocolBean.typeElement.asType().asTypeName())
                    .addModifiers(KModifier.PRIVATE)
                    .addStatement("bean.flags = FLAG_IN_USE\n" +
                            "synchronized(sPoolSync) {\n" +
                            "   if (sPoolSize < MAX_POOL_SIZE) {\n" +
                            "       bean.next = sPool\n" +
                            "       sPool = bean\n" +
                            "       sPoolSize++\n" +
                            "   }\n" +
                            "}")
                    .build())
                .build()

            cloneFunBuilder.addStatement("return data")

            val ktFile = ktFileBuilder.addType(type)
                .addProperty(PropertySpec.builder("length", Int::class)
                    .receiver(ClassName(packageName,className))
                    .getter(FunSpec.getterBuilder()
                        .addCode("return ${protocolBean.length}")
                        .build())
                    .build())
                .addFunction(FunSpec.builder("recycle")
                    .receiver(ClassName(packageName,className))
                    .addModifiers(KModifier.PUBLIC)
                    .addStatement("$newClassName.recycle(this)")
                    .build())
                .addFunction(cloneFunBuilder.build())
                .build()

            ktFile.writeTo(filer)
        }
    }

    private fun generatePropertyCode(ktFileBuilder:FileSpec.Builder,protocolPropertyBean : ProtocolPropertyBean,
        offset: String , length:String
    ):String{
       return "(((bytes.copyOfRange(${
            offset
        },${
            offset
        } + ${
            length
        }).${
           if(protocolPropertyBean.endian == Endian.BIG_ENDIAN){
               ktFileBuilder.addImport("org.hf.autoprotocol.utils", "toBigLong")
               "toBigLong()"
           }else{
               ktFileBuilder.addImport("org.hf.autoprotocol.utils", "toLittleLong")
               "toLittleLong()"
           }
       }${
           if(protocolPropertyBean.shr > 0) " shr ${
           protocolPropertyBean.shr
       })" else ")"
       } ${
           if(protocolPropertyBean.mask != -1L) ".and(${
           protocolPropertyBean.mask
       }))" else ")"
       }${
           if(protocolPropertyBean.multiple!=1f)"*${
               protocolPropertyBean.multiple
           })" else ")"
       }"
    }
}

