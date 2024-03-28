package org.hf.autoprotocol

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
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
import kotlin.math.log2
import kotlin.math.sqrt


/**********************************
 * @Name:         AutoProtocolProcesser
 * @Copyright：  Antoco
 * @CreateDate： 2024/1/31 10:46
 * @author:      huang feng
 * @Version：    1.0
 * @Describe:
 **********************************/
@AutoService(Processor::class)
class AutoEnProtocolProcessor : AbstractProcessor() {

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
        set.add(AutoEnProtocol::class.java.canonicalName)
        set.add(ProtocolProperty::class.java.canonicalName)
        set.add(ProtocolVerify::class.java.canonicalName)
        return set
    }

    override fun process(set: MutableSet<out TypeElement>, roundEnvironment : RoundEnvironment): Boolean {
        if(set.isNotEmpty()){
            getEnProtocolBean(roundEnvironment)
            getProtocolProperty(roundEnvironment)
            getProtocolVerify(roundEnvironment)
            createDeFile(decodeMap.entries)
        }
        return true
    }

    private fun getEnProtocolBean(roundEnvironment: RoundEnvironment){
        val set = roundEnvironment.getElementsAnnotatedWith(AutoEnProtocol::class.java)
        set.forEach{
            if(it is TypeElement){
                messager.printMessage(Diagnostic.Kind.NOTE,"开始解析 $it")
                val length = it.getAnnotation(AutoEnProtocol::class.java).length
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
            val newClassName = className + "EnUtil"

            val decodeFunBuilder = FunSpec.builder("decode")
                .addModifiers(KModifier.PUBLIC)
                .addParameter("bytes", ByteArray::class)
                .returns(protocolBean.typeElement.asType().asTypeName().copy(nullable = true))
                .addStatement("if(bytes.size != ${protocolBean.length}) return null")


            val ktFileBuilder = FileSpec.builder(packageName,newClassName)

//            protocolBean.verifies.forEach {protocolVerifyBean ->
//                val propertyElement = protocolVerifyBean.propertyElement
//                val pName = propertyElement.simpleName
//                if(protocolVerifyBean.isCustom){
//                    decodeFunBuilder.addStatement("if(result.customVerify())return null")
//                }else{
//                    if(propertyElement.asType().kind != TypeKind.BYTE
//                        &&propertyElement.asType().kind != TypeKind.SHORT
//                        &&propertyElement.asType().kind != TypeKind.INT
//                        &&propertyElement.asType().kind != TypeKind.LONG){
//                        messager.printMessage(Diagnostic.Kind.ERROR,"$pName 校验码只能是Byte、Short、Int、Long类型")
//                    }
//                    decodeFunBuilder.addStatement("val verify = bytes.copyOfRange(${
//                        protocolVerifyBean.offset
//                    },${
//                        protocolVerifyBean.offset
//                    } + ${
//                        protocolVerifyBean.length
//                    }).${when(protocolVerifyBean.endian){
//                        Endian.BIG_ENDIAN->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "toBigLong")
//                            "toBigLong()"
//                        }
//                        Endian.LITTLE_ENDIAN->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "toLittleLong")
//                            "toLittleLong()"
//                        }
//                    }
//                    }.${when(propertyElement.asType().kind){
//                        TypeKind.BYTE->{
//                            "toByte()"
//                        }
//                        TypeKind.SHORT->{
//                            "toShort()"
//                        }
//                        TypeKind.INT->{
//                            "toInt()"
//                        }
//                        TypeKind.LONG->{
//                            "toLong()"
//                        }
//                        else->{
//                            "使用了一个不支持的校验返回类型"
//                        }
//                    }}")
//                    decodeFunBuilder.addStatement("val verifyCheck = bytes.copyOfRange(${
//                        protocolVerifyBean.verifyStart
//                    },${
//                        protocolVerifyBean.verifyStart
//                    } + ${
//                        protocolVerifyBean.verifyLength
//                    }).${ when(protocolVerifyBean.type){
//                        Verify.SUM8->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getSUM8")
//                            "getSUM8()"
//                        }
//                        Verify.SUM16->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getSUM16")
//                            "getSUM16()"
//                        }
//                        Verify.CRC8->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC8")
//                            "getCRC8()"
//                        }
//                        Verify.CRC8_ITU->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC8_ITU")
//                            "getCRC8_ITU()"
//                        }
//                        Verify.CRC8_ROHC->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC8_ROHC")
//                            "getCRC8_ROHC()"
//                        }
//                        Verify.CRC8_MAXIM->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC8_MAXIM")
//                            "getCRC8_MAXIM()"
//                        }
//                        Verify.CRC16_IBM->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_IBM")
//                            "getCRC16_IBM()"
//                        }
//                        Verify.CRC16_MODBUS->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_MODBUS")
//                            "getCRC16_MODBUS()"
//                        }
//                        Verify.CRC16_USB->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_USB")
//                            "getCRC16_USB()"
//                        }
//                        Verify.CRC16_CCITT->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_CCITT")
//                            "getCRC16_CCITT()"
//                        }
//                        Verify.CRC16_CCITT_FALSE->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_CCITT_FALSE")
//                            "getCRC16_CCITT_FALSE()"
//                        }
//                        Verify.CRC16_MAXIM->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_MAXIM")
//                            "getCRC16_MAXIM()"
//                        }
//                        Verify.CRC16_X25->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_X25")
//                            "getCRC16_X25()"
//                        }
//                        Verify.CRC16_XMODEM->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_XMODEM")
//                            "getCRC16_XMODEM()"
//                        }
//                        Verify.CRC16_DNP->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC16_DNP")
//                            "getCRC16_DNP()"
//                        }
//                        Verify.CRC32->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC32")
//                            "getCRC32()"
//                        }
//                        Verify.CRC32_MPEG2->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getCRC32_MPEG2")
//                            "getCRC32_MPEG2()"
//                        }
//                        Verify.LRC->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getLRC")
//                            "getLRC()"
//                        }
//                        Verify.XOR,Verify.BCC->{
//                            ktFileBuilder.addImport("org.hf.autoprotocol.utils", "getBCC")
//                            "getBCC()"
//                        }
//                    }}.${when(propertyElement.asType().kind){
//                        TypeKind.BYTE->{
//                            "toByte()"
//                        }
//                        TypeKind.SHORT->{
//                            "toShort()"
//                        }
//                        TypeKind.INT->{
//                            "toInt()"
//                        }
//                        TypeKind.LONG->{
//                            "toLong()"
//                        }
//                        else->{
//                            "使用了一个不支持的校验返回类型"
//                        }
//                    }}")
//                    decodeFunBuilder.addStatement("if(verifyCheck != verify)return null")
//                        .addStatement("val result = $newClassName.obtain(bytes)")
//                        .addStatement("result.${pName} = verify")
//                }
//            }

//            protocolBean.properties.forEach { protocolPropertyBean ->
//                val propertyElement = protocolPropertyBean.propertyElement
//
//                if(propertyElement.asType().kind == TypeKind.ARRAY){
//
//                    if(protocolPropertyBean.step == 0)messager.printMessage(Diagnostic.Kind.ERROR,"$className 的${propertyElement.simpleName}数组，必须定义step属性")
//                    val count = protocolPropertyBean.length / protocolPropertyBean.step
//                    decodeFunBuilder.addStatement(
//                        "result.${propertyElement.simpleName} = result.${propertyElement.simpleName} ?: ${
//                            when((propertyElement.asType() as ArrayType).componentType.kind){
//                                TypeKind.BOOLEAN->{
//                                    "Boolean"
//                                }
//                                TypeKind.BYTE->{
//                                    "Byte"
//                                }
//                                TypeKind.SHORT->{
//                                    "Short"
//                                }
//                                TypeKind.INT->{
//                                    "Int"
//                                }
//                                TypeKind.LONG->{
//                                    "Long"
//                                }
//                                TypeKind.CHAR->{
//                                    "Char"
//                                }
//                                TypeKind.FLOAT->{
//                                    "Float"
//                                }
//                                TypeKind.DOUBLE->{
//                                    "Double"
//                                }
//                                else->{
//                                    "使用了一个不支持的数组类型"
//                                }
//                            }
//                        }Array(${count})"
//                    )
//                    decodeFunBuilder.addStatement(
//                        "for(i in 0 until $count){\nresult.${propertyElement.simpleName}!![i] = ${
//                            generatePropertyCode(ktFileBuilder,protocolPropertyBean,"${protocolPropertyBean.offset}+i*${protocolPropertyBean.step}","${protocolPropertyBean.step}")
//                        }\n.${
//                            when((propertyElement.asType() as ArrayType).componentType.kind){
//                                TypeKind.BOOLEAN->{
//                                    "toInt() == 1"
//                                }
//                                TypeKind.BYTE->{
//                                    "toInt().toByte()"
//                                }
//                                TypeKind.SHORT->{
//                                    "toInt().toShort()"
//                                }
//                                TypeKind.INT->{
//                                    "toInt()"
//                                }
//                                TypeKind.LONG->{
//                                    "toLong()"
//                                }
//                                TypeKind.CHAR->{
//                                    "toInt().toChar()"
//                                }
//                                TypeKind.FLOAT->{
//                                    "toFloat()"
//                                }
//                                TypeKind.DOUBLE->{
//                                    "toDouble()"
//                                }
//                                else->{
//                                    "使用了一个不支持的返回类型"
//                                }
//                            }
//                        }\n}"
//                    )
//                    return@forEach
//                }
//                val ps =  generatePropertyCode(ktFileBuilder,protocolPropertyBean,protocolPropertyBean.offset.toString(),protocolPropertyBean.length.toString())
//                decodeFunBuilder.addStatement(
//                    "result.${propertyElement.simpleName} = ${ps}.${
//                        when(propertyElement.asType().kind){
//                            TypeKind.BOOLEAN->{
//                                "toInt() == 1"
//                            }
//                            TypeKind.BYTE->{
//                                "toInt().toByte()"
//                            }
//                            TypeKind.SHORT->{
//                                "toInt().toShort()"
//                            }
//                            TypeKind.INT->{
//                                "toInt()"
//                            }
//                            TypeKind.LONG->{
//                                "toLong()"
//                            }
//                            TypeKind.CHAR->{
//                                "toInt().toChar()"
//                            }
//                            TypeKind.FLOAT->{
//                                "toFloat()"
//                            }
//                            TypeKind.DOUBLE->{
//                                "toDouble()"
//                            }
//                            else->{
//                                "使用了一个不支持的返回类型"
//                            }
//                        }
//                    }"
//                )
//            }
//
//            decodeFunBuilder.addStatement("return result")

            //init代码块
            val initialCodeBuilder = CodeBlock.builder()

            //类的构造
            val typeBuilder = TypeSpec.objectBuilder(newClassName)//类名
                 .addModifiers(KModifier.FINAL)//增加final修饰符
                .addSuperclassConstructorParameter(CodeBlock.of("${protocolBean.length}"))
                .addProperty(PropertySpec.builder("delegate",protocolBean.typeElement.asType().asTypeName())//增加delegate字段
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("$className()")
                    .build())
                .addProperty(PropertySpec.builder("resultBytes", ByteArray::class)//增加resultBytes字段
                    .initializer("ByteArray(${protocolBean.length})")
                    .build())

            for(protocolPropertyBean in protocolBean.properties){
                val propertyElement = protocolPropertyBean.propertyElement
                val pName = propertyElement.simpleName
                initialCodeBuilder.addStatement("$pName(delegate.$pName)")
                if(protocolPropertyBean.mask != -1L){
                    val maskFunBuilder = FunSpec.builder("$pName")
                        .addModifiers(KModifier.PRIVATE)
                        .addParameter("data",propertyElement.asType().asTypeName())
                    if(protocolPropertyBean.endian == Endian.BIG_ENDIAN){
                        ktFileBuilder.addImport("org.hf.autoprotocol.utils", "toBigLong")
                        maskFunBuilder.addStatement("val src = resultBytes.copyOfRange(${protocolPropertyBean.offset},${protocolPropertyBean.offset}+${protocolPropertyBean.length}).toBigLong()")
                    }else{
                        ktFileBuilder.addImport("org.hf.autoprotocol.utils", "toLittleLong")
                        maskFunBuilder.addStatement("val src = resultBytes.copyOfRange(${protocolPropertyBean.offset},${protocolPropertyBean.offset}+${protocolPropertyBean.length}).toLittleLong()")
                    }
                    maskFunBuilder
                        .addStatement("val bitCount = ${log2((protocolPropertyBean.mask +1).toDouble()).toInt()}")
                        .addStatement("val afterShr = src shr ${protocolPropertyBean.shr}")
                        .addStatement("val start = afterShr shr bitCount")
                        .addStatement("val invAfterShr = afterShr.inv()")
                        .addStatement("val end = invAfterShr and src")

                    when(propertyElement.asType().kind){
                        TypeKind.BOOLEAN->{
                            maskFunBuilder.addStatement("val d = if(data) 1 else 0")
                        }
                        TypeKind.CHAR,TypeKind.BYTE,
                        TypeKind.FLOAT,
                        TypeKind.DOUBLE,
                        TypeKind.SHORT,
                        TypeKind.INT,
                        TypeKind.LONG->{
                            maskFunBuilder.addStatement("val d = data.toLong()")
                        }
                        else->{
                            messager.printMessage(Diagnostic.Kind.ERROR,"${pName} 必须有默认值，或者使用了一个不支持的类型 ${propertyElement.enclosingElement.asType().kind}",protocolBean.typeElement)
                        }
                    }
                    typeBuilder
                        .addFunction(FunSpec.builder("set${pName.toString().capitalize()}")
                            .addModifiers(KModifier.PUBLIC)
                            .returns(ByteArray::class)
                            .addParameter("data",propertyElement.asType().asTypeName())
                            .addStatement("$pName(data)")
                            .addStatement("return resultBytes")
                            .build())
                        .addFunction(FunSpec.builder("set${pName.toString().capitalize()}")
                            .addModifiers(KModifier.PUBLIC,KModifier.INLINE)
                            .returns(ByteArray::class)
                            .addParameter("block",LambdaTypeName.get(parameters = arrayOf(ByteArray::class.asTypeName()) , returnType = Unit::class.asTypeName()))
                            .addStatement("val data = resultBytes.copyOfRange(${protocolPropertyBean.offset},${protocolPropertyBean.offset}+${protocolPropertyBean.length})")
                            .addStatement("block(data)")
                            .addStatement("data.copyInto(resultBytes,${protocolPropertyBean.offset},0,${protocolPropertyBean.length})")
                            .addStatement("return resultBytes")
                            .build())
                    maskFunBuilder.addStatement("val res = start shl bitCount or d.toLong() shl ${protocolPropertyBean.shr} or end")
                    if(protocolPropertyBean.length == 1){
                        maskFunBuilder.addStatement("resultBytes[${protocolPropertyBean.offset}] = (res and 0xff).toByte()")
                    }else
                        for(i in 0 until protocolPropertyBean.length){
                            if(protocolPropertyBean.endian == Endian.LITTLE_ENDIAN){
                                maskFunBuilder.addStatement("resultBytes[${protocolPropertyBean.offset} + $i] = ((res shr ($i*8)) and 0xff).toByte()")
                            }else{
                                maskFunBuilder.addStatement("resultBytes[${protocolPropertyBean.offset} + $i] = ((res shr ((${protocolPropertyBean.length-1}-$i)*8)) and 0xff).toByte()")
                            }
                        }
                    typeBuilder.addFunction(maskFunBuilder.build())
                }else{
                    when(propertyElement.asType().kind){
                        TypeKind.BOOLEAN->{
                            typeBuilder
                                .addFunction(FunSpec.builder("$pName")
                                    .addModifiers(KModifier.PRIVATE)
                                    .addParameter("data",propertyElement.asType().asTypeName())
                                    .addStatement("resultBytes[${protocolPropertyBean.offset}] = if(data) 1 else 0")
                                    .build())
                        }
                        TypeKind.CHAR,TypeKind.BYTE->{
                            typeBuilder.addFunction(FunSpec.builder("$pName")
                                .addModifiers(KModifier.PRIVATE)
                                .addParameter("data",propertyElement.asType().asTypeName())
                                .addStatement("resultBytes[${protocolPropertyBean.offset}] = data.toByte()")
                                .build())
                        }
                        TypeKind.FLOAT,
                        TypeKind.DOUBLE,
                        TypeKind.SHORT,
                        TypeKind.INT,
                        TypeKind.LONG->{
                            val funBulider = FunSpec.builder("$pName")
                                .addModifiers(KModifier.PRIVATE)
                                .addParameter("data",propertyElement.asType().asTypeName())
                            if(protocolPropertyBean.multiple != 1f){
                                funBulider.addStatement("val v = (data * ${protocolPropertyBean.multiple}).toLong()")
                            }else{
                                funBulider.addStatement("val v = data.toLong()")
                            }
                            if(protocolPropertyBean.length == 1){
                                funBulider.addStatement("resultBytes[${protocolPropertyBean.offset}] = (v and 0xff).toByte()")
                            }else
                                for(i in 0 until protocolPropertyBean.length){
                                    if(protocolPropertyBean.endian == Endian.LITTLE_ENDIAN){
                                        funBulider.addStatement("resultBytes[${protocolPropertyBean.offset} + $i] = ((v shr ($i*8)) and 0xff).toByte()")
                                    }else{
                                        funBulider.addStatement("resultBytes[${protocolPropertyBean.offset} + $i] = ((v shr ((${protocolPropertyBean.length-1}-$i)*8)) and 0xff).toByte()")
                                    }
                                }

                            typeBuilder.addFunction(funBulider.build())
                        }
                        else->{
                            messager.printMessage(Diagnostic.Kind.ERROR,"${pName} 必须有默认值，或者使用了一个不支持的类型 ${propertyElement.enclosingElement.asType().kind}",protocolBean.typeElement)
                        }
                    }
                    typeBuilder.addFunction(FunSpec.builder("set${pName.toString().capitalize()}")
                        .addModifiers(KModifier.PUBLIC)
                        .returns(ByteArray::class)
                        .addParameter("data",propertyElement.asType().asTypeName())
                        .addStatement("$pName(data)")
                        .addStatement("return resultBytes")
                        .build())
                        .addFunction(FunSpec.builder("set${pName.toString().capitalize()}")
                            .addModifiers(KModifier.PUBLIC,KModifier.INLINE)
                            .returns(ByteArray::class)
                            .addParameter("block",LambdaTypeName.get(parameters = arrayOf(ByteArray::class.asTypeName()) , returnType = Unit::class.asTypeName()))
                            .addStatement("val data = resultBytes.copyOfRange(${protocolPropertyBean.offset},${protocolPropertyBean.offset}+${protocolPropertyBean.length})")
                            .addStatement("block(data)")
                            .addStatement("data.copyInto(resultBytes,${protocolPropertyBean.offset},0,${protocolPropertyBean.length})")
                            .addStatement("return resultBytes")
                            .build())
                }
            }

            typeBuilder.addInitializerBlock(initialCodeBuilder.build())

            val ktFile = ktFileBuilder.addType(typeBuilder.build())
                .build()

            ktFile.writeTo(filer)
        }
    }

//    private fun generatePropertyCode(ktFileBuilder:FileSpec.Builder,protocolPropertyBean : ProtocolPropertyBean,
//        offset: String , length:String
//    ):String{
//       return "(((bytes.copyOfRange(${
//            offset
//        },${
//            offset
//        } + ${
//            length
//        }).${
//           if(protocolPropertyBean.endian == Endian.BIG_ENDIAN){
//               ktFileBuilder.addImport("org.hf.autoprotocol.utils", "toBigLong")
//               "toBigLong()"
//           }else{
//               ktFileBuilder.addImport("org.hf.autoprotocol.utils", "toLittleLong")
//               "toLittleLong()"
//           }
//       } shr ${
//            protocolPropertyBean.shr
//        }).and(${
//            protocolPropertyBean.mask
//        }))*${
//            protocolPropertyBean.multiple
//        })"
//    }
}

