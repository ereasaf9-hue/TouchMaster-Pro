package com.touchmaster.pro.gesture

/** Saf koordinat doğrulama ve normalizasyon yardımcıları. */
object CoordinateValidator {
    data class Point(val x: Float, val y: Float)
    fun validate(x: Float, y: Float, width: Int, height: Int): Result<Point> = runCatching {
        require(width > 0 && height > 0) { "Ekran ölçüsü geçersiz." }
        require(x >= 0 && y >= 0 && x < width && y < height) { "Koordinat ekran sınırlarının dışında." }
        Point(x, y)
    }
    fun normalize(x: Float, y: Float, width: Int, height: Int): Point {
        require(width > 0 && height > 0) { "Ekran ölçüsü geçersiz." }
        return Point(x / width, y / height)
    }
    fun denormalize(point: Point, width: Int, height: Int): Point {
        require(width > 0 && height > 0) { "Ekran ölçüsü geçersiz." }
        return Point(point.x * width, point.y * height)
    }
}
