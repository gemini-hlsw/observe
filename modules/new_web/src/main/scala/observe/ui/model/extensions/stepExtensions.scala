// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.extensions

import cats.Monoid
import cats.syntax.all.*
import lucuma.core.enums.Instrument
import lucuma.core.math.Offset
import observe.model.ExecutionStep
import observe.model.Enumerations
import observe.model.OffsetConfigResolver
import observe.model.enums.ExecutionStepType
import observe.model.enums.Guiding
import observe.model.optics.*
import observe.ui.model.formatting.*
import lucuma.core.enums.GpiDisperser
import lucuma.core.enums.GpiFilter
import lucuma.core.enums.GpiObservingMode

private val gpiObsMode = GpiObservingMode.all.map(x => x.shortName -> x).toMap

private val gpiDispersers: Map[String, String] =
  GpiDisperser.all.map(x => x.shortName -> x.longName).toMap

private val gpiFiltersMap: Map[String, GpiFilter] =
  GpiFilter.all.map(x => (x.shortName, x)).toMap

extension (s: ExecutionStep)

  def fpuNameMapper(i: Instrument): String => Option[String] =
    i match
      case Instrument.GmosSouth => Enumerations.Fpu.GmosSFPU.get
      case Instrument.GmosNorth => Enumerations.Fpu.GmosNFPU.get
      case _                    => _ => none

  def exposureTime: Option[Double] = observeExposureTimeO.getOption(s)

  def exposureTimeS(i: Instrument): Option[String] =
    exposureTime.map(formatExposureTime(i))

  def exposureAndCoaddsS(i: Instrument): Option[String] =
    (coAdds, exposureTime) match {
      case (c, Some(e)) if c.exists(_ > 1) =>
        s"${c.foldMap(_.show)}x${formatExposureTime(i)(e)} s".some
      case (_, Some(e))                    =>
        s"${formatExposureTime(i)(e)} s".some
      case _                               => none
    }

  def coAdds: Option[Int] = observeCoaddsO.getOption(s)

  def fpu(i: Instrument): Option[String] =
    (i, instrumentFPUO.getOption(s), instrumentFPUCustomMaskO.getOption(s)) match
      case (
            Instrument.GmosSouth | Instrument.GmosNorth | Instrument.Flamingos2,
            Some("CUSTOM_MASK"),
            c
          ) =>
        c
      case (
            Instrument.GmosSouth | Instrument.GmosNorth | Instrument.Flamingos2,
            None,
            c @ Some(_)
          ) =>
        c
      case (Instrument.Flamingos2, Some("Custom Mask"), c) => c
      case (Instrument.Flamingos2, a @ Some(_), _)         => a
      case (_, Some(b), _)                                 => fpuNameMapper(i)(b)
      case _                                               => none

  def fpuOrMask(i: Instrument): Option[String] =
    fpu(i)
      .orElse(instrumentSlitWidthO.getOption(s))
      .orElse(instrumentMaskO.getOption(s))

  def alignAndCalib(i: Instrument): Option[ExecutionStepType.AlignAndCalib.type] =
    i match {
      case Instrument.Gpi if stepClassO.getOption(s).exists(_ === "acq") =>
        ExecutionStepType.AlignAndCalib.some
      case _                                                             => none
    }

  def nodAndShuffle(i: Instrument): Option[ExecutionStepType] =
    i match {
      case Instrument.GmosSouth | Instrument.GmosNorth
          if isNodAndShuffleO.getOption(s).exists(identity) =>
        stepTypeO.getOption(s) match {
          case Some(ExecutionStepType.Dark) => ExecutionStepType.NodAndShuffleDark.some
          case _                            => ExecutionStepType.NodAndShuffle.some
        }
      case _ => none
    }

  def stepType(instrument: Instrument): Option[ExecutionStepType] =
    alignAndCalib(instrument)
      .orElse(nodAndShuffle(instrument))
      .orElse(stepTypeO.getOption(s))

  private def gpiFilter: ExecutionStep => Option[String] =
    s => // Read the filter, if not found deduce it from the obs mode
      val f: Option[GpiFilter] =
        instrumentFilterO.getOption(s).flatMap(gpiFiltersMap.get).orElse {
          for {
            m <- instrumentObservingModeO.getOption(s)
            o <- gpiObsMode.get(m)
            f <- o.filter
          } yield f
        }
      f.map(_.longName)

  def filter(i: Instrument): Option[String] =
    i match
      case Instrument.GmosSouth  =>
        instrumentFilterO
          .getOption(s)
          .flatMap(Enumerations.Filter.GmosSFilter.get)
      case Instrument.GmosNorth  =>
        instrumentFilterO
          .getOption(s)
          .flatMap(Enumerations.Filter.GmosNFilter.get)
      case Instrument.Flamingos2 =>
        instrumentFilterO
          .getOption(s)
      case Instrument.Niri       =>
        instrumentFilterO
          .getOption(s)
          .flatMap(Enumerations.Filter.Niri.get)
      case Instrument.Gnirs      =>
        instrumentFilterO
          .getOption(s)
          .map(_.toLowerCase.capitalize)
      case Instrument.Nifs       =>
        instrumentFilterO
          .getOption(s)
          .map(_.toLowerCase.capitalize)
      case Instrument.Gsaoi      =>
        instrumentFilterO
          .getOption(s)
          .map(_.toLowerCase.capitalize)
      case Instrument.Gpi        => gpiFilter(s)
      case _                     => None

  private def disperserNameMapper(i: Instrument): Map[String, String] =
    i match {
      case Instrument.GmosSouth => Enumerations.Disperser.GmosSDisperser
      case Instrument.GmosNorth => Enumerations.Disperser.GmosNDisperser
      case Instrument.Gpi       => gpiDispersers
      case _                    => Map.empty
    }

  def disperser(i: Instrument): Option[String] =
    val disperser =
      for disperser <- instrumentDisperserO.getOption(s)
      yield disperserNameMapper(i).getOrElse(disperser, disperser)

    val centralWavelength = instrumentDisperserLambdaO.getOption(s)

    // Format
    (disperser, centralWavelength) match
      case (Some(d), Some(w)) => f"$d @ $w%.0f nm".some
      case (Some(d), None)    => d.some
      case _                  => none

  def offset[T, A](using
    resolver: OffsetConfigResolver[T, A],
    m:        Monoid[Offset.Component[A]]
  ): Offset.Component[A] =
    offsetF[T, A].fold(s).orEmpty

  def guiding: Boolean = telescopeGuidingWithT.exist(_ === Guiding.Guide)(s)

  //  def readMode: Option[String] = instrumentReadModeO.getOption(s)

  //  def observingMode: Option[String] =
  //    instrumentObservingModeO
  //      .getOption(s)
  //      .flatMap(obsNames.get)

  //  def cameraName(i: Instrument): Option[String] =
  //    i match {
  //      case Instrument.Niri =>
  //        instrumentCameraO
  //          .getOption(s)
  //          .flatMap(enumerations.camera.Niri.get)
  //      case _               => None
  //    }

  //  def deckerName: Option[String] =
  //    instrumentDeckerO.getOption(s)

  //  def imagingMirrorName: Option[String] =
  //    instrumentImagingMirrorO.getOption(s)
