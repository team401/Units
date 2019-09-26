package org.snakeskin.compiler.units

import com.squareup.kotlinpoet.*
import org.snakeskin.compiler.units.UnitClassGenerator.inlineMaybe

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
                                    .inlineMaybe()
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(ClassName(unitPackageName, unitClassName))
                                    .addStatement("return $unitClassName(this.value + that.value)")
                                    .build()
                    )
                    type.addFunction(
                            FunSpec.builder("minus")
                                    .inlineMaybe()
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(ClassName(unitPackageName, unitClassName))
                                    .addStatement("return $unitClassName(this.value - that.value)")
                                    .build()
                    )
                    type.addFunction(
                            FunSpec.builder("times")
                                    .inlineMaybe()
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(ClassName(unitPackageName, unitClassName))
                                    .addStatement("return $unitClassName(this.value * that.value)")
                                    .build()
                    )
                    type.addFunction(
                            FunSpec.builder("div")
                                    .inlineMaybe()
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(ClassName(unitPackageName, unitClassName))
                                    .addStatement("return $unitClassName(this.value / that.value)")
                                    .build()
                    )

                    type.addFunction(
                            FunSpec.builder("compareTo")
                                    .inlineMaybe()
                                    .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "CascadeIf").build())
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(Int::class)
                                    .addStatement("return if (this.value > that.value) 1 else if (this.value < that.value) -1 else 0")
                                    .build()
                    )
                }
            }
        }

        //Generate angular to linear conversions

        //Generate angular -> linear distance
        //Convert "this" to radians, keep linear
        //Return linear unit
        if (currentElement.group == "distance.angular") {
            val radiusMeasureGroup = getGroup(otherRemoteElements, "distance.linear") //Group of possible units for the input radius
            val radiusMeasureGroupPackage = getPackage(radiusMeasureGroup.first()) //The package that contains the radius types
            radiusMeasureGroup.forEach {
                radiusUnit ->
                val radiusUnitClass = getMeasureClassName(radiusUnit)
                type.addFunction(
                        FunSpec.builder("toLinearDistance")
                                .inlineMaybe()
                                .addParameter("radius", ClassName(radiusMeasureGroupPackage, radiusUnitClass))
                                .returns(ClassName(radiusMeasureGroupPackage, radiusUnitClass))
                                .addStatement("return $radiusUnitClass(this.toRadians().value * radius.value)")
                                .build()
                )
            }
        }

        //Generate angular -> linear velocity
        //Convert "this" to radians per this time unit, keep linear
        //Return linear unit per time unit
        if (currentElement.group == "velocity.angular") {
            val radiusMeasureGroup = getGroup(otherRemoteElements, "distance.linear") //Group of possible units for the input radius
            val linearVelocityGroup = getGroup(otherRemoteElements, "velocity.linear") //Group of possible linear velocity types
            val radiusMeasureGroupPackage = getPackage(radiusMeasureGroup.first()) //The package that contains the radius types
            val linearVelocityGroupPackage = getPackage(linearVelocityGroup.first()) //The package that contains the linear velocity types

            val angularMeasureTimeComponentName = currentElement.components[1].name
            radiusMeasureGroup.forEach { //Generate a conversion for each type of input radius
                radiusUnit ->
                val radiusUnitClass = getMeasureClassName(radiusUnit) //Get the class name of this radius unit
                //Get the linear velocity unit whose time component matches ours, and whose distance component matches the radius unit
                val linearVelocityUnit = linearVelocityGroup.first {
                    it.components[1].name == angularMeasureTimeComponentName
                            && it.components[0].name == radiusUnit.components[0].name
                }
                val linearVelocityUnitClass = getMeasureClassName(linearVelocityUnit)
                type.addFunction(
                        FunSpec.builder("toLinearVelocity")
                                .inlineMaybe()
                                .addParameter("radius", ClassName(radiusMeasureGroupPackage, radiusUnitClass))
                                .returns(ClassName(linearVelocityGroupPackage, linearVelocityUnitClass))
                                .addStatement("return $linearVelocityUnitClass(this.toRadiansPer$angularMeasureTimeComponentName().value * radius.value)")
                                .build()
                )
            }
        }

        //Generate angular -> linear acceleration
        //Convert "this" to radians per this time unit 1 per this time 2 unit, keep linear
        //Return linear unit per time unit 1 per time unit 2
        if (currentElement.group == "acceleration.angular") {
            val radiusMeasureGroup = getGroup(otherRemoteElements, "distance.linear") //Group of possible units for the input radius
            val linearAccelerationGroup = getGroup(otherRemoteElements, "acceleration.linear")
            val radiusMeasureGroupPackage = getPackage(radiusMeasureGroup.first())
            val linearAccelerationGroupPackage = getPackage(linearAccelerationGroup.first())

            val angularMeasureTimeComponent1Name = currentElement.components[1].name
            val angularMeasureTimeComponent2Name = currentElement.components[2].name
            radiusMeasureGroup.forEach { //Generate a conversion for each type of input radius
                radiusUnit ->
                val radiusUnitClass = getMeasureClassName(radiusUnit) //Get the class name of this radius unit
                //Get the linear acceleration unit whose time components match ours, and whose distance component matches the radius unit
                val linearAccelerationUnit = linearAccelerationGroup.first {
                    it.components[1].name == angularMeasureTimeComponent1Name
                            && it.components[2].name == angularMeasureTimeComponent2Name
                            && it.components[0].name == radiusUnit.components[0].name
                }
                val linearAccelerationUnitClass = getMeasureClassName(linearAccelerationUnit)
                type.addFunction(
                    FunSpec.builder("toLinearAcceleration")
                        .inlineMaybe()
                        .addParameter("radius", ClassName(radiusMeasureGroupPackage, radiusUnitClass))
                        .returns(ClassName(linearAccelerationGroupPackage, linearAccelerationUnitClass))
                        .addStatement("return $linearAccelerationUnitClass(this.toRadiansPer${angularMeasureTimeComponent1Name}Per$angularMeasureTimeComponent2Name().value * radius.value)")
                        .build()
                )
            }
        }

        //Generate linear to angular conversions

        //Generate linear -> angular distance
        //Convert this to the radius unit
        //Return radians
        if (currentElement.group == "distance.linear") {
            val radiusGroupPackage = getPackage(otherLocalElements.first())
            val angularGroup = getGroup(otherRemoteElements, "distance.angular")
            val angularGroupPackage = getPackage(angularGroup.first())
            val angularElementClass = "AngularDistanceMeasureRadians"
            otherLocalElements.forEach { //Other local elements == radius elements
                    radiusUnit ->
                val radiusUnitClass = getMeasureClassName(radiusUnit)
                type.addFunction(
                    FunSpec.builder("toAngularDistance")
                        .inlineMaybe()
                        .addParameter("radius", ClassName(radiusGroupPackage, radiusUnitClass))
                        .returns(ClassName(angularGroupPackage, angularElementClass))
                        .addStatement("return $angularElementClass(this.to${radiusUnit.name}().value / radius.value)")
                        .build()
                )
            }
        }

        //Generate linear -> angular velocity
        //Convert this to the radius unit per this time unit
        //Return radians per this unit's time component
        if (currentElement.group == "velocity.linear") {
            val radiusMeasureGroup = getGroup(otherRemoteElements, "distance.linear") //Group of possible units for the input radius
            val angularVelocityGroup = getGroup(otherRemoteElements, "velocity.angular") //Group of possible angular velocities
            val radiusMeasureGroupPackage = getPackage(radiusMeasureGroup.first())
            val angularVelocityGroupPackage = getPackage(angularVelocityGroup.first())

            val linearMeasureTimeComponentName = currentElement.components[1].name
            val angularVelocityUnitClass = "AngularVelocityMeasureRadiansPer$linearMeasureTimeComponentName"

            radiusMeasureGroup.forEach {
                radiusUnit ->
                val radiusUnitClass = getMeasureClassName(radiusUnit)
                type.addFunction(
                        FunSpec.builder("toAngularVelocity")
                                .inlineMaybe()
                                .addParameter("radius", ClassName(radiusMeasureGroupPackage, radiusUnitClass))
                                .returns(ClassName(angularVelocityGroupPackage, angularVelocityUnitClass))
                                .addStatement("return $angularVelocityUnitClass(this.to${radiusUnit.name}Per$linearMeasureTimeComponentName().value / radius.value)")
                                .build()
                )
            }
        }

        //Generate linear -> angular acceleration
        //Convert this to the radius unit per this time unit 1 per this time unit 2
        //Return radians per this unit's time component 1 per this unit's time component 2
        if (currentElement.group == "acceleration.linear") {
            val radiusMeasureGroup = getGroup(otherRemoteElements, "distance.linear") //Group of possible units for the input radius
            val angularAccelerationGroup = getGroup(otherRemoteElements, "acceleration.angular") //Group of possible angular velocities
            val radiusMeasureGroupPackage = getPackage(radiusMeasureGroup.first())
            val angularAccelerationGroupPackage = getPackage(angularAccelerationGroup.first())

            val linearMeasureTimeComponent1Name = currentElement.components[1].name
            val linearMeasureTimeComponent2Name = currentElement.components[2].name
            val angularAccelerationUnitClass = "AngularAccelerationMeasureRadiansPer${linearMeasureTimeComponent1Name}Per$linearMeasureTimeComponent2Name"

            radiusMeasureGroup.forEach {
                    radiusUnit ->
                val radiusUnitClass = getMeasureClassName(radiusUnit)
                type.addFunction(
                    FunSpec.builder("toAngularAcceleration")
                        .inlineMaybe()
                        .addParameter("radius", ClassName(radiusMeasureGroupPackage, radiusUnitClass))
                        .returns(ClassName(angularAccelerationGroupPackage, angularAccelerationUnitClass))
                        .addStatement("return $angularAccelerationUnitClass(this.to${radiusUnit.name}Per${linearMeasureTimeComponent1Name}Per${linearMeasureTimeComponent2Name}().value / radius.value)")
                        .build()
                )
            }
        }
    }
}