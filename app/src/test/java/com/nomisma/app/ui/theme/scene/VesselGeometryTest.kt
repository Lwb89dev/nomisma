package com.nomisma.app.ui.theme.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VesselGeometryTest {

    @Test
    fun `a full vessel spills and a half vessel does not`() {
        assertTrue(VesselGeometry.spillsOver(1f))
        assertTrue(VesselGeometry.spillsOver(0.95f))
        assertFalse(VesselGeometry.spillsOver(0.5f))
        assertFalse(VesselGeometry.spillsOver(0.85f))
    }

    @Test
    fun `the surface wave collapses at the lip and stays full lower down`() {
        assertEquals(0f, VesselGeometry.lipRoom(1f), 0.001f)
        assertEquals(1f, VesselGeometry.lipRoom(0.5f), 0.001f)
        assertEquals(1f, VesselGeometry.lipRoom(0f), 0.001f)
    }

    @Test
    fun `the full surface is still a real span inside the neck`() {
        val surface = VesselGeometry.surfaceV(1f)
        val (left, right) = VesselGeometry.spanAtV(surface)
        assertTrue(surface > VesselGeometry.FILL_TOP_V)
        assertTrue(right - left > 0.15f)
    }

    @Test
    fun `overflow drops leave the spout and stay left of the belly`() {
        val start = VesselGeometry.overflowDropUv(0f)
        val end = VesselGeometry.overflowDropUv(1f)
        assertEquals(VesselGeometry.SPOUT_U, start.first, 0.001f)
        assertEquals(VesselGeometry.SPOUT_V, start.second, 0.001f)
        assertTrue(end.first < start.first)
        assertTrue(end.second > start.second)
        // pitcher.webp belly crosses u=0.20 near v=0.50. The drop must finish
        // above that and left of the neck, or it paints across the glass.
        assertTrue(end.first < 0.18f)
        assertTrue(end.second < 0.45f)
        val mid = VesselGeometry.overflowDropUv(0.5f)
        assertTrue(mid.first < VesselGeometry.SPOUT_U)
        assertTrue(mid.first > end.first)
    }
}
