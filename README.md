# SnakeSkin-Units
SnakeSkin-Units provides statically compiled units of measure for use in SnakeSkin projects. SnakeSkin-Units leverages Kotlin inline classes to prevent boxing and avoid heap allocation. All unit conversion class files are generated using KotlinPoet, adding a new unit is as easy as specifying it's measure type, name, plural name, and conversion factor in GenerateUnitsClasses.kt.
