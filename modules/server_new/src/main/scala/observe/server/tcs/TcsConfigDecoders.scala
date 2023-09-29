// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import cats.syntax.all.*
import edu.gemini.observe.server.tcs.BinaryOnOff
import edu.gemini.observe.server.tcs.BinaryYesNo
import observe.model.M1GuideConfig
import observe.model.M2GuideConfig
import observe.model.enums.ComaOption
import observe.model.enums.M1Source
import observe.model.enums.MountGuideOption
import observe.model.enums.TipTiltSource
import observe.server.EpicsCodex.DecodeEpicsValue
import observe.server.tcs.TcsController.FollowOption.FollowOff
import observe.server.tcs.TcsController.FollowOption.FollowOn
import observe.server.tcs.TcsController.*

trait TcsConfigDecoders {
  // Code to retrieve the current configuration from TCS. Include a lot of decoders
  given DecodeEpicsValue[Int, MountGuideOption] =
    DecodeEpicsValue((d: Int) =>
      if (d === 0) MountGuideOption.MountGuideOff else MountGuideOption.MountGuideOn
    )

  given DecodeEpicsValue[String, M1Source] =
    DecodeEpicsValue((s: String) =>
      s.trim match {
        case "PWFS1" => M1Source.PWFS1
        case "PWFS2" => M1Source.PWFS2
        case "OIWFS" => M1Source.OIWFS
        case "GAOS"  => M1Source.GAOS
        case _       => M1Source.PWFS1
      }
    )

  def decodeM1Guide(r: BinaryOnOff, s: M1Source): M1GuideConfig =
    if (r === BinaryOnOff.Off) M1GuideConfig.M1GuideOff
    else M1GuideConfig.M1GuideOn(s)

  def decodeGuideSourceOption(s: String): Boolean = s.trim =!= "OFF"

  given DecodeEpicsValue[String, ComaOption] =
    DecodeEpicsValue((s: String) => if (s.trim === "Off") ComaOption.ComaOff else ComaOption.ComaOn)

  def decodeM2Guide(s: BinaryOnOff, u: ComaOption, v: Set[TipTiltSource]): M2GuideConfig =
    if (s === BinaryOnOff.Off) M2GuideConfig.M2GuideOff
    else M2GuideConfig.M2GuideOn(u, v)

  given DecodeEpicsValue[String, AoFold] = DecodeEpicsValue((s: String) =>
    if (s.trim === "IN") AoFold.In
    else AoFold.Out
  )

  given DecodeEpicsValue[String, FollowOption] =
    DecodeEpicsValue((s: String) => if (s.trim === "Off") FollowOff else FollowOn)

  given DecodeEpicsValue[BinaryYesNo, GuiderSensorOption] =
    DecodeEpicsValue((s: BinaryYesNo) =>
      if (s === BinaryYesNo.No) GuiderSensorOff else GuiderSensorOn
    )

  given DecodeEpicsValue[String, HrwfsPickupPosition] =
    DecodeEpicsValue((t: String) =>
      if (t.trim === "IN") HrwfsPickupPosition.IN
      else HrwfsPickupPosition.OUT
    )

}
