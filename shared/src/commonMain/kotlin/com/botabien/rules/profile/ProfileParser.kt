package com.botabien.rules.profile

import com.botabien.domain.model.BinDefinition
import com.botabien.domain.model.BinId
import com.botabien.domain.model.CountryProfile
import com.botabien.domain.model.DisposalRoute
import com.botabien.domain.model.InspectionRule
import com.botabien.domain.model.MaterialRule
import com.botabien.domain.model.WasteMaterial
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parseo y validación de los archivos del catálogo de perfiles (RF-002, RF-004).
 *
 * Aplica en código multiplataforma las mismas restricciones que declara
 * `profile.schema.json` (el esquema sigue siendo la fuente de verdad para
 * autoría y validación en CI) más las de integridad referencial: toda caneca
 * referenciada existe, ningún material se repite y las reglas de inspección
 * apuntan a materiales con regla. Un archivo inválido produce una
 * [ProfileValidationException] con todos los problemas acumulados; nunca un
 * perfil a medias.
 */
object ProfileParser {

    private val json = Json

    private val isoCodePattern = Regex("^[a-z]{2}$")
    private val binIdPattern = Regex("^[a-z][a-z0-9_-]*$")
    private val colorHexPattern = Regex("^#[0-9A-F]{6}$")
    private val promptKeyPattern = Regex("^[a-z][a-z0-9_.]*$")
    private val profileIdPattern = Regex("^[a-z][a-z0-9_-]*$")

    /**
     * Parsea y valida un perfil normativo completo.
     *
     * @param sourceName nombre del archivo, citado en los errores.
     * @param jsonText contenido JSON del perfil.
     * @throws ProfileValidationException si el documento no tiene la forma del
     *   esquema o rompe la integridad referencial; el mensaje enumera todos
     *   los problemas encontrados.
     */
    fun parseProfile(sourceName: String, jsonText: String): CountryProfile {
        val document = decode<ProfileDocument>(sourceName, jsonText)
        val problems = mutableListOf<String>()

        if (!isoCodePattern.matches(document.isoCode)) {
            problems += "isoCode «${document.isoCode}» no es un código ISO 3166-1 alfa-2 en minúsculas"
        }
        if (document.regulationName.isBlank()) problems += "regulationName está vacío"
        if (document.regulationReference.isBlank()) problems += "regulationReference está vacío"

        validateBins(document.bins, problems)
        val binIds = document.bins.map { it.id }.toSet()
        validateRules(document.rules, binIds, problems)
        validateInspectionRules(document.inspectionRules, document.rules, problems)

        if (document.conservativeBin !in binIds) {
            problems += "conservativeBin «${document.conservativeBin}» no está definida en bins"
        }

        if (problems.isNotEmpty()) throw ProfileValidationException(sourceName, problems)

        return CountryProfile(
            isoCode = document.isoCode,
            regulationName = document.regulationName,
            regulationReference = document.regulationReference,
            bins = document.bins.map { bin ->
                BinDefinition(
                    id = BinId(bin.id),
                    displayName = bin.displayName,
                    colorHex = bin.colorHex,
                    route = DisposalRoute.valueOf(bin.route),
                )
            },
            rules = document.rules.map { rule ->
                MaterialRule(
                    material = WasteMaterial.valueOf(rule.material),
                    targetBin = BinId(rule.targetBin),
                    contaminatedFallback = rule.contaminatedFallback?.let { BinId(it) },
                    justification = rule.justification,
                )
            },
            inspectionRules = document.inspectionRules.map { rule ->
                InspectionRule(
                    material = WasteMaterial.valueOf(rule.material),
                    promptKey = rule.promptKey,
                    requiresInteriorView = rule.requiresInteriorView,
                )
            },
            conservativeBin = BinId(document.conservativeBin),
        )
    }

    /**
     * Parsea y valida el índice del catálogo (`catalog.json`).
     *
     * @throws ProfileValidationException si el índice es inválido: entradas
     *   duplicadas, país sin perfil por defecto o con más de uno, o campos
     *   fuera de formato.
     */
    fun parseCatalog(sourceName: String, jsonText: String): List<ProfileDescriptor> {
        val document = decode<CatalogDocument>(sourceName, jsonText)
        val problems = mutableListOf<String>()

        if (document.profiles.isEmpty()) problems += "el catálogo no declara ningún perfil"

        document.profiles.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            problems += "el identificador de perfil «$it» está repetido"
        }
        document.profiles.forEach { entry ->
            if (!profileIdPattern.matches(entry.id)) {
                problems += "el identificador «${entry.id}» no cumple el formato de identificador de perfil"
            }
            if (!isoCodePattern.matches(entry.country)) {
                problems += "perfil «${entry.id}»: country «${entry.country}» no es un código ISO alfa-2 en minúsculas"
            }
            if (entry.displayName.isBlank()) problems += "perfil «${entry.id}»: displayName está vacío"
            if (entry.file.isBlank()) problems += "perfil «${entry.id}»: file está vacío"
        }
        document.profiles.groupBy { it.country }.forEach { (country, entries) ->
            val defaults = entries.count { it.isDefault }
            if (defaults != 1) {
                problems += "el país «$country» declara $defaults perfiles por defecto y debe declarar exactamente uno"
            }
        }

        if (problems.isNotEmpty()) throw ProfileValidationException(sourceName, problems)

        return document.profiles.map { entry ->
            ProfileDescriptor(
                id = entry.id,
                country = entry.country,
                displayName = entry.displayName,
                fileName = entry.file,
                isDefault = entry.isDefault,
            )
        }
    }

    /** Decodifica el documento rechazando claves desconocidas y campos ausentes. */
    private inline fun <reified T> decode(sourceName: String, jsonText: String): T = try {
        json.decodeFromString<T>(jsonText)
    } catch (failure: IllegalArgumentException) {
        throw ProfileValidationException(
            sourceName,
            listOf("el documento no tiene la forma del esquema: ${failure.message ?: "JSON malformado"}"),
        )
    }

    private fun validateBins(bins: List<BinDocument>, problems: MutableList<String>) {
        if (bins.isEmpty()) problems += "bins debe declarar al menos una caneca"

        bins.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            problems += "la caneca «$it» está definida más de una vez"
        }
        bins.forEach { bin ->
            if (!binIdPattern.matches(bin.id)) {
                problems += "el identificador de caneca «${bin.id}» no cumple el formato"
            }
            if (bin.displayName.isBlank()) problems += "la caneca «${bin.id}» no tiene nombre visible"
            if (!colorHexPattern.matches(bin.colorHex)) {
                problems += "la caneca «${bin.id}» tiene color «${bin.colorHex}» fuera del formato #RRGGBB"
            }
            if (DisposalRoute.entries.none { it.name == bin.route }) {
                problems += "la caneca «${bin.id}» declara la ruta desconocida «${bin.route}»"
            }
        }
    }

    private fun validateRules(
        rules: List<MaterialRuleDocument>,
        binIds: Set<String>,
        problems: MutableList<String>,
    ) {
        if (rules.isEmpty()) problems += "rules debe declarar al menos una regla"

        rules.groupBy { it.material }.filterValues { it.size > 1 }.keys.forEach {
            problems += "el material «$it» tiene más de una regla"
        }
        rules.forEach { rule ->
            if (WasteMaterial.entries.none { it.name == rule.material }) {
                problems += "la regla de «${rule.material}» usa un material fuera del vocabulario WasteMaterial"
            }
            if (rule.targetBin !in binIds) {
                problems += "la regla de «${rule.material}» apunta a la caneca inexistente «${rule.targetBin}»"
            }
            val fallback = rule.contaminatedFallback
            if (fallback != null && fallback !in binIds) {
                problems += "la regla de «${rule.material}» degrada a la caneca inexistente «$fallback»"
            }
            if (rule.justification.isBlank()) {
                problems += "la regla de «${rule.material}» no tiene justificación citable"
            }
        }
    }

    private fun validateInspectionRules(
        inspectionRules: List<InspectionRuleDocument>,
        rules: List<MaterialRuleDocument>,
        problems: MutableList<String>,
    ) {
        val ruledMaterials = rules.map { it.material }.toSet()

        inspectionRules.groupBy { it.material }.filterValues { it.size > 1 }.keys.forEach {
            problems += "el material «$it» tiene más de una regla de inspección"
        }
        inspectionRules.forEach { rule ->
            if (WasteMaterial.entries.none { it.name == rule.material }) {
                problems += "la inspección de «${rule.material}» usa un material fuera del vocabulario WasteMaterial"
            } else if (rule.material !in ruledMaterials) {
                problems += "la inspección de «${rule.material}» no tiene regla de material asociada"
            }
            if (!promptKeyPattern.matches(rule.promptKey)) {
                problems += "la inspección de «${rule.material}» tiene promptKey «${rule.promptKey}» fuera de formato"
            }
        }
    }
}

/*
 * Documentos de transporte: reflejan `profile.schema.json` tal cual, con los
 * vocabularios como texto para poder reportar valores desconocidos con un
 * error descriptivo en lugar de una excepción de deserialización genérica.
 */

@Serializable
private data class ProfileDocument(
    val isoCode: String,
    val regulationName: String,
    val regulationReference: String,
    val bins: List<BinDocument>,
    val rules: List<MaterialRuleDocument>,
    val inspectionRules: List<InspectionRuleDocument>,
    val conservativeBin: String,
)

@Serializable
private data class BinDocument(
    val id: String,
    val displayName: String,
    val colorHex: String,
    val route: String,
)

@Serializable
private data class MaterialRuleDocument(
    val material: String,
    val targetBin: String,
    val contaminatedFallback: String? = null,
    val justification: String,
)

@Serializable
private data class InspectionRuleDocument(
    val material: String,
    val promptKey: String,
    val requiresInteriorView: Boolean,
)

@Serializable
private data class CatalogDocument(
    val profiles: List<CatalogEntryDocument>,
)

@Serializable
private data class CatalogEntryDocument(
    val id: String,
    val country: String,
    val displayName: String,
    val file: String,
    @SerialName("default") val isDefault: Boolean,
)
