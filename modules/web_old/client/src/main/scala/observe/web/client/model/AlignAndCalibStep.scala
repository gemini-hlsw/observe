// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model

import lucuma.core.util.Enumerated

sealed abstract class AlignAndCalibStep(val tag: String) extends Product with Serializable

object AlignAndCalibStep {
  case object NoAction           extends AlignAndCalibStep("NoAction")
  case object StartGuiding       extends AlignAndCalibStep("StartGuiding")
  case object StopGuiding        extends AlignAndCalibStep("StopGuiding")
  case object SuperContOff       extends AlignAndCalibStep("SuperContOff")
  case object OMSSEntShutterOff  extends AlignAndCalibStep("OMSSEntShutterOff")
  case object CalExistShutterOff extends AlignAndCalibStep("CalExistShutterOff")
  case object ArtSourceDeploy    extends AlignAndCalibStep("ArtSourceDeploy")
  case object AoDarks            extends AlignAndCalibStep("AoDarks")
  case object SuperContOn        extends AlignAndCalibStep("SuperContOn")
  case object CalFlags           extends AlignAndCalibStep("CalFlags")
  case object Twt2Lens           extends AlignAndCalibStep("Twt2Lens")
  case object CalExitShutterOn   extends AlignAndCalibStep("CalExitShutterOn")
  case object ArtSourceExtract   extends AlignAndCalibStep("ArtSourceExtract")
  case object OMSSEntShutterOn   extends AlignAndCalibStep("OMSSEntShutterOn")
  case object InputFoldTracking  extends AlignAndCalibStep("InputFoldTracking")
  case object Done               extends AlignAndCalibStep("Done")

  /** @group Typeclass Instances */
  given Enumerated[AlignAndCalibStep] =
    Enumerated.from(
      NoAction,
      StartGuiding,
      StopGuiding,
      SuperContOff,
      OMSSEntShutterOff,
      CalExistShutterOff,
      ArtSourceDeploy,
      AoDarks,
      SuperContOn,
      CalFlags,
      Twt2Lens,
      CalExitShutterOn,
      ArtSourceExtract,
      OMSSEntShutterOn,
      InputFoldTracking,
      Done
    ).withTag(_.tag)

  def fromInt(i: Int): AlignAndCalibStep =
    i match {
      case 0  => StartGuiding
      case 1  => StopGuiding
      case 2  => SuperContOff
      case 3  => OMSSEntShutterOff
      case 4  => CalExistShutterOff
      case 5  => ArtSourceDeploy
      case 6  => AoDarks
      case 7  => SuperContOn
      case 8  => CalFlags
      case 9  => Twt2Lens
      case 10 => CalExitShutterOn
      case 11 => ArtSourceExtract
      case 12 => OMSSEntShutterOn
      case 13 => InputFoldTracking
      case 14 => Done
      case _  => NoAction
    }
}
