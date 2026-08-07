package com.recycol.rules.bins

import com.recycol.domain.model.DetectedBin
import kotlin.math.max
import kotlin.math.min

/**
 * Región de color dominante encontrada en un frame durante el escaneo de
 * canecas (RF-005, CUS-002).
 *
 * @property colorHex color promedio de la región en `#RRGGBB`.
 * @property coverage fracción del frame que ocupa la región, en `[0, 1]`.
 * @property cohesion fracción de las celdas de la región cercanas a su color
 *   promedio: mide qué tan uniforme es el color, en `[0, 1]`.
 */
data class ColorRegion(
    val colorHex: String,
    val coverage: Float,
    val cohesion: Float,
) {

    /**
     * Traduce la región a la [DetectedBin] del contrato M0. La confianza crece
     * con el área hasta [ColorRegionFinder.FULL_CONFIDENCE_COVERAGE] y se
     * pondera por la uniformidad del color.
     */
    fun toDetectedBin(): DetectedBin = DetectedBin(
        colorHex = colorHex,
        confidence = (cohesion * min(1f, coverage / ColorRegionFinder.FULL_CONFIDENCE_COVERAGE))
            .coerceIn(0f, 1f),
    )
}

/**
 * Detección de regiones de color dominantes sobre un búfer de píxeles ARGB.
 *
 * Es el núcleo del detector de canecas: aritmética pura y determinista, sin
 * dependencias de plataforma (RNF-005), de modo que Android e iOS la comparten
 * y las pruebas la ejercitan con frames sintéticos bajo distintas condiciones
 * de luz. El algoritmo promedia celdas de una rejilla, une celdas vecinas de
 * color cercano (matiz estable frente a la iluminación) y reporta las regiones
 * de mayor área.
 *
 * El búfer se recibe y se descarta: nunca se persiste ni se registra (RNF-012).
 */
object ColorRegionFinder {

    /** Cobertura a partir de la cual una región alcanza confianza plena. */
    const val FULL_CONFIDENCE_COVERAGE: Float = 0.12f

    private const val GRID_RESOLUTION = 32
    private const val MIN_CELL_SIZE = 4
    private const val MERGE_TOLERANCE = 0.12f
    private const val MIN_COVERAGE = 0.02f
    private const val MAX_REGIONS = 8
    private const val SAMPLE_STRIDE = 2

    /**
     * Encuentra las regiones de color dominantes del frame.
     *
     * @param pixels búfer ARGB en orden de filas, de tamaño `width * height`.
     * @param width ancho del frame en píxeles.
     * @param height alto del frame en píxeles.
     * @return regiones ordenadas por área descendente, como máximo [MAX_REGIONS],
     *   cada una con cobertura mínima de [MIN_COVERAGE].
     */
    fun findRegions(pixels: IntArray, width: Int, height: Int): List<ColorRegion> {
        require(pixels.size == width * height) {
            "El búfer tiene ${pixels.size} píxeles y el frame declara ${width}x$height"
        }
        if (width == 0 || height == 0) return emptyList()

        val cellSize = max(MIN_CELL_SIZE, min(width, height) / GRID_RESOLUTION)
        val columns = max(1, width / cellSize)
        val rows = max(1, height / cellSize)
        val cellColors = averageCells(pixels, width, height, cellSize, columns, rows)

        val components = mergeNeighborCells(cellColors, columns, rows)

        return components.values.asSequence()
            .map { cells -> toRegion(cells, cellColors, columns * rows) }
            .filter { it.coverage >= MIN_COVERAGE }
            .sortedByDescending { it.coverage }
            .take(MAX_REGIONS)
            .toList()
    }

    /** Color promedio (RGB empaquetado sin alfa) de cada celda de la rejilla. */
    private fun averageCells(
        pixels: IntArray,
        width: Int,
        height: Int,
        cellSize: Int,
        columns: Int,
        rows: Int,
    ): IntArray {
        val cellColors = IntArray(columns * rows)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                var red = 0L
                var green = 0L
                var blue = 0L
                var count = 0
                val yEnd = min(height, (row + 1) * cellSize)
                val xEnd = min(width, (column + 1) * cellSize)
                var y = row * cellSize
                while (y < yEnd) {
                    var x = column * cellSize
                    while (x < xEnd) {
                        val pixel = pixels[y * width + x]
                        red += (pixel shr 16) and 0xFF
                        green += (pixel shr 8) and 0xFF
                        blue += pixel and 0xFF
                        count++
                        x += SAMPLE_STRIDE
                    }
                    y += SAMPLE_STRIDE
                }
                cellColors[row * columns + column] =
                    (((red / count).toInt()) shl 16) or
                        (((green / count).toInt()) shl 8) or
                        (blue / count).toInt()
            }
        }
        return cellColors
    }

    /** Une celdas vecinas de color cercano con unión-búsqueda sobre la rejilla. */
    private fun mergeNeighborCells(
        cellColors: IntArray,
        columns: Int,
        rows: Int,
    ): Map<Int, List<Int>> {
        val parent = IntArray(cellColors.size) { it }

        fun find(cell: Int): Int {
            var root = cell
            while (parent[root] != root) {
                parent[root] = parent[parent[root]]
                root = parent[root]
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val hsvA = ColorSpace.fromArgb(cellColors[a])
            val hsvB = ColorSpace.fromArgb(cellColors[b])
            if (ColorSpace.distance(hsvA, hsvB) < MERGE_TOLERANCE) {
                parent[find(a)] = find(b)
            }
        }

        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val cell = row * columns + column
                if (column + 1 < columns) union(cell, cell + 1)
                if (row + 1 < rows) union(cell, cell + columns)
            }
        }

        return cellColors.indices.groupBy { find(it) }
    }

    private fun toRegion(cells: List<Int>, cellColors: IntArray, totalCells: Int): ColorRegion {
        var red = 0L
        var green = 0L
        var blue = 0L
        cells.forEach { cell ->
            val color = cellColors[cell]
            red += (color shr 16) and 0xFF
            green += (color shr 8) and 0xFF
            blue += color and 0xFF
        }
        val averageRed = (red / cells.size).toInt()
        val averageGreen = (green / cells.size).toInt()
        val averageBlue = (blue / cells.size).toInt()

        val averageHsv = ColorSpace.fromRgb(averageRed, averageGreen, averageBlue)
        val cohesive = cells.count { cell ->
            ColorSpace.distance(ColorSpace.fromArgb(cellColors[cell]), averageHsv) < MERGE_TOLERANCE
        }

        return ColorRegion(
            colorHex = ColorSpace.toHex(averageRed, averageGreen, averageBlue),
            coverage = cells.size.toFloat() / totalCells,
            cohesion = cohesive.toFloat() / cells.size,
        )
    }
}
