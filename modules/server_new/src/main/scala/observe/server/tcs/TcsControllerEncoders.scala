// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.tcs

import lucuma.core.enums.ComaOption
import lucuma.core.enums.MountGuideOption
import lucuma.core.model.M1GuideConfig
import lucuma.core.model.M2GuideConfig
import observe.server.EpicsCodex.EncodeEpicsValue
import observe.server.tcs.TcsController.*

trait TcsControllerEncoders {
  // Encoders
  given EncodeEpicsValue[MountGuideOption, String] =
    EncodeEpicsValue {
      case MountGuideOption.MountGuideOn  => "on"
      case MountGuideOption.MountGuideOff => "off"
    }

  given EncodeEpicsValue[M1GuideConfig, String] =
    EncodeEpicsValue {
      case M1GuideConfig.M1GuideOn(_) => "on"
      case M1GuideConfig.M1GuideOff   => "off"
    }

  val encodeM2Guide: EncodeEpicsValue[M2GuideConfig, String] =
    EncodeEpicsValue {
      case M2GuideConfig.M2GuideOn(_, _) => "on"
      case M2GuideConfig.M2GuideOff      => "off"
    }

  val encodeM2Coma: EncodeEpicsValue[M2GuideConfig, String] =
    EncodeEpicsValue {
      case M2GuideConfig.M2GuideOn(ComaOption.ComaOn, _) => "on"
      case _                                             => "off"
    }

  val encodeM2GuideReset: EncodeEpicsValue[M2GuideConfig, String] =
    EncodeEpicsValue {
      case M2GuideConfig.M2GuideOn(_, _) => "off"
      case M2GuideConfig.M2GuideOff      => "on"
    }

  given EncodeEpicsValue[NodChopTrackingOption, String] =
    EncodeEpicsValue {
      case NodChopTrackingOption.NodChopTrackingOn  => "On"
      case NodChopTrackingOption.NodChopTrackingOff => "Off"
    }

  given EncodeEpicsValue[FollowOption, String] =
    EncodeEpicsValue {
      case FollowOption.FollowOn  => "On"
      case FollowOption.FollowOff => "Off"
    }

  given EncodeEpicsValue[HrwfsPickupPosition, String] =
    EncodeEpicsValue {
      case HrwfsPickupPosition.IN     => "IN"
      case HrwfsPickupPosition.OUT    => "OUT"
      case HrwfsPickupPosition.Parked => "park-pos."
    }

}
