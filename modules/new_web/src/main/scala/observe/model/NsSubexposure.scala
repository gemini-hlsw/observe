// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.Eq
import cats.syntax.all.*
import observe.model.enums.NodAndShuffleStage
import cats.derived.*

case class NsSubexposure private (
  totalCycles: NsCycles,          // Total amount of cycles for a N&S step
  cycle:       NsCycles,          // Cycle for this sub exposure
  stageIndex:  NsStageIndex,      // Nod or stage index (between 0 and 3)
  stage:       NodAndShuffleStage // Subexposure stage
) derives Eq:
  val firstSubexposure: Boolean = cycle.value === 0 && stageIndex.value === 0
  val lastSubexposure: Boolean  =
    cycle.value === totalCycles.value - 1 && stageIndex.value === NodAndShuffleStage.NsSequence.length - 1

object NsSubexposure:
  val Zero: NsSubexposure =
    NsSubexposure(NsCycles(0), NsCycles(0), NsStageIndex(0), NodAndShuffleStage.StageA)

  // Smart constructor returns a Some if the parameters are logically consistent
  def apply(
    totalCycles: NsCycles,
    cycle:       NsCycles,
    stageIndex:  NsStageIndex
  ): Option[NsSubexposure] =
    if (
      totalCycles.value >= 0 && cycle.value >= 0 && cycle.value <= totalCycles.value && stageIndex.value >= 0 && stageIndex.value < NodAndShuffleStage.NsSequence.length
    ) {
      NsSubexposure(
        totalCycles,
        cycle,
        stageIndex,
        NodAndShuffleStage.NsSequence.toList
          .lift(stageIndex.value)
          .getOrElse(NodAndShuffleStage.StageA)
      ).some
    } else none

  // Calculate the subexposures
  def subexposures(totalCycles: Int): List[NsSubexposure] =
    (for {
      i <- 0 until totalCycles
      j <- 0 until NodAndShuffleStage.NsSequence.length
    } yield NsSubexposure(NsCycles(totalCycles), NsCycles(i), NsStageIndex(j))).toList.flatten
