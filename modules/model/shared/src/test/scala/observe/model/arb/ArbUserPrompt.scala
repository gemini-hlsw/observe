// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import cats.data.NonEmptyList
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*
import observe.model.Observation
import observe.model.StepId
import observe.model.UserPrompt
import observe.model.UserPrompt.ChecksOverride
import observe.model.UserPrompt.Discrepancy
import observe.model.UserPrompt.ObsConditionsCheckOverride
import observe.model.UserPrompt.SeqCheck
import observe.model.UserPrompt.TargetCheckOverride
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.*
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait ArbUserPrompt {

  given discrepancyArb[A: Arbitrary]: Arbitrary[Discrepancy[A]] =
    Arbitrary[Discrepancy[A]] {
      for {
        actual   <- arbitrary[A]
        required <- arbitrary[A]
      } yield Discrepancy[A](actual, required)
    }

  given discrepancyCogen[A: Cogen]: Cogen[Discrepancy[A]] =
    Cogen[(A, A)].contramap(x => (x.actual, x.required))

  given targetCheckOverrideArb: Arbitrary[TargetCheckOverride] =
    Arbitrary[TargetCheckOverride] {
      for {
        self <- arbitrary[Discrepancy[String]]
      } yield TargetCheckOverride(self)
    }

  given targetCheckOverrideCogen: Cogen[TargetCheckOverride] =
    Cogen[Discrepancy[String]].contramap(x => x.self)

  given obsConditionsCheckOverrideArb: Arbitrary[ObsConditionsCheckOverride] =
    Arbitrary[ObsConditionsCheckOverride] {
      for {
        i  <- Gen.choose(0, 3)
        cc <- if (i == 0) arbitrary[Discrepancy[String]].map(Some(_))
              else arbitrary[Option[Discrepancy[String]]]
        iq <- if (i == 1) arbitrary[Discrepancy[String]].map(Some(_))
              else arbitrary[Option[Discrepancy[String]]]
        sc <- if (i == 2) arbitrary[Discrepancy[String]].map(Some(_))
              else arbitrary[Option[Discrepancy[String]]]
        wv <- if (i == 3) arbitrary[Discrepancy[String]].map(Some(_))
              else arbitrary[Option[Discrepancy[String]]]
      } yield ObsConditionsCheckOverride(cc, iq, sc, wv)
    }

  given obsConditionsCheckOverrideCogen: Cogen[ObsConditionsCheckOverride] =
    Cogen[
      (
        Option[Discrepancy[String]],
        Option[Discrepancy[String]],
        Option[Discrepancy[String]],
        Option[Discrepancy[String]]
      )
    ].contramap(x => (x.cc, x.iq, x.sb, x.wv))

  given seqCheckCogen: Cogen[SeqCheck] =
    Cogen[Either[TargetCheckOverride, ObsConditionsCheckOverride]]
      .contramap {
        case a: TargetCheckOverride        => Left(a)
        case b: ObsConditionsCheckOverride => Right(b)
      }

  given nelSeqCheckCogen: Cogen[NonEmptyList[SeqCheck]] =
    Cogen[(SeqCheck, List[SeqCheck])].contramap(x => (x.head, x.tail))

  private val checksGen = for {
    b   <- arbitrary[Boolean]
    tc  <- arbitrary[TargetCheckOverride]
    tco <- arbitrary[Option[TargetCheckOverride]]
    oc  <- arbitrary[ObsConditionsCheckOverride]
    oco <- arbitrary[Option[ObsConditionsCheckOverride]]
  } yield if (b) NonEmptyList(tc, oco.toList) else NonEmptyList(oc, tco.toList)

  given checksOverrideArb: Arbitrary[ChecksOverride] = Arbitrary[ChecksOverride] {
    for {
      sid  <- arbitrary[Observation.Id]
      stid <- arbitrary[StepId]
      chks <- checksGen
    } yield ChecksOverride(sid, stid, chks)
  }

  given checksOverrideCogen: Cogen[ChecksOverride] =
    Cogen[(Observation.Id, StepId, NonEmptyList[SeqCheck])].contramap(x =>
      (x.obsId, x.stepId, x.checks)
    )

  given userPromptArb: Arbitrary[UserPrompt] = Arbitrary[UserPrompt] {
    for {
      r <- arbitrary[ChecksOverride]
    } yield r
  }

  given userPromptCogen: Cogen[UserPrompt] =
    Cogen[Option[ChecksOverride]]
      .contramap { case r: ChecksOverride =>
        Some(r)
      }

}

object ArbUserPrompt extends ArbUserPrompt
