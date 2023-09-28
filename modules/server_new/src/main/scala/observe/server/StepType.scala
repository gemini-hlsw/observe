// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.*
import cats.syntax.all.*
import lucuma.core.model.sequence.StepConfig
import observe.model.enums.Instrument

sealed trait StepType extends Product with Serializable {
  def instrument: Instrument
}

object StepType {
  final case class CelestialObject(override val instrument: Instrument)     extends StepType
  final case class NodAndShuffle(override val instrument: Instrument)       extends StepType
  final case class Gems(override val instrument: Instrument)                extends StepType
  final case class AltairObs(override val instrument: Instrument)           extends StepType
  // Flats or Arcs that can be taken without caring about OI guiding
  final case class FlatOrArc(override val instrument: Instrument, gcalCfg: StepConfig.Gcal)
      extends StepType
  // Flats or Arcs that must care about OI guiding
  final case class NightFlatOrArc(override val instrument: Instrument, gcalCfg: StepConfig.Gcal)
      extends StepType
  final case class DarkOrBias(override val instrument: Instrument)          extends StepType
  final case class DarkOrBiasNS(override val instrument: Instrument)        extends StepType
  final case class ExclusiveDarkOrBias(override val instrument: Instrument) extends StepType
  case object AlignAndCalib                                                 extends StepType {
    override val instrument: Instrument = Instrument.Gpi
  }

  given Eq[StepType] = Eq.instance {
    case (CelestialObject(i), CelestialObject(j))         => i === j
    case (NodAndShuffle(i), NodAndShuffle(j))             => i === j
    case (Gems(i), Gems(j))                               => i === j
    case (AltairObs(i), AltairObs(j))                     => i === j
    case (FlatOrArc(i, a), FlatOrArc(j, b))               => i === j && a === b
    case (NightFlatOrArc(i, a), NightFlatOrArc(j, b))     => i === j && a === b
    case (DarkOrBias(i), DarkOrBias(j))                   => i === j
    case (DarkOrBiasNS(i), DarkOrBiasNS(j))               => i === j
    case (ExclusiveDarkOrBias(i), ExclusiveDarkOrBias(j)) => i === j
    case (AlignAndCalib, AlignAndCalib)                   => true
    case _                                                => false
  }
}
