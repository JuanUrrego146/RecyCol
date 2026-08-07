package com.botabien.rules.profile

import com.botabien.domain.model.CountryProfile

/**
 * Catálogo de perfiles normativos disponibles (RF-002, RF-004, CUS-001).
 *
 * Resuelve el índice `catalog.json` y carga perfiles individuales desde un
 * [ProfileSource]. Ningún método lanza: todo error se devuelve como
 * [Result.failure] con una [ProfileValidationException] descriptiva, de modo
 * que un perfil inválido se rechaza sin tumbar la aplicación y el consumidor
 * conserva el perfil que ya tenía activo.
 *
 * El catálogo no guarda estado: releer los archivos es barato y determinista.
 * Cachear el perfil activo es responsabilidad del repositorio de perfiles
 * (agente DATA), no de este componente.
 */
class ProfileCatalog(
    private val source: ProfileSource,
    private val catalogFileName: String = CATALOG_FILE_NAME,
) {

    /** Perfiles registrados en el catálogo, en el orden del archivo. */
    fun descriptors(): Result<List<ProfileDescriptor>> = runCatching {
        val text = source.read(catalogFileName)
            ?: throw ProfileValidationException(
                catalogFileName,
                listOf("el índice del catálogo no existe en los recursos"),
            )
        ProfileParser.parseCatalog(catalogFileName, text)
    }

    /**
     * Carga y valida el perfil registrado con [profileId] (por ejemplo `"co"`
     * o `"co-gtc24"`). Un identificador no registrado, un archivo ausente o
     * un perfil inválido devuelven un fallo explícito sin efectos laterales.
     */
    fun load(profileId: String): Result<CountryProfile> = descriptors().mapCatching { entries ->
        val descriptor = entries.firstOrNull { it.id == profileId }
            ?: throw ProfileValidationException(
                catalogFileName,
                listOf("el perfil «$profileId» no está registrado en el catálogo"),
            )
        val text = source.read(descriptor.fileName)
            ?: throw ProfileValidationException(
                descriptor.fileName,
                listOf("el archivo del perfil «$profileId» no existe en los recursos"),
            )
        val profile = ProfileParser.parseProfile(descriptor.fileName, text)
        if (profile.isoCode != descriptor.country) {
            throw ProfileValidationException(
                descriptor.fileName,
                listOf(
                    "el perfil declara isoCode «${profile.isoCode}» pero el catálogo " +
                        "lo registra bajo el país «${descriptor.country}»",
                ),
            )
        }
        profile
    }

    /** Perfil por defecto de un país; cada país declara exactamente uno. */
    fun defaultFor(country: String): Result<ProfileDescriptor> = descriptors().mapCatching { entries ->
        entries.firstOrNull { it.country == country && it.isDefault }
            ?: throw ProfileValidationException(
                catalogFileName,
                listOf("el país «$country» no tiene perfil por defecto registrado"),
            )
    }

    companion object {
        /** Nombre del índice del catálogo dentro de los recursos de perfiles. */
        const val CATALOG_FILE_NAME: String = "catalog.json"
    }
}
