package geotrellis.raster.io.geotiff

import geotrellis.raster._
import geotrellis.raster.io.geotiff.writer.GeoTiffWriter
import geotrellis.raster.mapalgebra.local._

import geotrellis.vector.Extent

import geotrellis.proj4._

import geotrellis.raster.testkit._

import org.scalatest._

class GeoTiffMultiBandTileSpec extends FunSpec
    with Matchers
    with BeforeAndAfterAll
    with RasterMatchers
    with GeoTiffTestUtils
    with TileBuilders {

  override def afterAll = purge

  describe ("GeoTiffMultiBandTile creation") {

    it("should create GeoTiffMultiBandTile from ArrayMultiBandTile") {
      val original =
        ArrayMultiBandTile(
          ArrayTile(Array.ofDim[Int](15*10).fill(1), 15, 10),
          ArrayTile(Array.ofDim[Int](15*10).fill(2), 15, 10),
          ArrayTile(Array.ofDim[Int](15*10).fill(3), 15, 10)
        )

      val gtm = GeoTiffMultiBandTile(original)

      assertEqual(gtm.band(0), original.band(0))
      assertEqual(gtm.band(1), original.band(1))
      assertEqual(gtm.band(2), original.band(2))
    }

    it("should create GeoTiffMultiBandTile from large Float32 ArrayMultiBandTile for Striped") {
      val original =
        ArrayMultiBandTile(
          ArrayTile(Array.ofDim[Float](150*140).fill(1.0f), 150, 140),
          ArrayTile(Array.ofDim[Float](150*140).fill(2.0f), 150, 140),
          ArrayTile(Array.ofDim[Float](150*140).fill(3.0f), 150, 140)
        )

      val gtm = GeoTiffMultiBandTile(original)

      assertEqual(gtm.band(0), original.band(0))
      assertEqual(gtm.band(1), original.band(1))
      assertEqual(gtm.band(2), original.band(2))
    }

    it("should create GeoTiffMultiBandTile from large Float32 ArrayMultiBandTile for Tiled") {
      val original =
        ArrayMultiBandTile(
          ArrayTile(Array.ofDim[Float](150*140).fill(1.0f), 150, 140),
          ArrayTile(Array.ofDim[Float](150*140).fill(2.0f), 150, 140),
          ArrayTile(Array.ofDim[Float](150*140).fill(3.0f), 150, 140)
        )

      val gtm = GeoTiffMultiBandTile(original, GeoTiffOptions(Tiled(16, 16)))

      assertEqual(gtm.band(0), original.band(0))
      assertEqual(gtm.band(1), original.band(1))
      assertEqual(gtm.band(2), original.band(2))
    }

    it("should create GeoTiffMultiBandTile from large Short ArrayMultiBandTile for Tiled") {
      val original =
        ArrayMultiBandTile(
          RawArrayTile(Array.ofDim[Short](150*140).fill(1.toShort), 150, 140),
          RawArrayTile(Array.ofDim[Short](150*140).fill(2.toShort), 150, 140),
          RawArrayTile(Array.ofDim[Short](150*140).fill(3.toShort), 150, 140)
        )

      val gtm = GeoTiffMultiBandTile(original, GeoTiffOptions(Tiled(32, 32)))

      assertEqual(gtm.band(0), original.band(0))
      assertEqual(gtm.band(1), original.band(1))
      assertEqual(gtm.band(2), original.band(2))
    }

    it("should create GeoTiffMultiBandTile from Double ArrayMultiBandTile for Tiled, write and read and match") {
      val path = "/tmp/geotiff-writer.tif"

      val band1 = ArrayTile( (0 until (3000)).map(_.toDouble).toArray, 50, 60)
      val band2 = ArrayTile( (3000 until (6000)).map(_.toDouble).toArray, 50, 60)
      val band3 = ArrayTile( (6000 until (9000)).map(_.toDouble).toArray, 50, 60)
      val original =
        ArrayMultiBandTile(
          band1,
          band2,
          band3
        )

      val gtm = GeoTiffMultiBandTile(original, GeoTiffOptions(Tiled(16, 16)))
      val geoTiff = MultiBandGeoTiff(gtm, Extent(100.0, 40.0, 120.0, 42.0), LatLng)
      geoTiff.write(path)

      addToPurge(path)

      val actual = MultiBandGeoTiff(path).tile

      assertEqual(actual.band(0), band1)
      assertEqual(actual.band(1), band2)
      assertEqual(actual.band(2), band3)
    }
  }

  describe("MultiBand subset combine methods") {

    it("should work correctly on integer-valued tiles") {
      val tiles = MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-pixel.tif"))
      val band0 = tiles.band(0).toArray
      val band2 = tiles.band(2).toArray
      val actual = tiles.combine(List(0,2))({ seq: Seq[Int] => seq.sum }).toArray
      val expected = band0.zip(band2).map({ pair => pair._1 + pair._2 })

      (actual.zip(expected)).foreach({ pair =>
        assert(pair._1 == pair._2, "actual should equal expected")
      })
    }

    it("should work correctly on double-valued tiles") {
      val original =
        ArrayMultiBandTile(
          ArrayTile(Array.ofDim[Float](150*140).fill(1.5f), 150, 140),
          ArrayTile(Array.ofDim[Float](150*140).fill(2.5f), 150, 140),
          ArrayTile(Array.ofDim[Float](150*140).fill(3.5f), 150, 140))
      val tiles = GeoTiffMultiBandTile(original)
      val band0 = tiles.band(0).toArrayDouble
      val band2 = tiles.band(2).toArrayDouble
      val actual = tiles.combineDouble(List(0,2))({ seq: Seq[Double] => seq.sum }).toArray
      val expected = band0.zip(band2).map({ pair => pair._1 + pair._2 })

      (actual.zip(expected)).foreach({ pair =>
        assert(pair._1 == pair._2, "actual should equal expected")
      })
    }
  }

  describe("MultiBand subset map methods") {

    it("should work correctly on integer-valued tiles") {
      val tiles = MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-pixel.tif"))
      val actual = tiles.map(List(0,2))({ (band,z) => z + band + 3 })
      val expectedBand0 = tiles.band(0).map({ z => z + 0 + 3 }).toArray
      val expectedBand1 = tiles.band(1).toArray
      val expectedBand2 = tiles.band(2).map({ z => z + 2 + 3 }).toArray

      (actual.band(0).toArray.zip(expectedBand0)).foreach({ pair =>
        assert(pair._1 == pair._2, "actual should equal expected in band 0")
      })

      (actual.band(1).toArray.zip(expectedBand1)).foreach({ pair =>
        assert(pair._1 == pair._2, "actual should equal expected in band 1")
      })

      (actual.band(2).toArray.zip(expectedBand2)).foreach({ pair =>
        assert(pair._1 == pair._2, "actual should equal expected in band 2")
      })
    }

    it("should work correctly on double-valued tiles") {
      val original =
        ArrayMultiBandTile(
          ArrayTile(Array.ofDim[Float](150*140).fill(1.5f), 150, 140),
          ArrayTile(Array.ofDim[Float](150*140).fill(2.5f), 150, 140),
          ArrayTile(Array.ofDim[Float](150*140).fill(3.5f), 150, 140))
      val tiles = GeoTiffMultiBandTile(original)
      val actual = tiles.mapDouble(List(0,2))({ (band,z) => z + band + 3.5 })
      val expectedBand0 = tiles.band(0).mapDouble({ z => z + 0 + 3.5 }).toArrayDouble
      val expectedBand1 = tiles.band(1).toArrayDouble
      val expectedBand2 = tiles.band(2).mapDouble({ z => z + 2 + 3.5 }).toArrayDouble

      (actual.band(0).toArrayDouble.zip(expectedBand0)).foreach({ pair =>
        assert(pair._1 == pair._2, "actual should equal expected in band 0")
      })

      (actual.band(1).toArrayDouble.zip(expectedBand1)).foreach({ pair =>
        assert(pair._1 == pair._2, "actual should equal expected in band 1")
      })

      (actual.band(2).toArrayDouble.zip(expectedBand2)).foreach({ pair =>
        assert(pair._1 == pair._2, "actual should equal expected in band 2")
      })
    }
  }

  describe("MultiBand bands (reorder) method") {

    it("should be inexpensive") {
      val tile0 = MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-pixel.tif"))
      val tile1 = tile0.bands(List(1, 2, 0))

      tile0.band(0) should be theSameInstanceAs tile1.band(2)
      tile0.band(1) should be theSameInstanceAs tile1.band(0)
      tile0.band(2) should be theSameInstanceAs tile1.band(1)
    }

    it("result should have correct bandCount") {
      val tile0 = MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-pixel.tif"))
      val tile1 = tile0.bands(List(1, 2, 0))
      val tile2 = tile0.bands(List(1, 2))

      tile1.bandCount should be (3)
      tile2.bandCount should be (2)
    }

    it("result should work properly with foreach") {
      val tile0 = MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-pixel.tif"))
      val tile1 = tile0.bands(List(1, 2, 0))
      val tile2 = tile1.bands(List(1, 2, 0))

      tile0.band(0).foreach { z => z should be (1) }
      tile0.band(1).foreach { z => z should be (2) }
      tile0.band(2).foreach { z => z should be (3) }
      tile1.band(2).foreach { z => z should be (1) }
      tile1.band(0).foreach { z => z should be (2) }
      tile1.band(1).foreach { z => z should be (3) }
      tile2.band(0).foreach { z => z should be (3) }
      tile2.band(1).foreach { z => z should be (1) }
      tile2.band(2).foreach { z => z should be (2) }
    }

    it("should disallow \"invalid\" bandSequences") {
      val tile0 = MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-pixel.tif"))
      an [IllegalArgumentException] should be thrownBy {
        tile0.bands(0,1,2,3) // There are only 3 bands
      }
    }
  }

  describe("MultiBand subset method") {

    it("should be insensative to the order of bandSequence") {
      val tile0 = MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-pixel.tif"))
      val tile1 = tile0.subset(List(1, 2, 0))

      tile0.band(0) should be theSameInstanceAs tile1.band(0)
      tile0.band(1) should be theSameInstanceAs tile1.band(1)
      tile0.band(2) should be theSameInstanceAs tile1.band(2)
    }
  }

  describe("GeoTiffMultiBandTile map") {

    it("should map a single band, striped, pixel interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-pixel.tif")).tile.map(1)(_ + 3)

      tile.band(0).foreach { z => z should be (1) }
      tile.band(1).foreach { z => z should be (5) }
      tile.band(2).foreach { z => z should be (3) }
    }

    it("should map a single band, tiled, pixel interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-tiled-pixel.tif")).tile.map(1)(_ + 3)

      tile.band(0).foreach { z => z should be (1) }
      tile.band(1).foreach { z => z should be (5) }
      tile.band(2).foreach { z => z should be (3) }
    }

    it("should map a single band, striped, band interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-band.tif")).tile.map(1)(_ + 3)

      tile.band(0).foreach { z => z should be (1) }
      tile.band(1).foreach { z => z should be (5) }
      tile.band(2).foreach { z => z should be (3) }
    }

    it("should map a single band, tiled, band interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-tiled-band.tif")).tile.map(1)(_ + 3)

      tile.band(0).foreach { z => z should be (1) }
      tile.band(1).foreach { z => z should be (5) }
      tile.band(2).foreach { z => z should be (3) }
    }

    it("should map over all bands, pixel interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-pixel.tif")).tile.map { (b, z) => b * 10 + z }

      tile.band(0).foreach { z => z should be (1) }
      tile.band(1).foreach { z => z should be (12) }
      tile.band(2).foreach { z => z should be (23) }
    }

    it("should map over all bands, tiled") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-tiled-pixel.tif")).tile.map { (b, z) => ((b+1) * 10) + z }

      tile.band(0).foreach { z => z should be (11) }
      tile.band(1).foreach { z => z should be (22) }
      tile.band(2).foreach { z => z should be (33) }
    }

    it("should mapDouble a single band, striped, pixel interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-pixel.tif")).tile.convert(DoubleConstantNoDataCellType).mapDouble(1)(_ + 3.3)

      tile.band(0).foreach { z => z should be (1) }
      tile.band(1).foreach { z => z should be (5) }
      tile.band(2).foreach { z => z should be (3) }
    }

    it("should mapDouble a single band, tiled, band interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-tiled-band.tif")).tile.convert(DoubleConstantNoDataCellType).mapDouble(1)(_ + 3.3)

      tile.band(0).foreach { z => z should be (1) }
      tile.band(1).foreach { z => z should be (5) }
      tile.band(2).foreach { z => z should be (3) }
    }

  }

  describe("GeoTiffMultiBandTile foreach") {

    it("should foreach a single band, striped, pixel interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-pixel.tif")).tile

      val cellCount = tile.band(1).toArray.size

      var count = 0
      tile.foreach(1) { z =>
        z should be (2)
        count += 1
      }
      count should be (cellCount)
    }

    it("should foreach a single band, tiled, pixel interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-tiled-pixel.tif")).tile

      val cellCount = tile.band(1).toArray.size

      var count = 0
      tile.foreach(1) { z =>
        z should be (2)
        count += 1
      }
      count should be (cellCount)
    }

    it("should foreach a single band, striped, band interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-band.tif")).tile

      val cellCount = tile.band(1).toArray.size

      var count = 0
      tile.foreach(1) { z =>
        z should be (2)
        count += 1
      }
      count should be (cellCount)
    }

    it("should foreach a single band, tiled, band interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-tiled-band.tif")).tile

      val cellCount = tile.band(1).toArray.size

      var count = 0
      tile.foreach(1) { z =>
        z should be (2)
        count += 1
      }
      count should be (cellCount)
    }

    it("should foreachDouble all bands, striped, pixel interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-pixel.tif")).tile

      val cellCount = tile.band(1).toArray.size

      val counts = Array.ofDim[Int](3)
      tile.foreachDouble { (b, z) =>
        z should be (b + 1.0)
        counts(b) += 1
      }

      counts(0)  should be (cellCount)
      counts(1)  should be (cellCount)
      counts(2)  should be (cellCount)
    }

    it("should foreachDouble all bands, tiled, pixel interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-tiled-pixel.tif")).tile

      val cellCount = tile.band(1).toArray.size

      val counts = Array.ofDim[Int](3)
      tile.foreachDouble { (b, z) =>
        z should be (b + 1.0)
        counts(b) += 1
      }

      counts(0)  should be (cellCount)
      counts(1)  should be (cellCount)
      counts(2)  should be (cellCount)
    }

    it("should foreachDouble all bands, striped, band interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-striped-band.tif")).tile

      val cellCount = tile.band(1).toArray.size

      val counts = Array.ofDim[Int](3)
      tile.foreachDouble { (b, z) =>
        z should be (b + 1.0)
        counts(b) += 1
      }

      counts(0)  should be (cellCount)
      counts(1)  should be (cellCount)
      counts(2)  should be (cellCount)
    }

    it("should foreachDouble all bands, tiled, band interleave") {

      val tile =
        MultiBandGeoTiff(geoTiffPath("3bands/int32/3bands-tiled-band.tif")).tile

      val cellCount = tile.band(1).toArray.size

      val counts = Array.ofDim[Int](3)
      tile.foreachDouble { (b, z) =>
        z should be (b + 1.0)
        counts(b) += 1
      }

      counts(0)  should be (cellCount)
      counts(1)  should be (cellCount)
      counts(2)  should be (cellCount)
    }
  }

  describe("GeoTiffMultibandTile combine") {
    val original = IntConstantTile(199, 4, 4)

    def mkGeotiffMultiBandTile(arity: Int) =
      GeoTiffMultiBandTile(ArrayMultiBandTile((0 to arity) map (_ => original)))

    def combineAssert(combined: Tile, arity: Int) = {
      val expected = IntConstantTile(199 * arity, 4, 4)
      assertEqual(combined, expected)
    }

    it("GeoTiffMultibandTile combine function test: arity 2") {
      val arity = 2
      val combined = mkGeotiffMultiBandTile(arity).combine(0, 1) { case (b0, b1) => b0 + b1 }
      combineAssert(combined, arity)
    }

    it("GeoTiffMultibandTile combine function test: arity 3") {
      val arity = 3
      val combined = mkGeotiffMultiBandTile(arity).combine(0, 1, 2) { case (b0, b1, b2) => b0 + b1 + b2 }
      combineAssert(combined, arity)
    }

    it("GeoTiffMultibandTile combine function test: arity 4") {
      val arity = 4
      val combined = mkGeotiffMultiBandTile(arity).combine(0, 1, 2, 3) {
        case (b0, b1, b2, b3) => b0 + b1 + b2 + b3
      }
      combineAssert(combined, arity)
    }

    it("GeoTiffMultibandTile combine function test: arity 5") {
      val arity = 5
      val combined = mkGeotiffMultiBandTile(arity).combine(0, 1, 2, 3, 4) {
        case (b0, b1, b2, b3, b4) => b0 + b1 + b2 + b3 + b4
      }
      combineAssert(combined, arity)
    }

    it("GeoTiffMultibandTile combine function test: arity 6") {
      val arity = 6
      val combined = mkGeotiffMultiBandTile(arity).combine(0, 1, 2, 3, 4, 5) {
        case (b0, b1, b2, b3, b4, b5) => b0 + b1 + b2 + b3 + b4 + b5
      }
      combineAssert(combined, arity)
    }

    it("GeoTiffMultibandTile combine function test: arity 7") {
      val arity = 7
      val combined = mkGeotiffMultiBandTile(arity).combine(0, 1, 2, 3, 4, 5, 6) {
        case (b0, b1, b2, b3, b4, b5, b6) => b0 + b1 + b2 + b3 + b4 + b5 + b6
      }
      combineAssert(combined, arity)
    }

    it("GeoTiffMultibandTile combine function test: arity 8") {
      val arity = 8
      val combined = mkGeotiffMultiBandTile(arity).combine(0, 1, 2, 3, 4, 5, 6, 7) {
        case (b0, b1, b2, b3, b4, b5, b6, b7) => b0 + b1 + b2 + b3 + b4 + b5 + b6 + b7
      }
      combineAssert(combined, arity)
    }

    it("GeoTiffMultibandTile combine function test: arity 9") {
      val arity = 9
      val combined = mkGeotiffMultiBandTile(arity).combine(0, 1, 2, 3, 4, 5, 6, 7, 8) {
        case (b0, b1, b2, b3, b4, b5, b6, b7, b8) => b0 + b1 + b2 + b3 + b4 + b5 + b6 + b7 + b8
      }
      combineAssert(combined, arity)
    }

    it("GeoTiffMultibandTile combine function test: arity 10") {
      val arity = 10
      val combined = mkGeotiffMultiBandTile(arity).combine(0, 1, 2, 3, 4, 5, 6, 7, 8, 9) {
        case (b0, b1, b2, b3, b4, b5, b6, b7, b8, b9) => b0 + b1 + b2 + b3 + b4 + b5 + b6 + b7 + b8 + b9
      }
      combineAssert(combined, arity)
    }
  }

}
