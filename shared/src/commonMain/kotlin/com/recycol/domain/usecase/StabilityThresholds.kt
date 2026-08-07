package com.recycol.domain.usecase

/**
 * Parámetros de la agregación temporal de la decisión visible.
 *
 * Se inyectan por la misma razón que [ConfidenceThresholds] y
 * [QualityThresholds]: son calibración, no política. Quien los ajusta con
 * dispositivos reales es QA, sin tocar la máquina de estados.
 *
 * **Lo que gobierna al usuario va en tiempo; lo que gobierna la evidencia va en
 * votos.** Ninguna de las dos cosas se expresa en fotogramas: la cadencia de
 * análisis no está garantizada —la gama baja clasifica bajo demanda, y una gama
 * media que tarda 3,5 s por clasificación sigue dentro de su presupuesto— así
 * que un parámetro en fotogramas significaría cosas distintas en cada gama.
 *
 * @property maxBallots papeletas que caben en la ventana de votación. Es un tope
 *   **de conteo**: la ventana siempre puede llenarse, sea cual sea la cadencia.
 *   Cerrarla solo por tiempo dejaba un único voto dentro en gama degradada, con
 *   lo que el estabilizador nunca se comprometía y era un no-op más lento que no
 *   estabilizar nada.
 * @property windowMillis **suelo** de antigüedad relevante de una papeleta. La
 *   ventana efectiva es `max(windowMillis, maxBallots × hueco observado)`: el
 *   tiempo solo puede ensancharla, jamás vaciarla.
 * @property maxBallotGapMillis tope del hueco que se considera al medir la
 *   cadencia; por encima de eso el dispositivo se degrada de gama solo.
 * @property commitVotes votos para comprometerse con una decisión.
 * @property switchVotes votos para desbancar a un titular **que todavía tiene
 *   [commitVotes] en la ventana**. Estrictamente mayor que [commitVotes]:
 *   cambiar de opinión debe costar más que formarla. Pero la histéresis protege
 *   a una opinión respaldada, no a una que ya perdió la ventana: un titular
 *   caído por debajo se desbanca con [commitVotes]. Sin esa condición, un objeto
 *   que supera el umbral en 2 de cada 5 fotogramas dejaba una caneca firme en
 *   pantalla para siempre mientras la mayoría de los fotogramas decía que el
 *   modelo no llega al umbral.
 * @property unpinVotes votos para desbancar una decisión que dio el usuario.
 *   Igual a [maxBallots] a propósito: hace falta que la ventana **entera** diga
 *   otra cosa. Que el clasificador insista no es motivo para contradecir a una
 *   persona, y con errores correlacionados entre fotogramas insistir es
 *   justamente lo que hace.
 * @property minHoldMillis permanencia mínima de una decisión en pantalla. Suelo
 *   duro contra el parpadeo: nada cambia antes de que se cumpla.
 * @property evidenceTimeoutMillis tiempo sin papeleta que respalde la decisión
 *   visible antes de retirarla.
 * @property evidenceTimeoutPasses pasadas sin respaldo exigidas **además** del
 *   tiempo. Van juntas: solo con tiempo, una cadencia de 3,5 s convertía el
 *   plazo en «un fotograma» y la pantalla se borraba y repintaba en cada pasada;
 *   solo con pasadas, perder la calidad de forma sostenida no retiraba nada
 *   porque los fotogramas malos llegan pero no votan.
 * @property pinnedEvidenceTimeoutMillis lo mismo para la decisión del usuario.
 * @property sceneChangeLuminanceDelta salto de luminancia que se interpreta como
 *   «hay otra cosa delante del lente». **Desactivado por omisión**: es el único
 *   parámetro sin respaldo empírico y su efecto es destructivo (retira la
 *   decisión visible). El código y sus pruebas están; se enciende poniendo
 *   [SCENE_CHANGE_CANDIDATE] cuando la medición en dispositivo lo respalde.
 */
data class StabilityThresholds(
    val maxBallots: Int = 5,
    val windowMillis: Long = 2_500L,
    val maxBallotGapMillis: Long = 4_000L,
    val commitVotes: Int = 3,
    val switchVotes: Int = 4,
    val unpinVotes: Int = 5,
    val minHoldMillis: Long = 1_400L,
    val evidenceTimeoutMillis: Long = 3_000L,
    val evidenceTimeoutPasses: Int = 4,
    val pinnedEvidenceTimeoutMillis: Long = 8_000L,
    val sceneChangeLuminanceDelta: Float = Float.MAX_VALUE,
) {
    init {
        require(switchVotes > commitVotes) {
            "Cambiar de opinión tiene que costar más que formarla"
        }
        require(unpinVotes >= maxBallots) {
            "Desbancar al usuario exige la ventana entera en contra"
        }
        require(commitVotes in 1..maxBallots) { "commitVotes no cabe en la ventana" }
    }

    companion object {
        /**
         * Valor a probar en dispositivo. `FrameQuality.luminance` está
         * normalizada en `[0,1]` y el umbral de subexposición calibrado por CAM
         * es 0,16: un salto de 0,12 son tres cuartas partes de la banda «oscuro».
         */
        const val SCENE_CHANGE_CANDIDATE = 0.12f
    }
}
