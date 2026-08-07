package com.recycol.domain.usecase

import com.recycol.domain.model.CaptureHint
import com.recycol.domain.model.ClassificationOutcome
import com.recycol.domain.model.WasteMaterial
import kotlin.math.abs

/**
 * Decisión estabilizada que debe mostrar la pantalla.
 *
 * Lleva **solo lo que es una instantánea de verdad**. Los candidatos y el
 * carácter provisional se leen vivos del estabilizador
 * ([ClassificationStabilizer.candidates], [ClassificationStabilizer.isProvisional]):
 * meterlos aquí los congelaba en el instante de la publicación —que es cuando la
 * ventana tiene exactamente un voto— y la hoja de selección manual se sembraba
 * con un único candidato de hace varios segundos.
 *
 * @property outcome decisión visible, o `null` si la pantalla vuelve a no tener
 *   ninguna porque se agotó la evidencia.
 * @property epoch identidad de la decisión. Avanza **solo** cuando la decisión
 *   cambia de verdad, nunca porque un fotograma nuevo diga lo mismo con otro
 *   decimal de confianza, ni porque una provisional ascienda a comprometida. Es
 *   la clave correcta para todo lo que debe ocurrir «una vez por decisión»: la
 *   vibración, la floración y la pregunta de suciedad. Usar `bin.id`, que es lo
 *   que había, ni distingue PLASTIC de PAPER —comparten caneca blanca— ni
 *   sobrevive a una reclasificación.
 *
 *   Es monótono **dentro de esta instancia** y empieza en cero en cada una: cada
 *   pantalla tiene su estabilizador. Quien guarde un epoch para compararlo
 *   después tiene que recordarlo con el tracker como clave, nunca suelto.
 */
data class StabilizedDecision(
    val outcome: ClassificationOutcome?,
    val epoch: Int,
)

/**
 * Agregación temporal de la decisión visible (CUS-003, RF-023).
 *
 * Existe porque el clasificador es **confiado pero inestable entre fotogramas**:
 * sobre el conjunto de control responde el 77 % de los frames con confianza
 * mediana 0,86, acierta el 61 % de esas respuestas, y para un mismo objeto
 * quieto el top-1 cambia en el 13,8 % de los fotogramas consecutivos. Publicar
 * cada frame tal cual produce una decisión distinta varias veces por segundo: la
 * caneca parpadea, el teléfono vibra a 3,3 Hz y la respuesta que acaba de dar el
 * usuario dura lo que tarda en llegar el siguiente fotograma.
 *
 * Cuatro mecanismos separados, cada uno con un trabajo:
 *
 * 1. **Permanencia mínima** ([StabilityThresholds.minHoldMillis]): suelo duro.
 *    Ninguna decisión —ni siquiera la provisional del primer fotograma— vive
 *    menos de eso. Es lo único que garantiza que no haya parpadeo, y no depende
 *    de la cadencia de análisis.
 * 2. **Votación por papeletas sobre una ventana de conteo**: gobierna *qué*
 *    decisión, no *cuándo*. Un fotograma aporta una papeleta: la decisión que
 *    produjo, la duda de RF-023, o nada si la calidad no dio para clasificar.
 *    Que la duda vote como una candidata más evita fabricar un segundo camino de
 *    RF-023 en la interfaz.
 * 3. **Caducidad de la evidencia**: la decisión se retira tras
 *    [StabilityThresholds.evidenceTimeoutMillis] **y**
 *    [StabilityThresholds.evidenceTimeoutPasses] sin una sola papeleta que la
 *    respalde. Las dos condiciones, no una.
 * 4. **Congelación del objeto publicado**: `published` se asigna únicamente
 *    cuando cambia la papeleta publicada. Un fotograma que dice lo mismo refresca
 *    el respaldo y nada más. Sin esto, un fotograma automático del mismo material
 *    —que trae `ContaminationState.UNKNOWN` y por tanto la caneca degradada—
 *    sustituía a la decisión que el usuario acababa de dar y la pregunta de
 *    suciedad reaparecía en bucle.
 *
 * **Un solo reloj.** Todo el tiempo sale de `ImageFrame.timestampMillis`,
 * incluida la marca de la decisión del usuario. Ese reloj es de pared y no es
 * monótono: si salta hacia atrás se trata como escena nueva, que es lo único
 * seguro. Un `pin` anterior al primer fotograma no se ancla en cero —compararlo
 * luego contra una marca de reloj de pared liberaría la decisión al instante—
 * sino en el primer fotograma que llegue.
 *
 * No es seguro para concurrencia: se consume desde la única corrutina que recoge
 * el flujo de frames, secuencialmente.
 */
class ClassificationStabilizer(
    private val thresholds: StabilityThresholds = StabilityThresholds(),
) {

    /**
     * Lo que un fotograma aporta a la votación. La abstención no se modela: es la
     * ausencia de papeleta, y no votar no es lo mismo que votar en contra.
     */
    private sealed interface Ballot {
        data class Decided(val material: WasteMaterial) : Ballot
        data object Unsure : Ballot
    }

    private class Vote(
        val ballot: Ballot,
        val outcome: ClassificationOutcome,
        val atMillis: Long,
    )

    private class Tally(val ballot: Ballot, val votes: Int, val outcome: ClassificationOutcome)

    private val votes = ArrayDeque<Vote>()

    private var published: ClassificationOutcome? = null
    private var publishedBallot: Ballot? = null
    private var committed = false
    private var pinned = false

    /** Instante hasta el que la decisión visible es intocable. */
    private var holdUntil = 0L

    /** Última vez que una papeleta respaldó la decisión visible. */
    private var supportedAt = 0L

    /** Pasadas consecutivas sin una papeleta que respalde la decisión visible. */
    private var unsupportedPasses = 0

    /** Un `pin` llegó antes que el primer fotograma y espera reloj al que anclarse. */
    private var awaitingAnchor = false

    private var lastFrameAt = NO_FRAME
    private var lastBallotAt = NO_FRAME
    private var lastLuminance = Float.NaN
    private var lastSceneChangeAt = NO_FRAME

    /** Media móvil del hueco entre papeletas; 0 mientras no haya dos. */
    private var ballotGapMillis = 0L

    /** Lo que ha cambiado en la pasada en curso: una sola emisión por pasada. */
    private var dirty = false

    private var epoch = 0

    /** Decisión visible sin envolver; para quien necesita consultarla, no reaccionar. */
    val visibleOutcome: ClassificationOutcome? get() = published

    /**
     * Indicaciones que la decisión visible arrastra. La directiva de inspección
     * interior es propiedad del material decidido, no del fotograma: emitirla
     * desde cada frame la hacía competir consigo misma, y como entra en
     * `HintPresenter` saltándose el intervalo mínimo y reiniciando su
     * temporizador, dos materiales alternándose dejaban «busca más luz» sin
     * mostrarse nunca.
     */
    val visibleHints: List<CaptureHint> get() = published?.hints.orEmpty()

    /**
     * `true` mientras la decisión visible sea la opinión de un solo fotograma. Se
     * publica igual —callar dos segundos con el objeto en alto es un producto
     * peor— pero la pantalla puede matizarla. Es propiedad **viva**: una
     * instantánea diría «provisional» para siempre, porque el ascenso a
     * comprometida no emite nada, y no debe: no es una decisión nueva.
     */
    val isProvisional: Boolean get() = published != null && !committed

    /**
     * Hipótesis en cabeza de la ventana, la publicada primero. Materia prima para
     * la hoja de selección manual (CUS-006). Se lee **al abrir la hoja**, no del
     * snapshot: ahí quedaría congelada con la ventana en tamaño 1, que es como
     * está cuando se publica lo provisional.
     *
     * Hoy salen de contar votos del top-1; cuando se abra el puerto a top-K se
     * sustituyen por la distribución real sin cambiar este contrato.
     */
    fun candidates(): List<WasteMaterial> {
        val ranked = votes.asSequence()
            .mapNotNull { (it.ballot as? Ballot.Decided)?.material }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
        // La publicada primero aunque no lidere la ventana: es la que el usuario
        // está viendo, y desmentir empieza por ella.
        val head = (publishedBallot as? Ballot.Decided)?.material
            ?: published?.classification?.material
        return (listOfNotNull(head) + ranked).distinct().take(MAX_CANDIDATES)
    }

    /**
     * Ofrece el resultado de un fotograma.
     *
     * @param luminance luminancia medida del frame, o `null` si no la hay (toma
     *   dirigida de inspección): sin medida no se evalúa el cambio de escena.
     * @return la decisión que la pantalla debe pasar a mostrar, o `null` si nada
     *   cambia. Devolver `null` —en vez del mismo valor otra vez— es lo que
     *   impide recomponer la pantalla entera tres veces por segundo.
     */
    fun offer(
        outcome: ClassificationOutcome,
        atMillis: Long,
        luminance: Float?,
    ): StabilizedDecision? {
        dirty = false

        val sceneChanged = updateTimeline(atMillis, luminance)
        anchorIfPending(atMillis)

        // Una publicación provisional —la opinión de un solo fotograma— solo se
        // permite cuando la pantalla no tiene nada que mostrar por un motivo
        // legítimo: arranque en frío, o escena nueva. Nunca justo después de
        // retirar algo por falta de evidencia: acabamos de concluir que no hay
        // material para decidir, y contestar con un fotograma se contradice.
        var provisionalAllowed = publishedBallot == null

        // Cambio de escena confirmado: delante del lente hay otra cosa, así que la
        // evidencia acumulada es de un objeto que ya no está y la decisión visible
        // no describe nada. Se retira también la decisión, no solo los votos:
        // limitarse a vaciar la urna dejaba la tarjeta del objeto anterior en
        // pantalla. Una decisión que el usuario acaba de fijar está protegida
        // durante su permanencia mínima: al tocar la tarjeta el teléfono se mueve
        // y la exposición cambia, y sería absurdo que el gesto de contestar
        // borrase la respuesta.
        if (sceneChanged && (!pinned || atMillis >= holdUntil)) {
            votes.clear()
            retire()
            holdUntil = atMillis
            provisionalAllowed = true
        }

        val ballot = ballotOf(outcome)
        if (ballot != null) recordGap(atMillis)

        // La poda va en TODAS las pasadas, no solo cuando llega papeleta. Un
        // fotograma que falla la calidad no vota, pero el tiempo sigue corriendo:
        // sin esto los votos de hace tres segundos seguían en la urna y
        // resucitaban la decisión justo al caducar la evidencia. Con el teléfono
        // bajado, eso era vibrar y florecer cada tres segundos indefinidamente
        // por un objeto que la cámara no veía.
        trimStale(atMillis)

        if (ballot != null) {
            votes.addLast(Vote(ballot, outcome, atMillis))
            while (votes.size > thresholds.maxBallots) votes.removeFirst()
        }

        if (publishedBallot != null) {
            if (ballot != null && ballot == publishedBallot) {
                supportedAt = atMillis
                unsupportedPasses = 0
            } else {
                unsupportedPasses++
            }
            val timeout =
                if (pinned) thresholds.pinnedEvidenceTimeoutMillis
                else thresholds.evidenceTimeoutMillis
            if (atMillis - supportedAt >= timeout &&
                unsupportedPasses >= thresholds.evidenceTimeoutPasses
            ) {
                retire()
                provisionalAllowed = false
            }
        }

        val held = publishedBallot
        if (held != null) {
            // Ascenso: la provisional alcanzó quórum. No emite ni avanza el epoch
            // — no es una decisión nueva, es la misma mejor respaldada.
            if (!committed && votesFor(held) >= thresholds.commitVotes) committed = true

            if (atMillis >= holdUntil) {
                val rival = leader { it != held }
                if (rival != null && rival.votes >= votesToUnseat(held)) {
                    publish(rival, atMillis, committed = true)
                }
            }
        }

        if (publishedBallot == null) {
            val best = leader { true }
            when {
                best != null && best.votes >= thresholds.commitVotes ->
                    publish(best, atMillis, committed = true)

                // Publicación provisional: exactamente lo que la app muestra hoy a
                // los 350 ms, ni más acertada ni menos, con la diferencia de que a
                // partir de aquí no se mueve hasta cumplir la permanencia mínima.
                //
                // Solo una decisión, nunca la duda. La duda no es una decisión, es
                // la ausencia de una, y ya es el estado por defecto de la pantalla.
                // Publicarla provisionalmente congelaba durante la permanencia el
                // modo más disruptivo de la interfaz —marco respirando y aviso de
                // baja confianza con botón— por un único primer fotograma
                // desafortunado, que es casi una de cada cuatro sesiones. Mientras
                // tanto la entrada manual sigue visible: nadie se queda sin salida.
                provisionalAllowed && ballot is Ballot.Decided ->
                    publish(Tally(ballot, 1, outcome), atMillis, committed = false)
            }
        }

        // Una sola emisión y un solo incremento de epoch por pasada, pase lo que
        // pase dentro. Retirar y republicar en la misma pasada es **una** decisión
        // nueva para la pantalla, no dos: contarlo dos veces disparaba la
        // vibración y la floración por duplicado.
        if (!dirty) return null
        epoch++
        return decision()
    }

    /**
     * Fija la decisión que dio el usuario (CUS-006, respuesta de suciedad).
     *
     * Limpia la ventana a propósito: cuando el usuario toca hay un fotograma en
     * vuelo que retornará *después* del toque. Sin limpiar, esa papeleta cuenta
     * como evidencia contra lo que la persona acaba de afirmar.
     *
     * Avanza el epoch **siempre**, aunque la papeleta no cambie: contestar la
     * pregunta de suciedad sobre el mismo cartón es una decisión nueva y tiene
     * que vibrar y florecer, que es justo el momento que más lo merece.
     */
    fun pin(outcome: ClassificationOutcome): StabilizedDecision {
        votes.clear()
        published = outcome
        publishedBallot = ballotOf(outcome)
            ?: outcome.classification?.material?.let { Ballot.Decided(it) }
            ?: Ballot.Unsure
        committed = true
        pinned = true
        unsupportedPasses = 0
        if (lastFrameAt == NO_FRAME) {
            // Todavía no ha llegado ningún fotograma, así que no hay reloj al que
            // anclarse. Anclar en 0 y comparar luego contra una marca de reloj de
            // pared hacía que el primer fotograma superase el plazo de evidencia
            // por doce órdenes de magnitud y borrase la elección del usuario al
            // instante. Y es el caso normal: la entrada manual solo se ofrece
            // mientras no hay decisión, o sea al arrancar.
            awaitingAnchor = true
            holdUntil = 0L
            supportedAt = 0L
        } else {
            awaitingAnchor = false
            holdUntil = lastFrameAt + thresholds.minHoldMillis
            supportedAt = lastFrameAt
        }
        epoch++
        return decision()
    }

    /**
     * Olvida la evidencia acumulada y la decisión visible. Se llama al dejar de
     * consumir frames: volver a la pantalla no debe reanudar con votos rancios de
     * hace un minuto, ni con una caneca resuelta contra una disponibilidad que el
     * usuario pudo cambiar en la pantalla de escaneo.
     *
     * El epoch no se reinicia: dentro de esta instancia es monótono.
     */
    fun reset() {
        votes.clear()
        published = null
        publishedBallot = null
        committed = false
        pinned = false
        holdUntil = 0L
        supportedAt = 0L
        unsupportedPasses = 0
        awaitingAnchor = false
        lastFrameAt = NO_FRAME
        lastBallotAt = NO_FRAME
        lastLuminance = Float.NaN
        lastSceneChangeAt = NO_FRAME
        ballotGapMillis = 0L
    }

    private fun ballotOf(outcome: ClassificationOutcome): Ballot? = when {
        // El orden importa: un resultado de baja confianza trae clasificación *y*
        // needsUserDecision. Es duda, no decisión.
        outcome.disposal != null ->
            outcome.classification?.material?.let { Ballot.Decided(it) }
        outcome.needsUserDecision -> Ballot.Unsure
        else -> null
    }

    /**
     * Coste de desbancar a la decisión visible.
     *
     * La histéresis protege a una opinión **respaldada**, no a una que ya perdió
     * la ventana. Un titular caído por debajo de `commitVotes` se desbanca con
     * `commitVotes`: sin esa condición, un objeto que supera el umbral en 2 de
     * cada 5 fotogramas producía un estado estacionario en el que la app mostraba
     * una caneca firme, vibrada y celebrada, indefinidamente, mientras la mayoría
     * de los fotogramas decía que el modelo no llega al umbral.
     */
    private fun votesToUnseat(held: Ballot): Int = when {
        pinned -> thresholds.unpinVotes
        committed && votesFor(held) >= thresholds.commitVotes -> thresholds.switchVotes
        else -> thresholds.commitVotes
    }

    /**
     * Ventana efectiva. `windowMillis` es un **suelo**, no un techo: si las
     * papeletas llegan cada 3,5 s —una gama media dentro de su presupuesto—, una
     * ventana absoluta de 2,5 s se vacía en cada pasada, el estabilizador no se
     * compromete jamás y publica el top-1 de cada fotograma, que es exactamente
     * lo que venía a arreglar.
     */
    private fun effectiveWindowMillis(): Long =
        maxOf(thresholds.windowMillis, ballotGapMillis * thresholds.maxBallots)

    /**
     * Mide la cadencia real de **papeletas**, no de fotogramas: el filtro de
     * calidad descarta una fracción desconocida. Se lleva aparte de la urna a
     * propósito: si se leyera del último voto guardado, una ventana demasiado
     * estrecha la vaciaría, no habría con qué medir y la ventana no se ensancharía
     * nunca — el pez que se muerde la cola.
     */
    private fun recordGap(atMillis: Long) {
        val previous = lastBallotAt
        lastBallotAt = atMillis
        if (previous == NO_FRAME) return
        val gap = (atMillis - previous).coerceIn(0L, thresholds.maxBallotGapMillis)
        if (gap <= 0L) return
        ballotGapMillis = if (ballotGapMillis == 0L) gap else (ballotGapMillis + gap) / 2
    }

    private fun trimStale(now: Long) {
        val window = effectiveWindowMillis()
        while (votes.isNotEmpty() && now - votes.first().atMillis > window) votes.removeFirst()
    }

    private fun votesFor(ballot: Ballot): Int = votes.count { it.ballot == ballot }

    /**
     * Papeleta con más votos entre las aceptadas. El desempate lo da el orden de
     * llegada; es determinista, que es lo que exigen las pruebas, y con
     * `commitVotes = 3` sobre una ventana de 5 un empate nunca alcanza quórum.
     */
    private fun leader(accept: (Ballot) -> Boolean): Tally? {
        val counts = votes.groupingBy { it.ballot }.eachCount().filterKeys(accept)
        val best = counts.maxByOrNull { it.value } ?: return null
        val outcome = votes.last { it.ballot == best.key }.outcome
        return Tally(best.key, best.value, outcome)
    }

    private fun publish(tally: Tally, atMillis: Long, committed: Boolean) {
        published = tally.outcome
        publishedBallot = tally.ballot
        this.committed = committed
        pinned = false
        holdUntil = atMillis + thresholds.minHoldMillis
        supportedAt = atMillis
        unsupportedPasses = 0
        dirty = true
    }

    private fun retire() {
        if (published == null && publishedBallot == null) return
        published = null
        publishedBallot = null
        committed = false
        pinned = false
        unsupportedPasses = 0
        dirty = true
    }

    private fun anchorIfPending(atMillis: Long) {
        if (!awaitingAnchor) return
        awaitingAnchor = false
        supportedAt = atMillis
        holdUntil = atMillis + thresholds.minHoldMillis
    }

    private fun decision() = StabilizedDecision(outcome = published, epoch = epoch)

    /** @return `true` si delante del lente hay algo distinto. */
    private fun updateTimeline(atMillis: Long, luminance: Float?): Boolean {
        val previousFrameAt = lastFrameAt
        val previousLuminance = lastLuminance
        lastFrameAt = atMillis
        if (luminance != null) lastLuminance = luminance

        if (previousFrameAt != NO_FRAME && atMillis < previousFrameAt) {
            // El reloj de pared se movió hacia atrás. Todos los deltas guardados
            // dejan de significar nada; se reancla en vez de arrastrar basura.
            supportedAt = atMillis
            holdUntil = atMillis
            lastSceneChangeAt = atMillis
            return true
        }
        if (luminance == null || previousLuminance.isNaN() || previousFrameAt == NO_FRAME) {
            return false
        }
        if (abs(luminance - previousLuminance) < thresholds.sceneChangeLuminanceDelta) return false
        // Una lámpara parpadeando o una autoexposición nerviosa no pueden disparar
        // esto más de una vez por permanencia mínima: el peor caso de un falso
        // positivo queda acotado a un cambio de decisión.
        if (lastSceneChangeAt != NO_FRAME &&
            atMillis - lastSceneChangeAt < thresholds.minHoldMillis
        ) {
            return false
        }
        lastSceneChangeAt = atMillis
        return true
    }

    private companion object {
        const val NO_FRAME = Long.MIN_VALUE
        const val MAX_CANDIDATES = 2
    }
}
