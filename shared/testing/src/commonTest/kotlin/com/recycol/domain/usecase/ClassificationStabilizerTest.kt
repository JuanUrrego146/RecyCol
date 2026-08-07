package com.recycol.domain.usecase

import com.recycol.domain.model.BinDefinition
import com.recycol.domain.model.BinId
import com.recycol.domain.model.ClassificationOutcome
import com.recycol.domain.model.ClassificationResult
import com.recycol.domain.model.Disposal
import com.recycol.domain.model.DisposalRoute
import com.recycol.domain.model.WasteMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El reloj son los `atMillis` que se pasan: dominio puro, sin corrutinas ni
 * dispositivo. Los tiempos imitan la cadencia real medida en el Galaxy A35,
 * ~3 fotogramas por segundo.
 */
class ClassificationStabilizerTest {

    private fun bin(id: String, route: DisposalRoute = DisposalRoute.RECYCLABLE) =
        BinDefinition(BinId(id), "Caneca $id", "#FFFFFF", route)

    private fun decided(
        material: WasteMaterial,
        binId: String = "blanca",
        confidence: Float = 0.9f,
        manual: Boolean = false,
    ) = ClassificationOutcome(
        classification = ClassificationResult(material, confidence),
        disposal = Disposal(
            bin = bin(binId),
            route = DisposalRoute.RECYCLABLE,
            justification = "porque sí",
            degradedByContamination = false,
        ),
        hints = emptyList(),
        needsUserDecision = false,
        manualSelection = manual,
    )

    private fun unsure(material: WasteMaterial = WasteMaterial.PLASTIC) = ClassificationOutcome(
        classification = ClassificationResult(material, 0.3f),
        disposal = null,
        hints = emptyList(),
        needsUserDecision = true,
    )

    /** Fotograma que no dio ni para clasificar: no vota. */
    private fun abstention() = ClassificationOutcome(
        classification = null,
        disposal = null,
        hints = emptyList(),
        needsUserDecision = false,
    )

    private val plastic = decided(WasteMaterial.PLASTIC)
    private val paper = decided(WasteMaterial.PAPER, binId = "gris")

    @Test
    fun `el primer fotograma decidido publica una decision provisional`() {
        val stabilizer = ClassificationStabilizer()

        val emitted = stabilizer.offer(plastic, atMillis = 1_000, luminance = 0.5f)

        assertNotNull(emitted)
        assertEquals(WasteMaterial.PLASTIC, emitted.outcome?.classification?.material)
        assertTrue(stabilizer.isProvisional, "Un solo voto no es una decisión comprometida")
    }

    @Test
    fun `la duda no se publica provisionalmente`() {
        val stabilizer = ClassificationStabilizer()

        // Un primer fotograma desafortunado no puede congelar el modo más
        // disruptivo de la interfaz durante toda la permanencia mínima.
        assertNull(stabilizer.offer(unsure(), atMillis = 1_000, luminance = 0.5f))
    }

    @Test
    fun `la duda se publica al alcanzar el quorum`() {
        val stabilizer = ClassificationStabilizer()

        assertNull(stabilizer.offer(unsure(), 1_000, 0.5f))
        assertNull(stabilizer.offer(unsure(), 1_300, 0.5f))
        val emitted = stabilizer.offer(unsure(), 1_600, 0.5f)

        assertNotNull(emitted)
        assertTrue(emitted.outcome?.needsUserDecision == true)
    }

    @Test
    fun `la decision no se mueve dentro de la permanencia minima`() {
        val stabilizer = ClassificationStabilizer()
        stabilizer.offer(plastic, 1_000, 0.5f)

        // Cuatro papeletas seguidas de otro material dentro de los 1 400 ms.
        val cambios = listOf(1_200L, 1_400L, 1_900L, 2_300L)
            .count { stabilizer.offer(paper, it, 0.5f) != null }

        assertEquals(0, cambios, "Nada puede desbancar dentro de la permanencia mínima")
    }

    @Test
    fun `dos decisiones nunca estan mas cerca que la permanencia minima`() {
        val thresholds = StabilityThresholds()
        val stabilizer = ClassificationStabilizer(thresholds)
        val publicadas = mutableListOf<Long>()
        var t = 1_000L

        // El bug observado: dos materiales alternando a ~3 fps durante 10 s. Es
        // el peor caso posible para la votación —ninguno consigue mayoría clara—
        // y aun así la pantalla no puede cambiar más rápido que la permanencia.
        var epochPrevio = -1
        repeat(33) { index ->
            val outcome = if (index % 2 == 0) plastic else paper
            // Solo los cambios de decisión, no los ascensos: el ascenso emite
            // para que la pantalla se entere, pero no mueve nada visible.
            stabilizer.offer(outcome, t, 0.5f)?.let { emitida ->
                if (emitida.epoch != epochPrevio) {
                    publicadas += t
                    epochPrevio = emitida.epoch
                }
            }
            t += 300
        }

        val huecos = publicadas.zipWithNext { previa, siguiente -> siguiente - previa }
        assertTrue(
            huecos.all { it >= thresholds.minHoldMillis },
            "Hubo cambios a ${huecos.filter { it < thresholds.minHoldMillis }} ms: el parpadeo vuelve",
        )
        // Antes: 33 decisiones en 10 s. Ahora, como mucho una cada 1,4 s.
        assertTrue(publicadas.size <= 8, "Se emitieron ${publicadas.size} decisiones en 10 s")
    }

    @Test
    fun `el mismo material con otra confianza no republica`() {
        val stabilizer = ClassificationStabilizer()
        assertNotNull(stabilizer.offer(decided(WasteMaterial.PLASTIC, confidence = 0.91f), 1_000, 0.5f))

        // Mismo material, otro decimal: no es una decisión nueva y no debe
        // recomponer la pantalla ni volver a vibrar.
        assertNull(stabilizer.offer(decided(WasteMaterial.PLASTIC, confidence = 0.88f), 1_300, 0.5f))
    }

    @Test
    fun `tres votos iguales comprometen desde cero`() {
        val stabilizer = ClassificationStabilizer()
        // Arranca con duda para que no haya publicación provisional.
        stabilizer.offer(unsure(), 1_000, 0.5f)
        stabilizer.offer(unsure(), 1_200, 0.5f)
        stabilizer.offer(unsure(), 1_400, 0.5f)

        assertFalse(stabilizer.isProvisional)
    }

    @Test
    fun `un voto discrepante no desbanca`() {
        val stabilizer = ClassificationStabilizer()
        repeat(3) { stabilizer.offer(plastic, 1_000 + it * 300L, 0.5f) }

        // Ya pasada la permanencia mínima, pero un solo voto rival no basta.
        assertNull(stabilizer.offer(paper, 3_000, 0.5f))
    }

    @Test
    fun `una decision comprometida no se releva sin unanimidad`() {
        val stabilizer = ClassificationStabilizer()
        repeat(3) { stabilizer.offer(plastic, 1_000 + it * 300L, 0.5f) }
        assertFalse(stabilizer.isProvisional)

        // Cuatro de cinco no bastan: eso es el modelo dudando del mismo objeto.
        // Esta es la deriva observada en el teléfono —el mismo vaso pasando por
        // plástico, vidrio y cartón— y no puede volver.
        listOf(3_000L, 3_300L, 3_600L, 3_900L).forEach { stabilizer.offer(paper, it, 0.5f) }

        assertEquals(WasteMaterial.PLASTIC, stabilizer.visibleOutcome?.classification?.material)
    }

    @Test
    fun `una ventana entera y unanime si releva a una comprometida`() {
        val stabilizer = ClassificationStabilizer()
        repeat(3) { stabilizer.offer(plastic, 1_000 + it * 300L, 0.5f) }

        // Kilómetro y medio de segundo diciendo consistentemente otra cosa: eso
        // es apuntar a otro objeto, no dudar del mismo.
        var t = 3_000L
        repeat(5) {
            stabilizer.offer(paper, t, 0.5f)
            t += 300
        }

        assertEquals(WasteMaterial.PAPER, stabilizer.visibleOutcome?.classification?.material)
    }

    @Test
    fun `una provisional si se releva con el quorum normal`() {
        val stabilizer = ClassificationStabilizer()
        stabilizer.offer(plastic, 1_000, 0.5f)
        assertTrue(stabilizer.isProvisional, "Un solo voto es una opinión, no una decisión")

        var t = 2_500L
        repeat(3) {
            stabilizer.offer(paper, t, 0.5f)
            t += 300
        }

        assertEquals(WasteMaterial.PAPER, stabilizer.visibleOutcome?.classification?.material)
    }

    @Test
    fun `el ascenso a comprometida se emite sin avanzar el epoch`() {
        val stabilizer = ClassificationStabilizer()
        val first = stabilizer.offer(plastic, 1_000, 0.5f)
        assertNotNull(first)
        assertTrue(first.provisional)

        assertNull(stabilizer.offer(plastic, 1_300, 0.5f))
        val promoted = stabilizer.offer(plastic, 1_600, 0.5f)

        // La pantalla tiene que enterarse para poder colgar la pregunta de
        // suciedad, pero el epoch no se mueve: no es una decisión nueva y volver
        // a vibrar por ella sería mentir.
        assertNotNull(promoted, "El ascenso tiene que llegar a la pantalla")
        assertFalse(promoted.provisional)
        assertEquals(first.epoch, promoted.epoch)
    }

    @Test
    fun `un fotograma sin papeleta no suelta la decision`() {
        val stabilizer = ClassificationStabilizer()
        stabilizer.offer(plastic, 1_000, 0.5f)

        // Fotogramas borrosos: llegan pero no votan. No son evidencia en contra.
        assertNull(stabilizer.offer(abstention(), 1_300, 0.5f))
        assertNull(stabilizer.offer(abstention(), 1_600, 0.5f))
        assertNotNull(stabilizer.visibleOutcome)
    }

    @Test
    fun `sin evidencia sostenida la decision se retira`() {
        val stabilizer = ClassificationStabilizer()
        stabilizer.offer(plastic, 1_000, 0.5f)

        // Hacen falta las dos cosas: 3 s y 4 pasadas.
        var retirada: StabilizedDecision? = null
        listOf(2_000L, 3_000L, 4_100L, 4_400L, 4_700L).forEach { t ->
            stabilizer.offer(abstention(), t, 0.5f)?.let { retirada = it }
        }

        assertNotNull(retirada)
        assertNull(retirada.outcome, "La pantalla debe quedarse sin decisión")
    }

    @Test
    fun `la decision retirada no resucita con votos caducados`() {
        val stabilizer = ClassificationStabilizer()
        stabilizer.offer(plastic, 1_000, 0.5f)
        listOf(2_000L, 3_000L, 4_100L, 4_400L, 4_700L).forEach {
            stabilizer.offer(abstention(), it, 0.5f)
        }
        assertNull(stabilizer.visibleOutcome)

        // Con el teléfono bajado, esto era vibrar y florecer cada tres segundos.
        assertNull(stabilizer.offer(abstention(), 5_000, 0.5f))
        assertNull(stabilizer.offer(abstention(), 5_300, 0.5f))
        assertNull(stabilizer.visibleOutcome)
    }

    @Test
    fun `el pin anula los votos anteriores`() {
        val stabilizer = ClassificationStabilizer()
        repeat(3) { stabilizer.offer(plastic, 1_000 + it * 300L, 0.5f) }

        val fixed = stabilizer.pin(decided(WasteMaterial.CARDBOARD, manual = true))

        assertEquals(WasteMaterial.CARDBOARD, fixed.outcome?.classification?.material)
        assertTrue(fixed.outcome?.manualSelection == true)
    }

    @Test
    fun `el fotograma automatico no pisa la respuesta del usuario`() {
        val stabilizer = ClassificationStabilizer()
        stabilizer.offer(decided(WasteMaterial.CARDBOARD), 1_000, 0.5f)
        stabilizer.pin(decided(WasteMaterial.CARDBOARD, binId = "blanca", manual = true))

        // El fotograma en vuelo trae el MISMO material pero sin conocer la
        // contaminación, así que su caneca es la degradada. Si esto pisara la
        // respuesta, la pregunta de suciedad reaparecería en bucle.
        val emitted = stabilizer.offer(decided(WasteMaterial.CARDBOARD, binId = "negra"), 1_300, 0.5f)

        assertNull(emitted, "Un fotograma automático no puede desmentir al usuario")
        assertTrue(stabilizer.visibleOutcome?.manualSelection == true)
    }

    @Test
    fun `cuatro votos discrepantes no le quitan la respuesta al usuario`() {
        val stabilizer = ClassificationStabilizer()
        stabilizer.offer(plastic, 1_000, 0.5f)
        stabilizer.pin(decided(WasteMaterial.CARDBOARD, manual = true))

        var t = 3_000L
        repeat(4) {
            stabilizer.offer(paper, t, 0.5f)
            t += 300
        }

        assertTrue(
            stabilizer.visibleOutcome?.manualSelection == true,
            "Desbancar a una persona exige la ventana entera",
        )
    }

    @Test
    fun `cinco votos discrepantes unanimes si la sueltan`() {
        val stabilizer = ClassificationStabilizer()
        stabilizer.offer(plastic, 1_000, 0.5f)
        stabilizer.pin(decided(WasteMaterial.CARDBOARD, manual = true))

        var t = 3_000L
        repeat(5) {
            stabilizer.offer(paper, t, 0.5f)
            t += 300
        }

        assertEquals(WasteMaterial.PAPER, stabilizer.visibleOutcome?.classification?.material)
    }

    @Test
    fun `el pin antes del primer fotograma no se libera`() {
        val stabilizer = ClassificationStabilizer()

        // La entrada manual solo se ofrece mientras no hay decisión, o sea al
        // arrancar: aquí no hay ningún reloj al que anclarse todavía.
        stabilizer.pin(decided(WasteMaterial.BATTERY, manual = true))

        // Primer fotograma con marca de reloj de pared: doce órdenes de magnitud
        // por encima de cero. Anclar el pin en 0 lo habría liberado al instante.
        stabilizer.offer(abstention(), 1_750_000_000_000L, 0.5f)

        assertTrue(stabilizer.visibleOutcome?.manualSelection == true)
    }

    @Test
    fun `un reloj que retrocede se trata como escena nueva`() {
        val stabilizer = ClassificationStabilizer()
        repeat(3) { stabilizer.offer(plastic, 5_000 + it * 300L, 0.5f) }

        // El reloj de pared saltó hacia atrás: los deltas guardados dejan de
        // significar nada.
        val emitted = stabilizer.offer(paper, 2_000, 0.5f)

        assertNotNull(emitted)
        assertEquals(WasteMaterial.PAPER, emitted.outcome?.classification?.material)
    }

    @Test
    fun `un salto de luminancia retira y publica en la misma pasada`() {
        val stabilizer = ClassificationStabilizer(
            StabilityThresholds(
                sceneChangeLuminanceDelta = StabilityThresholds.SCENE_CHANGE_CANDIDATE,
            ),
        )
        repeat(3) { stabilizer.offer(plastic, 1_000 + it * 300L, 0.5f) }

        // Otro objeto delante del lente: la tarjeta anterior no describe nada.
        val emitted = stabilizer.offer(paper, atMillis = 3_000, luminance = 0.9f)

        assertNotNull(emitted)
        assertEquals(WasteMaterial.PAPER, emitted.outcome?.classification?.material)
    }

    @Test
    fun `por omision el detector de escena esta apagado`() {
        val stabilizer = ClassificationStabilizer()
        repeat(3) { stabilizer.offer(plastic, 1_000 + it * 300L, 0.5f) }

        // Mismo salto de luminancia, sin el detector encendido: manda la votación.
        assertNull(stabilizer.offer(paper, atMillis = 3_000, luminance = 0.95f))
    }

    @Test
    fun `la ventana efectiva se ensancha con la cadencia lenta`() {
        val stabilizer = ClassificationStabilizer()

        // Gama media degradada: 3,5 s por clasificación, dentro de presupuesto.
        // Con una ventana absoluta de 2,5 s no quedaría nunca más de un voto y el
        // estabilizador no llegaría a comprometerse jamás.
        var t = 1_000L
        repeat(3) {
            stabilizer.offer(plastic, t, 0.5f)
            t += 3_500
        }

        assertFalse(stabilizer.isProvisional, "A cadencia lenta también hay que comprometerse")
    }

    @Test
    fun `los candidatos son vivos y siguen a la ventana`() {
        val stabilizer = ClassificationStabilizer()
        stabilizer.offer(plastic, 1_000, 0.5f)
        assertEquals(listOf(WasteMaterial.PLASTIC), stabilizer.candidates())

        listOf(1_300L, 1_600L, 1_900L, 2_200L).forEachIndexed { index, t ->
            stabilizer.offer(if (index % 2 == 0) paper else plastic, t, 0.5f)
        }

        assertTrue(
            WasteMaterial.PAPER in stabilizer.candidates(),
            "La hoja manual se sembraba con un candidato de hace varios segundos",
        )
    }

    @Test
    fun `reset olvida los votos pero no el epoch`() {
        val stabilizer = ClassificationStabilizer()
        val first = stabilizer.offer(plastic, 1_000, 0.5f)
        assertNotNull(first)

        stabilizer.reset()
        assertNull(stabilizer.visibleOutcome)

        val after = stabilizer.offer(paper, 2_000, 0.5f)
        assertNotNull(after)
        assertTrue(after.epoch > first.epoch, "El epoch es monótono dentro de la instancia")
    }
}
