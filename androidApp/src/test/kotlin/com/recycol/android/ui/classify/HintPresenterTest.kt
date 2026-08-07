package com.recycol.android.ui.classify

import com.recycol.domain.model.CaptureHint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pruebas de la política de presentación de indicaciones (RF-017, RF-018):
 * una indicación a la vez, sustitución solo tras el intervalo mínimo y
 * retirada sin parpadeo.
 */
class HintPresenterTest {

    private var now = 0L
    private val presenter = HintPresenter(clock = { now })

    @Test
    fun `la primera indicacion se muestra de inmediato`() {
        presenter.offer(listOf(CaptureHint.MORE_LIGHT))
        assertEquals(CaptureHint.MORE_LIGHT, presenter.visible)
    }

    @Test
    fun `una indicacion distinta no sustituye antes del intervalo`() {
        presenter.offer(listOf(CaptureHint.MORE_LIGHT))
        now += HintPresenter.MIN_INTERVAL_MS - 1
        presenter.offer(listOf(CaptureHint.MOVE_CLOSER))
        assertEquals(CaptureHint.MORE_LIGHT, presenter.visible)
    }

    @Test
    fun `una indicacion distinta sustituye cumplido el intervalo`() {
        presenter.offer(listOf(CaptureHint.MORE_LIGHT))
        now += HintPresenter.MIN_INTERVAL_MS
        presenter.offer(listOf(CaptureHint.MOVE_CLOSER))
        assertEquals(CaptureHint.MOVE_CLOSER, presenter.visible)
    }

    @Test
    fun `la misma indicacion se mantiene sin reiniciar el reloj`() {
        presenter.offer(listOf(CaptureHint.MORE_LIGHT))
        now += HintPresenter.MIN_INTERVAL_MS - 1
        presenter.offer(listOf(CaptureHint.MORE_LIGHT))
        now += 1
        presenter.offer(listOf(CaptureHint.MOVE_CLOSER))
        assertEquals(CaptureHint.MOVE_CLOSER, presenter.visible)
    }

    @Test
    fun `con calidad suficiente la indicacion se retira tras su permanencia minima`() {
        presenter.offer(listOf(CaptureHint.MORE_LIGHT))
        now += HintPresenter.MIN_VISIBLE_MS - 1
        presenter.offer(emptyList())
        assertEquals(CaptureHint.MORE_LIGHT, presenter.visible)
        now += 1
        presenter.offer(emptyList())
        assertNull(presenter.visible)
    }

    @Test
    fun `la directiva de inspeccion interior entra sin esperar el intervalo`() {
        presenter.offer(listOf(CaptureHint.MORE_LIGHT))
        now += 1
        presenter.offer(listOf(CaptureHint.POINT_INSIDE))
        assertEquals(CaptureHint.POINT_INSIDE, presenter.visible)
    }

    @Test
    fun `sin indicaciones no se muestra nada`() {
        presenter.offer(emptyList())
        assertNull(presenter.visible)
    }
}
