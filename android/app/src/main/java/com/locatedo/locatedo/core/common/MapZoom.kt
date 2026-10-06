package com.locatedo.locatedo.core.common

import kotlin.math.log2

private const val BASE_ZOOM = 16.0
private const val BASE_RADIUS_METERS = 100.0

// Keeps a reminder circle at roughly the same size on screen whatever its radius.
fun zoomForRadius(radiusMeters: Double): Float = (BASE_ZOOM - log2(radiusMeters / BASE_RADIUS_METERS)).toFloat()
