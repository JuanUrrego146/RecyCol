package com.recycol.android.camera

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Criterio de hecho de S12: una mancha simulada fija se detecta como suciedad
 * y un objeto estático de la escena no genera falso positivo.
 *
 * Las secuencias simulan el movimiento de cámara desplazando la escena bajo un
 * lente fijo; la mancha se aplica después del desplazamiento porque vive en el
 * lente, no en la escena, y el ruido de sensor se aplica al final porque nace
 * en el sensor. La escena es una textura de bloques, como cualquier superficie
 * real con estructura.
 */
class PersistentSmudgeDetectorTest {

    /** Desplazamientos alternantes que imitan el temblor y paneo de la mano. */
    private val cameraPath = listOf(
        0 to 0, 8 to 3, -6 to 7, 10 to -4, -8 to -6, 7 to 9, -10 to 3, 6 to -8,
        9 to 4, -7 to -3, 8 to -7, -9 to 6, 5 to 8, -6 to -9, 10 to 2, -8 to 7,
    )

    /** La mancha se centra en el centro de una celda interior de la rejilla. */
    private fun dirtyLens(scene: ByteArray): ByteArray = SyntheticFrames.withSmudge(
        scene,
        centerXFraction = 2.5f / LumaGrid.COLUMNS,
        centerYFraction = 2.5f / LumaGrid.ROWS,
        radiusFraction = 0.22f,
        strength = 1.0f,
    )

    private fun scene(): ByteArray = SyntheticFrames.blockNoise(block = 20, seed = 21)

    /** Frame visto a través de un lente sucio en el paso [index] del recorrido. */
    private fun dirtyFrame(index: Int, dx: Int, dy: Int, scene: ByteArray = scene()): LumaImageFrame {
        val shifted = SyntheticFrames.shifted(scene, dx, dy)
        return SyntheticFrames.frame(SyntheticFrames.withSensorNoise(dirtyLens(shifted), index))
    }

    /** Frame visto a través de un lente limpio en el paso [index] del recorrido. */
    private fun cleanFrame(index: Int, dx: Int, dy: Int, scene: ByteArray = scene()): LumaImageFrame {
        val shifted = SyntheticFrames.shifted(scene, dx, dy)
        return SyntheticFrames.frame(SyntheticFrames.withSensorNoise(shifted, index))
    }

    @Test
    fun `una mancha fija en el lente se detecta durante el movimiento`() {
        val detector = PersistentSmudgeDetector()
        var verdict = false

        cameraPath.forEachIndexed { index, (dx, dy) ->
            verdict = detector.update(dirtyFrame(index, dx, dy))
        }

        assertTrue(verdict, "La mancha fija debería detectarse tras la secuencia de movimiento")
    }

    @Test
    fun `un lente limpio con la camara en movimiento no acusa suciedad`() {
        val detector = PersistentSmudgeDetector()
        var verdict = false

        cameraPath.forEachIndexed { index, (dx, dy) ->
            verdict = detector.update(cleanFrame(index, dx, dy))
        }

        assertFalse(verdict)
    }

    @Test
    fun `un objeto estatico de la escena no genera falso positivo`() {
        val detector = PersistentSmudgeDetector()
        var verdict = false

        // El objeto pertenece a la escena: se desplaza con ella al mover la cámara.
        val sceneWithObject = SyntheticFrames.withTexturedObject(
            SyntheticFrames.blockNoise(block = 20, seed = 9),
            centerXFraction = 0.5f,
            centerYFraction = 0.5f,
            sizeFraction = 0.35f,
        )
        cameraPath.forEachIndexed { index, (dx, dy) ->
            verdict = detector.update(cleanFrame(index, dx, dy, scene = sceneWithObject))
        }

        assertFalse(verdict, "Un objeto que se mueve con la escena no es suciedad del lente")
    }

    @Test
    fun `sin movimiento de camara no se acumula evidencia`() {
        val detector = PersistentSmudgeDetector()
        var verdict = false

        // Cámara quieta: solo cambia el ruido de sensor entre frames.
        val still = dirtyLens(scene())
        repeat(30) { index ->
            verdict = detector.update(
                SyntheticFrames.frame(SyntheticFrames.withSensorNoise(still, index)),
            )
        }

        assertFalse(verdict, "Sin movimiento no se puede distinguir mancha de escena")
    }

    @Test
    fun `el veredicto se retira cuando la mancha desaparece`() {
        val detector = PersistentSmudgeDetector()

        cameraPath.forEachIndexed { index, (dx, dy) ->
            detector.update(dirtyFrame(index, dx, dy))
        }

        var verdict = true
        repeat(3) { round ->
            cameraPath.forEachIndexed { index, (dx, dy) ->
                verdict = detector.update(cleanFrame(round * cameraPath.size + index, dx, dy))
            }
        }

        assertFalse(verdict, "Con el lente limpio la sugerencia de limpieza debe retirarse")
    }

    @Test
    fun `reset limpia el estado acumulado`() {
        val detector = PersistentSmudgeDetector()

        cameraPath.forEachIndexed { index, (dx, dy) ->
            detector.update(dirtyFrame(index, dx, dy))
        }
        detector.reset()

        assertFalse(detector.update(dirtyFrame(0, 0, 0)))
    }
}
