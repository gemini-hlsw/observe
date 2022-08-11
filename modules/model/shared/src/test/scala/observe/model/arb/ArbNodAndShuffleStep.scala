// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import org.scalacheck.{ Arbitrary, Cogen, Gen }
import org.scalacheck.Arbitrary._
import lucuma.core.util.arb.ArbEnumerated._
import lucuma.core.util.arb.ArbGid._
import lucuma.core.util.arb.ArbUid._
import observe.model._
import observe.model.GmosParameters._
import observe.model.NodAndShuffleStep.{ PauseGracefully, PendingObserveCmd, StopGracefully }
import observe.model.enum._
import observe.model.arb.ArbStepConfig._
import observe.model.arb.ArbStepState._
import observe.model.arb.ArbDhsTypes._
import observe.model.arb.ArbTime._
import observe.model.arb.ArbGmosParameters._
import observe.model.arb.ArbNSRunningState._
import squants._

trait ArbNodAndShuffleStep {
  implicit val nssArb = Arbitrary[NodAndShuffleStatus] {
    for {
      as <- arbitrary[ActionStatus]
      t  <- arbitrary[Time]
      n  <- arbitrary[Time]
      c  <- arbitrary[NsCycles]
      s  <- arbitrary[Option[NSRunningState]]
    } yield NodAndShuffleStatus(as, t, n, c, s)
  }

  implicit val nodAndShuffleStatusCogen: Cogen[NodAndShuffleStatus] =
    Cogen[(ActionStatus, Time, Time, Int)].contramap { x =>
      (x.observing, x.totalExposureTime, x.nodExposureTime, x.cycles)
    }

  implicit val nodAndShufflePendingCmdArb: Arbitrary[PendingObserveCmd] =
    Arbitrary[PendingObserveCmd](
      Gen.oneOf(List(PauseGracefully, StopGracefully))
    )

  implicit val nodShuffleStepArb = Arbitrary[NodAndShuffleStep] {
    for {
      id <- arbitrary[StepId]
      c  <- stepConfigGen
      s  <- arbitrary[StepState]
      b  <- arbitrary[Boolean]
      k  <- arbitrary[Boolean]
      f  <- arbitrary[Option[dhs.ImageFileId]]
      cs <- arbitrary[List[(Resource, ActionStatus)]]
      os <- arbitrary[NodAndShuffleStatus]
      oc <- arbitrary[Option[PendingObserveCmd]]
    } yield new NodAndShuffleStep(id = id,
                                  config = c,
                                  status = s,
                                  breakpoint = b,
                                  skip = k,
                                  fileId = f,
                                  configStatus = cs,
                                  nsStatus = os,
                                  pendingObserveCmd = oc
    )
  }

  implicit val nodShuffleStepCogen: Cogen[NodAndShuffleStep] =
    Cogen[
      (
        StepId,
        Map[SystemName, Map[String, String]],
        StepState,
        Boolean,
        Boolean,
        Option[dhs.ImageFileId],
        List[(Resource, ActionStatus)],
        NodAndShuffleStatus
      )
    ].contramap(s =>
      (s.id, s.config, s.status, s.breakpoint, s.skip, s.fileId, s.configStatus, s.nsStatus)
    )

}

object ArbNodAndShuffleStep extends ArbNodAndShuffleStep
