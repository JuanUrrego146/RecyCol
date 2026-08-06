package com.botabien.rules

import com.botabien.domain.model.BinDefinition
import com.botabien.domain.model.BinId
import com.botabien.domain.model.CountryProfile
import com.botabien.domain.model.DisposalRoute
import com.botabien.domain.model.InspectionRule
import com.botabien.domain.model.MaterialRule
import com.botabien.domain.model.WasteMaterial

/**
 * Perfiles en memoria para las pruebas del motor de reglas.
 *
 * Reproducen la estructura del código de colores colombiano (blanca, verde,
 * negra) sin pretender ser el perfil oficial: ese es dato del catálogo
 * (`shared/resources/profiles/co.json`) y las pruebas contra él viven en
 * `jvmTest`. Aquí se cubren además casos que el perfil oficial no tiene,
 * como un material sin regla declarada.
 */
object RuleProfileFixtures {

    val whiteBin = BinDefinition(
        id = BinId("white"),
        displayName = "Caneca blanca",
        colorHex = "#FFFFFF",
        route = DisposalRoute.RECYCLABLE,
    )

    val greenBin = BinDefinition(
        id = BinId("green"),
        displayName = "Caneca verde",
        colorHex = "#2E7D32",
        route = DisposalRoute.ORGANIC,
    )

    val blackBin = BinDefinition(
        id = BinId("black"),
        displayName = "Caneca negra",
        colorHex = "#1C1C1C",
        route = DisposalRoute.NON_RECYCLABLE,
    )

    val plasticRule = MaterialRule(
        material = WasteMaterial.PLASTIC,
        targetBin = whiteBin.id,
        contaminatedFallback = blackBin.id,
        justification = "Aprovechable si está limpio y seco; contaminado va a la negra",
    )

    val beverageCartonRule = MaterialRule(
        material = WasteMaterial.BEVERAGE_CARTON,
        targetBin = whiteBin.id,
        contaminatedFallback = blackBin.id,
        justification = "Aprovechable solo sin residuo líquido interior",
    )

    val organicRule = MaterialRule(
        material = WasteMaterial.ORGANIC,
        targetBin = greenBin.id,
        contaminatedFallback = null,
        justification = "Orgánico aprovechable: la humedad no cambia su destino",
    )

    val residualRule = MaterialRule(
        material = WasteMaterial.RESIDUAL,
        targetBin = blackBin.id,
        contaminatedFallback = null,
        justification = "No aprovechable",
    )

    /**
     * Perfil de tres canecas con el caso del cartón para bebidas y, a propósito,
     * sin regla para [WasteMaterial.GLASS]: cubre la ruta conservadora por
     * material no contemplado.
     */
    val threeBins = CountryProfile(
        isoCode = "zz",
        regulationName = "Perfil de prueba",
        regulationReference = "Perfil sintético para pruebas del motor — no citable",
        bins = listOf(whiteBin, greenBin, blackBin),
        rules = listOf(plasticRule, beverageCartonRule, organicRule, residualRule),
        inspectionRules = listOf(
            InspectionRule(
                material = WasteMaterial.BEVERAGE_CARTON,
                promptKey = "inspection.point_inside",
                requiresInteriorView = true,
            ),
        ),
        conservativeBin = blackBin.id,
        unavailableBinNotice = "La caneca ideal ({ideal}) no está disponible; usa {assigned} como alternativa conservadora.",
    )
}
