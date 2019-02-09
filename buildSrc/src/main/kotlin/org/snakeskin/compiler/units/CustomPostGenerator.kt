package org.snakeskin.compiler.units

import com.squareup.kotlinpoet.*

/**
 * @author Cameron Earle
 * @version 2/8/2019
 *
 * Generates any post processing behaviors for a unit.  This includes:
 * - Conversions from angular to linear units given a radius
 */
object CustomPostGenerator {
    private fun getGroup(all: List<UnitDefinition>, group: String): List<UnitDefinition> {
        val toReturn = arrayListOf<UnitDefinition>()
        all.forEach {
            if (it.group == group) {
                toReturn.add(it)
            }
        }
        return toReturn
    }

    private fun getUnitFromFirstComponent(group: List<UnitDefinition>, def: UnitDefinition): UnitDefinition {
        return group.first { it.components.first().name == def.components.first().name }
    }

    private fun getPackage(def: UnitDefinition): String {
        return "org.snakeskin.measure.${def.group}".removeSuffix(".")
    }

    private fun getMeasureClassName(def: UnitDefinition): String {
        return def.group.split(".").reversed().joinToString("", transform = { it.capitalize() }) + "Measure${def.name}"
    }

    private fun getConversionToFunction(def: UnitDefinition): String {
        return "to${def.name}"
    }


    fun accept(type: TypeSpec.Builder, currentElement: UnitDefinition, otherLocalElements: List<UnitDefinition>, otherRemoteElements: List<UnitDefinition>) {
        //Generate unitless arithmetic (this = unitless, that = unit)
        if (currentElement.group == "") {
            otherRemoteElements.forEach {
                unit ->
                if (unit.group != "") {
                    val unitPackageName = getPackage(unit)
                    val unitClassName = getMeasureClassName(unit)
                    type.addFunction(
                            FunSpec.builder("plus")
                                    .addModifiers(KModifier.INLINE, KModifier.OPERATOR)
                                    .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "NOTHING_TO_INLINE").build())
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(ClassName(unitPackageName, unitClassName))
                                    .addStatement("return $unitClassName(this.value + that.value)")
                                    .build()
                    )
                    type.addFunction(
                            FunSpec.builder("minus")
                                    .addModifiers(KModifier.INLINE, KModifier.OPERATOR)
                                    .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "NOTHING_TO_INLINE").build())
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(ClassName(unitPackageName, unitClassName))
                                    .addStatement("return $unitClassName(this.value - that.value)")
                                    .build()
                    )
                    type.addFunction(
                            FunSpec.builder("times")
                                    .addModifiers(KModifier.INLINE, KModifier.OPERATOR)
                                    .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "NOTHING_TO_INLINE").build())
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(ClassName(unitPackageName, unitClassName))
                                    .addStatement("return $unitClassName(this.value * that.value)")
                                    .build()
                    )
                    type.addFunction(
                            FunSpec.builder("div")
                                    .addModifiers(KModifier.INLINE, KModifier.OPERATOR)
                                    .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "NOTHING_TO_INLINE").build())
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(ClassName(unitPackageName, unitClassName))
                                    .addStatement("return $unitClassName(this.value / that.value)")
                                    .build()
                    )

                    type.addFunction(
                            FunSpec.builder("compareTo")
                                    .addModifiers(KModifier.INLINE, KModifier.OPERATOR)
                                    .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "NOTHING_TO_INLINE").addMember("%S", "CascadeIf").build())
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(Int::class)
                                    .addStatement("return if (this.value > that.value) 1 else if (this.value < that.value) -1 else 0")
                                    .build()
                    )
                }
            }
        }

        //Generate angular -> linear
        //Convert "this" to radians, keep linear
        //Return linear unit
        if (currentElement.group == "distance.angular") {
            val linearGroup = getGroup(otherRemoteElements, "distance.linear")
            val linearGroupPackage = getPackage(linearGroup.first())
            linearGroup.forEach {
                linearElement ->
                val linearElementClass = getMeasureClassName(linearElement)
                type.addFunction(
                        FunSpec.builder("toLinearDistance")
                                .addModifiers(KModifier.INLINE)
                                .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "NOTHING_TO_INLINE").build())
                                .addParameter("radius", ClassName(linearGroupPackage, linearElementClass))
                                .returns(ClassName(linearGroupPackage, linearElementClass))
                                .addStatement("return $linearElementClass(this.toRadians().value * radius.value)")
                                .build()
                )
            }
        }

        //Generate linear -> angular
        //Convert the radius to this unit
        //Return radians
        if (currentElement.group == "distance.linear") {
            val linearGroupPackage = getPackage(otherLocalElements.first())
            val angularGroup = getGroup(otherRemoteElements, "distance.angular")
            val angularGroupPackage = getPackage(angularGroup.first())
            val angularElementClass = "AngularDistanceMeasureRadians"
            val conversionToThis = getConversionToFunction(currentElement)
            otherLocalElements.forEach {
                linearElement ->
                val linearElementClass = getMeasureClassName(linearElement)
                type.addFunction(
                        FunSpec.builder("toAngularDistance")
                                .addModifiers(KModifier.INLINE)
                                .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "NOTHING_TO_INLINE").build())
                                .addParameter("radius", ClassName(linearGroupPackage, linearElementClass))
                                .returns(ClassName(angularGroupPackage, angularElementClass))
                                .addStatement("return $angularElementClass(this.value / radius.$conversionToThis().value)")
                                .build()
                )
            }
        }

        //Generate angular -> linear velocity
        //Convert "this" to radians per linear time unit, keep linear
        //Return linear unit per time unit
        if (currentElement.group == "velocity.angular") {
            val linearGroup = getGroup(otherRemoteElements, "distance.linear")
            val linearDerivativeGroup = getGroup(otherRemoteElements, "velocity.linear")
            val linearGroupPackage = getPackage(linearGroup.first())
            val linearDerivativeGroupPackage = getPackage(linearDerivativeGroup.first())
            linearGroup.forEach {
                linearElement ->
                val linearElementClass = getMeasureClassName(linearElement)
                val linearDerivativeElement = linearDerivativeGroup.first { it.components[1].name == currentElement.components[1].name && it.components[0].name == linearElement.components[0].name}
                val linearDerivativeElementClass = getMeasureClassName(linearDerivativeElement)
                type.addFunction(
                        FunSpec.builder("toLinearVelocity")
                                .addModifiers(KModifier.INLINE)
                                .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "NOTHING_TO_INLINE").build())
                                .addParameter("radius", ClassName(linearGroupPackage, linearElementClass))
                                .returns(ClassName(linearDerivativeGroupPackage, linearDerivativeElementClass))
                                .addStatement("return $linearDerivativeElementClass(this.toRadiansPer${linearDerivativeElement.components[1].name}().value * radius.value)")
                                .build()
                )
            }
        }

        //Generate linear -> angular velocity
        //Convert the radius to this unit's first component's unit
        //Return radians per this unit's second component's unit (angular velocity)
        if (currentElement.group == "velocity.linear") {
            val angularGroup = getGroup(otherRemoteElements, "distance.angular")
            val angularDerivativeGroup = getGroup(otherRemoteElements, "velocity.angular")
            val angularGroupPackage = getPackage(angularGroup.first())
            val angularDerivativeGroupPackage = getPackage(angularDerivativeGroup.first())
            val angularDerivativeElementClassBase = "AngularVelocityMeasureRadiansPer" //append on second component from this unit
            val angularDerivativeElementClass = angularDerivativeElementClassBase + currentElement.components[1].name

            val linearGroup = getGroup(otherRemoteElements, "distance.linear")
            val linearGroupPackage = getPackage(linearGroup.first())
            val currentLinearIntegral = getUnitFromFirstComponent(linearGroup, currentElement)

            linearGroup.forEach {
                linearElement ->
                val linearElementClass = getMeasureClassName(linearElement)
                val conversionToThis = getConversionToFunction(currentLinearIntegral)
                type.addFunction(
                        FunSpec.builder("toAngularVelocity")
                                .addModifiers(KModifier.INLINE)
                                .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "NOTHING_TO_INLINE").build())
                                .addParameter("radius", ClassName(linearGroupPackage, linearElementClass))
                                .returns(ClassName(angularDerivativeGroupPackage, angularDerivativeElementClass))
                                .addStatement("return $angularDerivativeElementClass(this.value / radius.$conversionToThis().value)")
                                .build()
                )
            }
        }
    }
}