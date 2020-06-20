package org.team401.units.compiler

import com.squareup.kotlinpoet.*
import org.team401.units.compiler.UnitClassGenerator.inlineMaybe

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

    private fun getPackage(def: UnitDefinition): String {
        return "${UnitClassGenerator.PACKAGE_NAME}.${def.group}".removeSuffix(".")
    }

    private fun getMeasureClassName(def: UnitDefinition): String {
        return "Measure${def.name}"
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
                                    .inlineMaybe(true)
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(ClassName(unitPackageName, unitClassName))
                                    .addStatement("return $unitClassName(this.value + that.value)")
                                    .build()
                    )
                    type.addFunction(
                            FunSpec.builder("minus")
                                    .inlineMaybe(true)
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(ClassName(unitPackageName, unitClassName))
                                    .addStatement("return $unitClassName(this.value - that.value)")
                                    .build()
                    )
                    type.addFunction(
                            FunSpec.builder("times")
                                    .inlineMaybe(true)
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(ClassName(unitPackageName, unitClassName))
                                    .addStatement("return $unitClassName(this.value * that.value)")
                                    .build()
                    )
                    type.addFunction(
                            FunSpec.builder("div")
                                    .inlineMaybe(true)
                                    .addParameter("that", ClassName(unitPackageName, unitClassName))
                                    .returns(ClassName(unitPackageName, unitClassName))
                                    .addStatement("return $unitClassName(this.value / that.value)")
                                    .build()
                    )

                    type.addFunction(
                            FunSpec.builder("compareTo")
                                    .inlineMaybe(true, "CascadeIf")
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
            val angularElementClass = "MeasureRadians"
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
            val angularVelocityUnitClass = "MeasureRadiansPer$linearMeasureTimeComponentName"

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
            val angularAccelerationUnitClass = "MeasureRadiansPer${linearMeasureTimeComponent1Name}Per$linearMeasureTimeComponent2Name"

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

        //Generate linear velocity -> position ( vel * time )
        //Multiply value by time and then strip the time unit off, returning the corresponding position unit
        if (currentElement.group == "velocity.linear") {
            val linearDistanceGroup = getGroup(otherRemoteElements, "distance.linear")
            val linearDistanceGroupPackage = getPackage(linearDistanceGroup.first())

            val timeGroup = getGroup(otherRemoteElements, "time")
            val timeGroupPackage = getPackage(timeGroup.first())

            val linearVelocityDistanceComponentName = currentElement.components[0].pluralName
            val linearVelocityTimeComponentName = currentElement.components[1].pluralName
            val linearDistanceUnitClass = "Measure$linearVelocityDistanceComponentName"
            val timeUnitClass = "Measure$linearVelocityTimeComponentName"

            type.addFunction(
                    FunSpec.builder("times")
                            .inlineMaybe(true)
                            .addParameter("that", ClassName(timeGroupPackage, timeUnitClass))
                            .returns(ClassName(linearDistanceGroupPackage, linearDistanceUnitClass))
                            .addStatement("return $linearDistanceUnitClass(this.value * that.value)")
                            .build()
            )
        }

        //Generate linear velocity -> position ( time * vel )
        //Multiply value by time and then strip the time unit off, returning the corresponding position unit
        if (currentElement.group == "time") {
            val linearDistanceGroup = getGroup(otherRemoteElements, "distance.linear")
            val linearDistanceGroupPackage = getPackage(linearDistanceGroup.first())

            val linearVelocityGroup = getGroup(otherRemoteElements, "velocity.linear")
            val linearVelocityGroupPackage = getPackage(linearVelocityGroup.first())

            linearVelocityGroup.forEach {
                linearVelocityUnit ->
                val linearVelocityDistanceComponentName = linearVelocityUnit.components[0].pluralName
                val linearVelocityTimeComponentName = linearVelocityUnit.components[1].pluralName
                if (linearVelocityTimeComponentName == currentElement.components[0].pluralName) {
                    val linearDistanceUnitClass = "Measure$linearVelocityDistanceComponentName"
                    val linearVelocityUnitClass = getMeasureClassName(linearVelocityUnit)
                    type.addFunction(
                            FunSpec.builder("times")
                                    .inlineMaybe(true)
                                    .addParameter("that", ClassName(linearVelocityGroupPackage, linearVelocityUnitClass))
                                    .returns(ClassName(linearDistanceGroupPackage, linearDistanceUnitClass))
                                    .addStatement("return $linearDistanceUnitClass(this.value * that.value)")
                                    .build()
                    )
                }
            }
        }

        //Generate angular velocity -> position ( vel * time )
        //Multiply value by time and then strip the time unit off, returning the corresponding position unit
        if (currentElement.group == "velocity.angular") {
            val angularDistanceGroup = getGroup(otherRemoteElements, "distance.angular")
            val angularDistanceGroupPackage = getPackage(angularDistanceGroup.first())

            val timeGroup = getGroup(otherRemoteElements, "time")
            val timeGroupPackage = getPackage(timeGroup.first())

            val angularVelocityDistanceComponentName = currentElement.components[0].pluralName
            val angularVelocityTimeComponentName = currentElement.components[1].pluralName
            val angularDistanceUnitClass = "Measure$angularVelocityDistanceComponentName"
            val timeUnitClass = "Measure$angularVelocityTimeComponentName"

            type.addFunction(
                    FunSpec.builder("times")
                            .inlineMaybe(true)
                            .addParameter("that", ClassName(timeGroupPackage, timeUnitClass))
                            .returns(ClassName(angularDistanceGroupPackage, angularDistanceUnitClass))
                            .addStatement("return $angularDistanceUnitClass(this.value * that.value)")
                            .build()
            )
        }

        //Generate angular velocity -> position ( time * vel )
        //Multiply value by time and then strip the time unit off, returning the corresponding position unit
        if (currentElement.group == "time") {
            val angularDistanceGroup = getGroup(otherRemoteElements, "distance.angular")
            val angularDistanceGroupPackage = getPackage(angularDistanceGroup.first())

            val angularVelocityGroup = getGroup(otherRemoteElements, "velocity.angular")
            val angularVelocityGroupPackage = getPackage(angularVelocityGroup.first())

            angularVelocityGroup.forEach {
                angularVelocityUnit ->
                val angularVelocityDistanceComponentName = angularVelocityUnit.components[0].pluralName
                val angularVelocityTimeComponentName = angularVelocityUnit.components[1].pluralName
                if (angularVelocityTimeComponentName == currentElement.components[0].pluralName) {
                    val angularDistanceUnitClass = "Measure$angularVelocityDistanceComponentName"
                    val angularVelocityUnitClass = getMeasureClassName(angularVelocityUnit)
                    type.addFunction(
                            FunSpec.builder("times")
                                    .inlineMaybe(true)
                                    .addParameter("that", ClassName(angularVelocityGroupPackage, angularVelocityUnitClass))
                                    .returns(ClassName(angularDistanceGroupPackage, angularDistanceUnitClass))
                                    .addStatement("return $angularDistanceUnitClass(this.value * that.value)")
                                    .build()
                    )
                }
            }
        }

        //Generate linear acceleration -> velocity ( accel * time )
        //Multiply value by time and then strip the time unit off, returning the corresponding position unit
        if (currentElement.group == "acceleration.linear") {
            val linearVelocityGroup = getGroup(otherRemoteElements, "velocity.linear")
            val linearVelocityGroupPackage = getPackage(linearVelocityGroup.first())

            val timeGroup = getGroup(otherRemoteElements, "time")
            val timeGroupPackage = getPackage(timeGroup.first())

            val linearAccelerationDistanceComponentName = currentElement.components[0].pluralName
            val linearAccelerationTime1ComponentName = currentElement.components[1].name
            val linearAccelerationTime2ComponentName = currentElement.components[2].pluralName

            val linearVelocityUnitClass = "Measure${linearAccelerationDistanceComponentName}Per$linearAccelerationTime1ComponentName"
            val timeUnitClass = "Measure$linearAccelerationTime2ComponentName"

            type.addFunction(
                    FunSpec.builder("times")
                            .inlineMaybe(true)
                            .addParameter("that", ClassName(timeGroupPackage, timeUnitClass))
                            .returns(ClassName(linearVelocityGroupPackage, linearVelocityUnitClass))
                            .addStatement("return $linearVelocityUnitClass(this.value * that.value)")
                            .build()
            )
        }

        //Generate linear acceleration -> velocity ( time * accel )
        //Multiply value by time and then strip the time unit off, returning the corresponding position unit
        if (currentElement.group == "time") {
            val linearVelocityGroup = getGroup(otherRemoteElements, "velocity.linear")
            val linearVelocityGroupPackage = getPackage(linearVelocityGroup.first())

            val linearAccelerationGroup = getGroup(otherRemoteElements, "acceleration.linear")
            val linearAccelerationGroupPackage = getPackage(linearAccelerationGroup.first())
            
            linearAccelerationGroup.forEach {
                linearAccelerationUnit ->
                val linearAccelerationDistanceComponentName = linearAccelerationUnit.components[0].pluralName
                val linearAccelerationTime1ComponentName = linearAccelerationUnit.components[1].name
                val linearAccelerationTime2ComponentName = linearAccelerationUnit.components[2].name
                
                if (linearAccelerationTime2ComponentName == currentElement.components[0].name) {
                    val linearVelocityUnitClass = "Measure${linearAccelerationDistanceComponentName}Per$linearAccelerationTime1ComponentName"
                    val linearAccelerationUnitClass = getMeasureClassName(linearAccelerationUnit)
                    type.addFunction(
                            FunSpec.builder("times")
                                    .inlineMaybe(true)
                                    .addParameter("that", ClassName(linearAccelerationGroupPackage, linearAccelerationUnitClass))
                                    .returns(ClassName(linearVelocityGroupPackage, linearVelocityUnitClass))
                                    .addStatement("return $linearVelocityUnitClass(this.value * that.value)")
                                    .build()
                    )
                }
            }
        }

        //Generate angular acceleration -> velocity ( accel * time )
        //Multiply value by time and then strip the time unit off, returning the corresponding position unit
        if (currentElement.group == "acceleration.angular") {
            val angularVelocityGroup = getGroup(otherRemoteElements, "velocity.angular")
            val angularVelocityGroupPackage = getPackage(angularVelocityGroup.first())

            val timeGroup = getGroup(otherRemoteElements, "time")
            val timeGroupPackage = getPackage(timeGroup.first())

            val angularAccelerationDistanceComponentName = currentElement.components[0].pluralName
            val angularAccelerationTime1ComponentName = currentElement.components[1].name
            val angularAccelerationTime2ComponentName = currentElement.components[2].pluralName

            val angularVelocityUnitClass = "Measure${angularAccelerationDistanceComponentName}Per$angularAccelerationTime1ComponentName"
            val timeUnitClass = "Measure$angularAccelerationTime2ComponentName"

            type.addFunction(
                    FunSpec.builder("times")
                            .inlineMaybe(true)
                            .addParameter("that", ClassName(timeGroupPackage, timeUnitClass))
                            .returns(ClassName(angularVelocityGroupPackage, angularVelocityUnitClass))
                            .addStatement("return $angularVelocityUnitClass(this.value * that.value)")
                            .build()
            )
        }

        //Generate angular acceleration -> velocity ( time * accel )
        //Multiply value by time and then strip the time unit off, returning the corresponding position unit
        if (currentElement.group == "time") {
            val angularVelocityGroup = getGroup(otherRemoteElements, "velocity.angular")
            val angularVelocityGroupPackage = getPackage(angularVelocityGroup.first())

            val angularAccelerationGroup = getGroup(otherRemoteElements, "acceleration.angular")
            val angularAccelerationGroupPackage = getPackage(angularAccelerationGroup.first())

            angularAccelerationGroup.forEach {
                angularAccelerationUnit ->
                val angularAccelerationDistanceComponentName = angularAccelerationUnit.components[0].pluralName
                val angularAccelerationTime1ComponentName = angularAccelerationUnit.components[1].name
                val angularAccelerationTime2ComponentName = angularAccelerationUnit.components[2].name

                if (angularAccelerationTime2ComponentName == currentElement.components[0].name) {
                    val angularVelocityUnitClass = "Measure${angularAccelerationDistanceComponentName}Per$angularAccelerationTime1ComponentName"
                    val angularAccelerationUnitClass = getMeasureClassName(angularAccelerationUnit)
                    type.addFunction(
                            FunSpec.builder("times")
                                    .inlineMaybe(true)
                                    .addParameter("that", ClassName(angularAccelerationGroupPackage, angularAccelerationUnitClass))
                                    .returns(ClassName(angularVelocityGroupPackage, angularVelocityUnitClass))
                                    .addStatement("return $angularVelocityUnitClass(this.value * that.value)")
                                    .build()
                    )
                }
            }
        }

        //Generate linear distance -> velocity
        if (currentElement.group == "distance.linear") {
            val linearVelocityGroup = getGroup(otherRemoteElements, "velocity.linear")
            val linearVelocityGroupPackage = getPackage(linearVelocityGroup.first())

            val linearDistanceComponentName = currentElement.components[0].pluralName

            val timeGroup = getGroup(otherRemoteElements, "time")
            val timeGroupPackage = getPackage(timeGroup.first())

            timeGroup.forEach {
                timeUnit ->
                val timeComponentName = timeUnit.components[0].name
                val timeUnitClass = getMeasureClassName(timeUnit)
                val linearVelocityUnitClass = "Measure${linearDistanceComponentName}Per$timeComponentName"
                type.addFunction(
                        FunSpec.builder("div")
                                .inlineMaybe(true)
                                .addParameter("that", ClassName(timeGroupPackage, timeUnitClass))
                                .returns(ClassName(linearVelocityGroupPackage, linearVelocityUnitClass))
                                .addStatement("return $linearVelocityUnitClass(this.value / that.value)")
                                .build()
                )
            }
        }

        //Generate angular distance -> velocity
        if (currentElement.group == "distance.angular") {
            val angularVelocityGroup = getGroup(otherRemoteElements, "velocity.angular")
            val angularVelocityGroupPackage = getPackage(angularVelocityGroup.first())

            val angularDistanceComponentName = currentElement.components[0].pluralName

            val timeGroup = getGroup(otherRemoteElements, "time")
            val timeGroupPackage = getPackage(timeGroup.first())

            timeGroup.forEach {
                timeUnit ->
                val timeComponentName = timeUnit.components[0].name
                val timeUnitClass = getMeasureClassName(timeUnit)
                val angularVelocityUnitClass = "Measure${angularDistanceComponentName}Per$timeComponentName"
                type.addFunction(
                        FunSpec.builder("div")
                                .inlineMaybe(true)
                                .addParameter("that", ClassName(timeGroupPackage, timeUnitClass))
                                .returns(ClassName(angularVelocityGroupPackage, angularVelocityUnitClass))
                                .addStatement("return $angularVelocityUnitClass(this.value / that.value)")
                                .build()
                )
            }
        }

        //Generate linear velocity -> acceleration
        if (currentElement.group == "velocity.linear") {
            val linearAccelerationGroup = getGroup(otherRemoteElements, "acceleration.linear")
            val linearAccelerationGroupPackage = getPackage(linearAccelerationGroup.first())

            val linearVelocityDistanceComponentName = currentElement.components[0].pluralName
            val linearVelocityTimeComponentName = currentElement.components[1].name

            val timeGroup = getGroup(otherRemoteElements, "time")
            val timeGroupPackage = getPackage(timeGroup.first())

            timeGroup.forEach {
                timeUnit ->
                val timeComponentName = timeUnit.components[0].name
                val timeUnitClass = getMeasureClassName(timeUnit)
                val linearAccelerationUnitClass = "Measure${linearVelocityDistanceComponentName}Per${linearVelocityTimeComponentName}Per${timeComponentName}"
                type.addFunction(
                        FunSpec.builder("div")
                                .inlineMaybe(true)
                                .addParameter("that", ClassName(timeGroupPackage, timeUnitClass))
                                .returns(ClassName(linearAccelerationGroupPackage, linearAccelerationUnitClass))
                                .addStatement("return $linearAccelerationUnitClass(this.value / that.value)")
                                .build()
                )
            }
        }

        //Generate angular velocity -> acceleration
        if (currentElement.group == "velocity.angular") {
            val angularAccelerationGroup = getGroup(otherRemoteElements, "acceleration.angular")
            val angularAccelerationGroupPackage = getPackage(angularAccelerationGroup.first())

            val angularVelocityDistanceComponentName = currentElement.components[0].pluralName
            val angularVelocityTimeComponentName = currentElement.components[1].name

            val timeGroup = getGroup(otherRemoteElements, "time")
            val timeGroupPackage = getPackage(timeGroup.first())

            timeGroup.forEach {
                timeUnit ->
                val timeComponentName = timeUnit.components[0].name
                val timeUnitClass = getMeasureClassName(timeUnit)
                val angularAccelerationUnitClass = "Measure${angularVelocityDistanceComponentName}Per${angularVelocityTimeComponentName}Per${timeComponentName}"
                type.addFunction(
                        FunSpec.builder("div")
                                .inlineMaybe(true)
                                .addParameter("that", ClassName(timeGroupPackage, timeUnitClass))
                                .returns(ClassName(angularAccelerationGroupPackage, angularAccelerationUnitClass))
                                .addStatement("return $angularAccelerationUnitClass(this.value / that.value)")
                                .build()
                )
            }
        }
    }
}