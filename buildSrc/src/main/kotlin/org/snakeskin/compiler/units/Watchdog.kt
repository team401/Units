package org.snakeskin.compiler.units

/**
 * @author Cameron Earle
 * @version 2/9/2019
 *
 */
object Watchdog: Runnable {
    override fun run() {
        println("I'm running!")
    }
}