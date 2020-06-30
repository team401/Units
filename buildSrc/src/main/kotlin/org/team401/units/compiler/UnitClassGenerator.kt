package org.team401.units.compiler

import com.squareup.kotlinpoet.*
import java.io.File
import java.time.Instant
import java.util.*

/**
 * @author Cameron Earle
 * @version 2/7/2019
 *
 */
object UnitClassGenerator {
    const val INLINE_METHODS = true
    const val PACKAGE_NAME = "org.team401.units.measure"

    fun FunSpec.Builder.inlineMaybe(operator: Boolean = false, vararg otherSuppress: String): FunSpec.Builder {
        val annotationSpecBuilder = AnnotationSpec.builder(Suppress::class)
        otherSuppress.forEach {
            annotationSpecBuilder.addMember("%S", it)
        }
        if (operator) {
            addModifiers(KModifier.OPERATOR)
        }

        if (INLINE_METHODS) {
            addModifiers(KModifier.INLINE)
            annotationSpecBuilder.addMember("%S", "NOTHING_TO_INLINE")
        }

        if (annotationSpecBuilder.members.isNotEmpty()) {
            addAnnotation(annotationSpecBuilder.build())
        }

        return this
    }

    data class FactorKey(val functionName: String, val returnTypeName: String)

    fun generateClass(writeTo: File, unitDefinition: UnitDefinition, group: List<UnitDefinition>, remote: List<UnitDefinition>) {
        //Generate conversion factors
        val factors = hashMapOf<FactorKey, Double>()
        group.forEach {
            groupElement ->
            var upperFactor = 1.0
            unitDefinition.components.forEachIndexed {
                i, componentElement ->
                val groupComponentElement = groupElement.components[i]
                val componentFactor = componentElement.conversionToBase * groupComponentElement.conversionFromBase
                upperFactor *= componentFactor
            }
            val conversionName = "to${groupElement.name}"
            val className = "Measure${groupElement.name}"

            factors[FactorKey(conversionName, className)] = upperFactor
        }

        val packageName = "$PACKAGE_NAME.${unitDefinition.group}".removeSuffix(".")
        val finalClassName = "Measure${unitDefinition.name}"

        val typeBuilder = TypeSpec.classBuilder(finalClassName)
                .addModifiers(KModifier.INLINE)
                .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "EXPERIMENTAL_FEATURE_WARNING").build())
                .addKdoc("This class was automatically generated on ${Date.from(Instant.now())}")
                .primaryConstructor(
                    FunSpec.constructorBuilder().addParameter("value", Double::class).build()
                )
                .addProperty(PropertySpec.builder("value", Double::class).initializer("value").build())

        //Add toString
        typeBuilder.addFunction(
                FunSpec.builder("toString")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(String::class)
                        .addStatement("return this.value.toString() + %S", " ${unitDefinition.abbreviation}")
                        .build()
        )

        //Add unaryMinus
        typeBuilder.addFunction(
                FunSpec.builder("unaryMinus")
                        .inlineMaybe(true)
                        .returns(ClassName(packageName, finalClassName))
                        .addStatement("return $finalClassName(this.value * -1.0)")
                        .build()
        )

        if (unitDefinition.group != "") {
            //Add unitless arithmetic (this = unit, that = unitless)
            typeBuilder.addFunction(
                    FunSpec.builder("plus")
                            .inlineMaybe(true)
                            .addParameter("that", ClassName(PACKAGE_NAME, "MeasureUnitless"))
                            .returns(ClassName(packageName, finalClassName))
                            .addStatement("return $finalClassName(this.value + that.value)")
                            .build()
            )
            typeBuilder.addFunction(
                    FunSpec.builder("minus")
                            .inlineMaybe(true)
                            .addParameter("that", ClassName(PACKAGE_NAME, "MeasureUnitless"))
                            .returns(ClassName(packageName, finalClassName))
                            .addStatement("return $finalClassName(this.value - that.value)")
                            .build()
            )
            typeBuilder.addFunction(
                    FunSpec.builder("times")
                            .inlineMaybe(true)
                            .addParameter("that", ClassName(PACKAGE_NAME, "MeasureUnitless"))
                            .returns(ClassName(packageName, finalClassName))
                            .addStatement("return $finalClassName(this.value * that.value)")
                            .build()
            )
            typeBuilder.addFunction(
                    FunSpec.builder("div")
                            .inlineMaybe(true)
                            .addParameter("that", ClassName(PACKAGE_NAME, "MeasureUnitless"))
                            .returns(ClassName(packageName, finalClassName))
                            .addStatement("return $finalClassName(this.value / that.value)")
                            .build()
            )

            //Add unitless comparison functions (this = unit, that = unitless)
            typeBuilder.addFunction(
                    FunSpec.builder("compareTo")
                            .inlineMaybe(true, "CascadeIf")
                            .addParameter("that", ClassName(PACKAGE_NAME, "MeasureUnitless"))
                            .returns(Int::class)
                            .addStatement("return if (this.value > that.value) 1 else if (this.value < that.value) -1 else 0")
                            .build()
            )

            //Add same unit arithmetic
            typeBuilder.addFunction(
                FunSpec.builder("plus")
                    .inlineMaybe(true)
                    .addParameter("that", ClassName(packageName, finalClassName))
                    .returns(ClassName(packageName, finalClassName))
                    .addStatement("return $finalClassName(this.value + that.value)")
                    .build()
            )
            typeBuilder.addFunction(
                FunSpec.builder("minus")
                    .inlineMaybe(true)
                    .addParameter("that", ClassName(packageName, finalClassName))
                    .returns(ClassName(packageName, finalClassName))
                    .addStatement("return $finalClassName(this.value - that.value)")
                    .build()
            )

            //Division of same units to ratio
            typeBuilder.addFunction(
                    FunSpec.builder("div")
                            .inlineMaybe(true)
                            .addParameter("that", ClassName(packageName, finalClassName))
                            .returns(Double::class)
                            .addStatement("return (this.value / that.value)")
                            .build()
            )

            //Add same unit comparison
            typeBuilder.addFunction(
                FunSpec.builder("compareTo")
                    .inlineMaybe(true, "CascadeIf")
                    .addParameter("that", ClassName(packageName, finalClassName))
                    .returns(Int::class)
                    .addStatement("return if (this.value > that.value) 1 else if (this.value < that.value) -1 else 0")
                    .build()
            )

            //Add at least and at most
            typeBuilder.addFunction(
                    FunSpec.builder("coerceAtLeast")
                            .inlineMaybe()
                            .addParameter("that", ClassName(packageName, finalClassName))
                            .returns(ClassName(packageName, finalClassName))
                            .addStatement("return if (this.value > that.value) this else that")
                            .build()
            )

            typeBuilder.addFunction(
                    FunSpec.builder("coerceAtMost")
                            .inlineMaybe()
                            .addParameter("that", ClassName(packageName, finalClassName))
                            .returns(ClassName(packageName, finalClassName))
                            .addStatement("return if (this.value < that.value) this else that")
                            .build()
            )

            //Add absolute value and sign
            typeBuilder.addFunction(
                    FunSpec.builder("abs")
                            .inlineMaybe()
                            .returns(ClassName(packageName, finalClassName))
                            .addStatement("return $finalClassName(kotlin.math.abs(this.value))")
                            .build()
            )

            typeBuilder.addFunction(
                    FunSpec.builder("sign")
                            .inlineMaybe()
                            .returns(Double::class)
                            .addStatement("return kotlin.math.sign(this.value)")
                            .build()
            )
        }

        factors.forEach { (key, factor) ->
            //Add direct conversion functions
            typeBuilder.addFunction(
                    FunSpec.builder(key.functionName)
                            .inlineMaybe()
                            .returns(ClassName(packageName, key.returnTypeName))
                            .addStatement("return ${key.returnTypeName}(value * $factor)")
                            .build()
            )
        }
        CustomPostGenerator.accept(typeBuilder, unitDefinition, group, remote)

        val file = FileSpec.builder("$PACKAGE_NAME.${unitDefinition.group}".removeSuffix("."), finalClassName)
                .addType(typeBuilder.build())
                .build()

        file.writeTo(writeTo)
    }

    fun generateGroups(writeTo: File, vararg groups: List<UnitDefinition>) {
        val flatList = groups.toList().flatten()
        groups.forEach {
            group ->
            group.forEach {
                activeDef ->
                generateClass(writeTo, activeDef, group, flatList.filter { it.group != activeDef.group })
            }
        }
    }

    fun generateDsl(writeTo: File, vararg groups: List<UnitDefinition>) {
        val fileBuilder = FileSpec.builder(PACKAGE_NAME, "MeasureDsl")
        groups.forEach {
            group ->
            group.forEach {
                element ->
                val packageName = "$PACKAGE_NAME.${element.group}".removeSuffix(".")
                val finalClassName = "Measure${element.name}"
                //Full name DSL
                fileBuilder.addProperty(
                        PropertySpec.builder(element.name, ClassName(packageName, finalClassName))
                                .getter(
                                        FunSpec.getterBuilder()
                                                .addStatement("return $finalClassName(this)")
                                                .build()
                                )
                                .receiver(Double::class)
                                .build()
                )
                //Underscore abbreviation DSL
                fileBuilder.addProperty(
                    PropertySpec.builder('_' + element.abbreviation.replace("/", "_per_").replace(" ", "_"), ClassName(packageName, finalClassName))
                                .getter(
                                    FunSpec.getterBuilder()
                                        .addStatement("return $finalClassName(this)")
                                        .build()
                                )
                                .receiver(Double::class)
                                .build()
                )
            }
        }

        fileBuilder.build().writeTo(writeTo)
    }
}