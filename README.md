# Units

[![Download](https://api.bintray.com/packages/team401/SnakeSkin/SnakeSkin-Units/images/download.svg) ](https://bintray.com/team401/SnakeSkin/SnakeSkin-Units/_latestVersion)
[![Travis](https://img.shields.io/travis/team401/SnakeSkin-Units.svg)](https://travis-ci.org/team401/SnakeSkin-Units)
[![Language](https://img.shields.io/github/languages/top/team401/SnakeSkin-Units.svg)](https://github.com/team401/SnakeSkin-Units) 
[![license](https://img.shields.io/github/license/team401/SnakeSkin-Units.svg)](https://github.com/team401/SnakeSkin-Units/blob/master/LICENSE)

Units provides statically compiled units of measure for use in projects. Units leverages [Kotlin inline classes](https://kotlinlang.org/docs/reference/inline-classes.html) to prevent boxing and avoid heap allocation. All unit conversion class files are generated using KotlinPoet, adding a new unit is as easy as specifying it's measure type, name, plural name, and conversion factor in [GenerateUnitsClasses.kt](buildSrc/src/main/kotlin/org/snakeskin/compiler/units/GenerateUnitsClasses.kt).
