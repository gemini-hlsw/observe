// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.model.arb

import lucuma.core.enums.Instrument
import lucuma.core.model.sequence.Step
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.enums.Resource
import observe.model.given
import observe.ui.model.ObservationRequests
import observe.ui.model.enums.OperationRequest
import observe.ui.model.enums.arb.ArbOperationRequest.given
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen

trait ArbObservationRequests:
  given Arbitrary[ObservationRequests] = Arbitrary:
    for
      run               <- arbitrary[OperationRequest]
      stop              <- arbitrary[OperationRequest]
      abort             <- arbitrary[OperationRequest]
      pause             <- arbitrary[OperationRequest]
      cancelPause       <- arbitrary[OperationRequest]
      resume            <- arbitrary[OperationRequest]
      startFrom         <- arbitrary[OperationRequest]
      subsystemRun      <- arbitrary[Map[Step.Id, Map[Resource | Instrument, OperationRequest]]]
      acquisitionPrompt <- arbitrary[OperationRequest]
    yield ObservationRequests(
      run,
      stop,
      abort,
      pause,
      cancelPause,
      resume,
      startFrom,
      subsystemRun,
      acquisitionPrompt
    )

  given Cogen[ObservationRequests] = Cogen[
    (OperationRequest,
     OperationRequest,
     OperationRequest,
     OperationRequest,
     OperationRequest,
     OperationRequest,
     OperationRequest,
     List[(Step.Id, List[(Resource | Instrument, OperationRequest)])],
     OperationRequest
    )
  ].contramap(x =>
    (x.run,
     x.stop,
     x.abort,
     x.pause,
     x.cancelPause,
     x.resume,
     x.startFrom,
     x.subsystemRun.view.mapValues(_.toList).toList,
     x.acquisitionPrompt
    )
  )

object ArbObservationRequests extends ArbObservationRequests
