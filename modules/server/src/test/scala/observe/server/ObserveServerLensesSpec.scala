// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server

import cats.Eq
import cats.tests.CatsSuite
import edu.gemini.spModel.config2.{Config, ItemKey}
import monocle.law.discipline.LensTests
import lucuma.core.util.arb.ArbGid.*
import lucuma.core.util.arb.ArbUid.*
import observe.model.enums.Instrument
import observe.model.SystemOverrides
import observe.engine
import observe.engine.{Action, ParallelActions}
import SequenceGen._
import cats.effect.IO
import observe.common.test.observationId

/**
 * Tests ObserveServer Lenses
 */
final class ObserveServerLensesSpec extends CatsSuite with ObserveServerArbitraries {

  given Eq[Map[ItemKey, AnyRef]] = Eq.fromUniversalEquals
  given Eq[Config]               = Eq.fromUniversalEquals
  given Eq[CleanConfig]          =
    Eq.by(x => (x.config, x.overrides))

  // I tried to go down the rabbit hole with the Eqs, but it is not worth it for what they are used.
  given [F[_]]: Eq[Action.State[F]]                                                = Eq.fromUniversalEquals
  given [F[_]]: Eq[Action[F]]                                                      = Eq.by(x => (x.kind, x.state))
  // Formally, to probe equality between two functions it must be probed that they produce the same result for all
  // possible inputs. We settle for the `UniversalEquals` instead.
  given [F[_]]: Eq[HeaderExtraData => List[ParallelActions[F]]]                    =
    Eq.fromUniversalEquals
  given [F[_]]: Eq[(HeaderExtraData, SystemOverrides) => List[ParallelActions[F]]] =
    Eq.fromUniversalEquals
  given [F[_]]: Eq[SystemOverrides => Action[F]]                                   = Eq.fromUniversalEquals
  given [F[_]]: Eq[StepActionsGen[F]]                                              = Eq.by(x => (x.configs, x.post))
  given [F[_]]: Eq[PendingStepGen[F]]                                              =
    Eq.by(x => (x.id, x.config, x.resources, x.generator))
  given Eq[SkippedStepGen]                                                         = Eq.by(x => (x.id, x.config))
  given Eq[CompletedStepGen]                                                       = Eq.by(x => (x.id, x.config, x.fileId))
  given [F[_]]: Eq[StepGen[F]]                                                     = Eq.instance {
    case (a: PendingStepGen[F], b: PendingStepGen[F]) => a === b
    case (a: SkippedStepGen, b: SkippedStepGen)       => a === b
    case (a: CompletedStepGen, b: CompletedStepGen)   => a === b
    case _                                            => false
  }
  given [F[_]]: Eq[SequenceGen[F]]                                                 = Eq.by(x => (x.id, x.title, x.instrument, x.steps))
  given [F[_]]: Eq[SequenceData[F]]                                                = Eq.by(x => (x.observer, x.seqGen))
  given [F[_]]: Eq[engine.Sequence.State[F]]                                       = Eq.fromUniversalEquals
  given Eq[EngineState[IO]]                                                        =
    Eq.by(x => (x.queues, x.selected, x.conditions, x.operator, x.sequences))

  checkAll("selected optional", LensTests(EngineState.instrumentLoadedL[IO](Instrument.Gpi)))

  private val seqId = observationId(1)
  // Some sanity checks
  test("Support inserting new loaded sequences") {
    val base = EngineState
      .default[IO]
      .copy(selected = Map(Instrument.F2 -> observationId(1)))
    EngineState
      .instrumentLoadedL(Instrument.Gpi)
      .replace(seqId.some)
      .apply(base) shouldEqual base.copy(selected = base.selected + (Instrument.Gpi -> seqId))
  }
  test("Support replacing loaded sequences") {
    val base = EngineState
      .default[IO]
      .copy(
        selected = Map(Instrument.Gpi -> observationId(1), Instrument.F2 -> observationId(2))
      )
    EngineState
      .instrumentLoadedL(Instrument.Gpi)
      .replace(seqId.some)
      .apply(base) shouldEqual base.copy(selected = base.selected.updated(Instrument.Gpi, seqId))
  }
}
