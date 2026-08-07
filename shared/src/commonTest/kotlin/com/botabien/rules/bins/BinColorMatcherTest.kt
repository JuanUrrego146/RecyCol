package com.botabien.rules.bins

import com.botabien.domain.model.BinDefinition
import com.botabien.domain.model.BinId
import com.botabien.domain.model.CountryProfile
import com.botabien.domain.model.DetectedBin
import com.botabien.domain.model.DisposalRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Emparejamiento de colores detectados con las canecas del perfil (S34,
 * RF-006): robusto a iluminación variable, con descarte informado de los
 * colores ajenos al estándar del país y de las detecciones débiles.
 */
class BinColorMatcherTest {

    private val matcher = BinColorMatcher()

    private val white = BinDefinition(BinId("white"), "Caneca blanca", "#FFFFFF", DisposalRoute.RECYCLABLE)
    private val black = BinDefinition(BinId("black"), "Caneca negra", "#1C1C1C", DisposalRoute.NON_RECYCLABLE)
    private val green = BinDefinition(BinId("green"), "Caneca verde", "#2E7D32", DisposalRoute.ORGANIC)

    private val colombianLike = CountryProfile(
        isoCode = "zz",
        regulationName = "Perfil de prueba",
        regulationReference = "Perfil sintético para pruebas — no citable",
        bins = listOf(white, black, green),
        rules = emptyList(),
        inspectionRules = emptyList(),
        conservativeBin = black.id,
    )

    private val blue = BinDefinition(BinId("blue"), "Contenedor azul", "#1565C0", DisposalRoute.RECYCLABLE)
    private val yellow = BinDefinition(BinId("yellow"), "Contenedor amarillo", "#F9A825", DisposalRoute.RECYCLABLE)
    private val brown = BinDefinition(BinId("brown"), "Contenedor marrón", "#6D4C41", DisposalRoute.ORGANIC)
    private val gray = BinDefinition(BinId("gray"), "Contenedor gris", "#616161", DisposalRoute.NON_RECYCLABLE)

    private val spanishLike = colombianLike.copy(
        bins = listOf(blue, yellow, brown, gray),
        conservativeBin = gray.id,
    )

    private fun detection(hex: String, confidence: Float = 0.9f) = DetectedBin(hex, confidence)

    @Test
    fun lasTresCanecasEmparejanBajoTodasLasCondicionesDeLuz() {
        SyntheticLighting.ALL.forEach { (name, light) ->
            val detections = listOf(white, black, green).map { bin ->
                detection(SyntheticLighting.applyToHex(bin.colorHex, light))
            }

            val result = matcher.match(detections, colombianLike)

            assertEquals(
                setOf(white.id, black.id, green.id),
                result.matches.map { it.bin.id }.toSet(),
                "Emparejamiento bajo luz $name",
            )
            assertTrue(result.unmatched.isEmpty(), "Sin descartes bajo luz $name")
        }
    }

    @Test
    fun unColorAjenoAlEstandarDelPaisSeDescartaYSeInforma() {
        val orange = detection("#E8790F")

        val result = matcher.match(listOf(orange, detection(green.colorHex)), colombianLike)

        assertEquals(listOf(green.id), result.matches.map { it.bin.id })
        val discarded = result.unmatched.single()
        assertEquals(orange, discarded.detected)
        assertEquals(UnmatchedReason.COLOR_NOT_IN_PROFILE, discarded.reason)
    }

    @Test
    fun unaDeteccionDeBajaConfianzaSeDescartaConMotivo() {
        val weak = detection(green.colorHex, confidence = 0.2f)

        val result = matcher.match(listOf(weak), colombianLike)

        assertTrue(result.matches.isEmpty())
        assertEquals(UnmatchedReason.LOW_CONFIDENCE, result.unmatched.single().reason)
    }

    @Test
    fun dosDeteccionesDeLaMismaCanecaConservanLaDeMayorConfianza() {
        val strong = detection(white.colorHex, confidence = 0.9f)
        val duplicate = detection("#F4F4F4", confidence = 0.6f)

        val result = matcher.match(listOf(duplicate, strong), colombianLike)

        val match = result.matches.single()
        assertEquals(white.id, match.bin.id)
        assertEquals(strong, match.detected)
        assertEquals(UnmatchedReason.ALREADY_MATCHED, result.unmatched.single().reason)
    }

    @Test
    fun elAmarilloBajoLuzTenueNoSeConfundeConElMarron() {
        val dimYellow = detection(SyntheticLighting.applyToHex(yellow.colorHex, SyntheticLighting.DIM))

        val result = matcher.match(listOf(dimYellow), spanishLike)

        assertEquals(yellow.id, result.matches.single().bin.id)
    }

    @Test
    fun unGrisMedioNoPerteneceAlPerfilColombiano() {
        val result = matcher.match(listOf(detection("#8F8F8F")), colombianLike)

        // El gris medio no es de este perfil: ni la verde (cromática) ni la
        // blanca o la negra (brillo lejano) deben reclamarlo.
        assertTrue(result.matches.isEmpty())
        assertEquals(UnmatchedReason.COLOR_NOT_IN_PROFILE, result.unmatched.single().reason)
    }

    @Test
    fun lasDeteccionesCanonicasUsanElColorExactoDelPerfilBajoCualquierLuz() {
        SyntheticLighting.ALL.forEach { (name, light) ->
            val detections = listOf(white, black, green).map { bin ->
                detection(SyntheticLighting.applyToHex(bin.colorHex, light))
            }

            val canonical = matcher.match(detections, colombianLike).toCanonicalDetections()

            // Contrato con ScanBinsUseCase (#49): el caso de uso empareja por
            // hex exacto, así que cada detección canónica debe coincidir
            // literalmente con una caneca del perfil.
            assertEquals(3, canonical.size, "Canónicas bajo luz $name")
            canonical.forEach { detected ->
                assertTrue(
                    colombianLike.bins.any { it.colorHex.equals(detected.colorHex, ignoreCase = true) },
                    "«${detected.colorHex}» no es el color canónico de ninguna caneca (luz $name)",
                )
            }
        }
    }

    @Test
    fun elPuntoDeRecoleccionEspecialNuncaSeProponeDesdeElEscaneo() {
        val special = BinDefinition(
            BinId("special"),
            "Punto de recolección especial",
            "#795548",
            DisposalRoute.SPECIAL_COLLECTION,
        )
        val withSpecial = colombianLike.copy(bins = colombianLike.bins + special)

        // Una región exactamente del color declarado para el punto especial
        // no debe emparejar con él: no es una caneca física del entorno (#54).
        val result = matcher.match(listOf(detection(special.colorHex)), withSpecial)

        assertTrue(result.matches.isEmpty())
        assertEquals(UnmatchedReason.COLOR_NOT_IN_PROFILE, result.unmatched.single().reason)
    }

    @Test
    fun losEmparejamientosVienenEnOrdenDeConfianzaDescendente() {
        val detections = listOf(
            detection(green.colorHex, confidence = 0.5f),
            detection(white.colorHex, confidence = 0.9f),
            detection(black.colorHex, confidence = 0.7f),
        )

        val result = matcher.match(detections, colombianLike)

        assertEquals(
            listOf(white.id, black.id, green.id),
            result.matches.map { it.bin.id },
        )
    }
}
