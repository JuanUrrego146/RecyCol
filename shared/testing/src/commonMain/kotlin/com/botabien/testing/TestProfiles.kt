package com.botabien.testing

import com.botabien.domain.model.BinDefinition
import com.botabien.domain.model.BinId
import com.botabien.domain.model.CountryProfile
import com.botabien.domain.model.DisposalRoute
import com.botabien.domain.model.InspectionRule
import com.botabien.domain.model.MaterialRule
import com.botabien.domain.model.WasteMaterial

/**
 * Perfiles normativos de prueba, construidos en memoria y compartidos por los
 * fakes y las pruebas de todos los agentes.
 *
 * [threeBins] refleja la estructura del código de colores colombiano
 * (Resolución 2184 de 2019: blanca, verde y negra) sin pretender ser el perfil
 * oficial: ese es dato del catálogo (`shared/resources/profiles/co.json`, S03)
 * y no se duplica aquí.
 */
object TestProfiles {

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
        colorHex = "#000000",
        route = DisposalRoute.NON_RECYCLABLE,
    )

    /** Perfil determinista de tres canecas con el caso del cartón para bebidas. */
    val threeBins = CountryProfile(
        isoCode = "zz",
        regulationName = "Perfil de prueba",
        regulationReference = "Perfil sintético para pruebas — no citable",
        bins = listOf(whiteBin, greenBin, blackBin),
        rules = listOf(
            MaterialRule(
                material = WasteMaterial.PLASTIC,
                targetBin = whiteBin.id,
                contaminatedFallback = blackBin.id,
                justification = "Aprovechable si está limpio y seco",
            ),
            MaterialRule(
                material = WasteMaterial.BEVERAGE_CARTON,
                targetBin = whiteBin.id,
                contaminatedFallback = blackBin.id,
                justification = "Aprovechable solo sin residuo líquido interior",
            ),
            MaterialRule(
                material = WasteMaterial.ORGANIC,
                targetBin = greenBin.id,
                contaminatedFallback = null,
                justification = "Orgánico aprovechable",
            ),
            MaterialRule(
                material = WasteMaterial.RESIDUAL,
                targetBin = blackBin.id,
                contaminatedFallback = null,
                justification = "No aprovechable",
            ),
        ),
        inspectionRules = listOf(
            InspectionRule(
                material = WasteMaterial.BEVERAGE_CARTON,
                promptKey = "inspection.point_inside",
                requiresInteriorView = true,
            ),
        ),
        conservativeBin = blackBin.id,
    )
}
