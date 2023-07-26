// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import clue.data.syntax.*
import lucuma.core.math.Offset
import lucuma.core.model.sequence.gmos.{GmosNodAndShuffle, StaticConfig}
import lucuma.schemas.ObservationDB.Types.{
  GmosNodAndShuffleInput,
  GmosNorthStaticInput,
  GmosSouthStaticInput,
  OffsetComponentInput,
  OffsetInput
}

object ObsQueryInput {

  extension [A](o: Offset.Component[A])
    def toInput: OffsetComponentInput =
      OffsetComponentInput(microarcseconds = o.toAngle.toMicroarcseconds.assign)

  extension (o: Offset) {
    def toInput: OffsetInput = OffsetInput(o.p.toInput, o.q.toInput)
  }

  extension (ns: GmosNodAndShuffle) {
    def toInput: GmosNodAndShuffleInput = GmosNodAndShuffleInput(
      ns.posA.toInput,
      ns.posB.toInput,
      ns.eOffset,
      ns.shuffleOffset,
      ns.shuffleCycles
    )
  }

  extension (gmosNStatic: StaticConfig.GmosNorth) {
    def toInput: GmosNorthStaticInput = GmosNorthStaticInput(
      gmosNStatic.stageMode.assign,
      gmosNStatic.detector.assign,
      gmosNStatic.mosPreImaging.assign,
      gmosNStatic.nodAndShuffle.map(_.toInput).orUnassign
    )
  }

  extension (gmosNStatic: StaticConfig.GmosSouth) {
    def toInput: GmosSouthStaticInput = GmosSouthStaticInput(
      gmosNStatic.stageMode.assign,
      gmosNStatic.detector.assign,
      gmosNStatic.mosPreImaging.assign,
      gmosNStatic.nodAndShuffle.map(_.toInput).orUnassign
    )
  }

}
