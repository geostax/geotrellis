package geotrellis.raster.io.geotiff

import geotrellis.raster._
import geotrellis.raster.io.geotiff.compression._

import java.nio.ByteBuffer

class Float32GeoTiffMultiBandTile(
  compressedBytes: Array[Array[Byte]],
  decompressor: Decompressor,
  segmentLayout: GeoTiffSegmentLayout,
  compression: Compression,
  bandCount: Int,
  hasPixelInterleave: Boolean,
  val noDataValue: Option[Double]
) extends GeoTiffMultiBandTile(compressedBytes, decompressor, segmentLayout, compression, bandCount, hasPixelInterleave)
    with Float32GeoTiffSegmentCollection {

  protected def createSegmentCombiner(targetSize: Int): SegmentCombiner =
    new SegmentCombiner {
      private val arr = Array.ofDim[Float](targetSize)

      def set(targetIndex: Int, v: Int): Unit = {
        arr(targetIndex) = i2f(v)
      }

      def setDouble(targetIndex: Int, v: Double): Unit = {
        arr(targetIndex) = d2f(v)
      }

      def getBytes(): Array[Byte] = {
        val result = new Array[Byte](targetSize * TypeFloat.bytes)
        val bytebuff = ByteBuffer.wrap(result)
        bytebuff.asFloatBuffer.put(arr)
        result
      }
    }
}

