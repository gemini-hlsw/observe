// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.model.arb

import cats.*
import org.scalacheck.Arbitrary.*
import org.scalacheck.*

import scala.collection.immutable.SortedMap
import lucuma.core.util.arb.ArbEnumerated.given
import lucuma.core.util.arb.ArbGid.given
import lucuma.core.util.arb.ArbUid.given
import observe.model.StepId
import observe.model.enums.Resource
import observe.web.client.model.*
import observe.web.client.model.RunOperation

trait ArbTabOperations {
  given Arbitrary[ResourceRunOperation] =
    Arbitrary {
      for {
        i <- arbitrary[StepId]
        s <- Gen.oneOf(ResourceRunOperation.ResourceRunIdle,
                       ResourceRunOperation.ResourceRunInFlight(i),
                       ResourceRunOperation.ResourceRunCompleted(i)
             )
      } yield s
    }

  given Cogen[ResourceRunOperation] =
    Cogen[Option[Either[StepId, Either[StepId, StepId]]]].contramap {
      case ResourceRunOperation.ResourceRunIdle         => None
      case ResourceRunOperation.ResourceRunInFlight(i)  => Some(Left(i))
      case ResourceRunOperation.ResourceRunCompleted(i) => Some(Right(Right(i)))
      case ResourceRunOperation.ResourceRunFailed(i)    => Some(Right(Left(i)))
    }

  given Arbitrary[TabOperations] = {
    given Ordering[Resource] =
      Order[Resource].toOrdering

    Arbitrary {
      for {
        r <- arbitrary[RunOperation]
        s <- arbitrary[SyncOperation]
        p <- arbitrary[PauseOperation]
        c <- arbitrary[CancelPauseOperation]
        m <- arbitrary[ResumeOperation]
        t <- arbitrary[StopOperation]
        a <- arbitrary[AbortOperation]
        f <- arbitrary[StartFromOperation]
        u <- arbitrary[SortedMap[Resource, ResourceRunOperation]]
      } yield TabOperations(r, s, p, c, m, t, a, f, u)
    }
  }

  given Cogen[TabOperations] =
    Cogen[
      (
        RunOperation,
        SyncOperation,
        PauseOperation,
        CancelPauseOperation,
        ResumeOperation,
        StopOperation,
        AbortOperation,
        StartFromOperation,
        List[(Resource, ResourceRunOperation)]
      )
    ].contramap(x =>
      (x.runRequested,
       x.syncRequested,
       x.pauseRequested,
       x.cancelPauseRequested,
       x.resumeRequested,
       x.stopRequested,
       x.abortRequested,
       x.startFromRequested,
       x.resourceRunRequested.toList
      )
    )

}

object ArbTabOperations extends ArbTabOperations
