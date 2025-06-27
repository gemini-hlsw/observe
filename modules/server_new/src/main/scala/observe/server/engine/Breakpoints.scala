// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.engine

import cats.syntax.all.*
import lucuma.core.enums.Breakpoint
import lucuma.core.model.sequence.Step

import scala.collection.immutable.HashSet

opaque type Breakpoints = Set[Step.Id]

opaque type BreakpointsDelta = Set[(Step.Id, Breakpoint)]

object Breakpoints:
  val empty: Breakpoints = HashSet.empty
  def apply(steps: Set[Step.Id]): Breakpoints = steps
  def fromStepsWithBreakpoints[F[_]](
    stepsWithBreakpoints: List[(EngineStep[F], Breakpoint)]
  ): Breakpoints =
    stepsWithBreakpoints
      .collect:
        case (s, b) if b === Breakpoint.Enabled => s.id
      .toSet

  extension (breakpoints: Breakpoints)
    def value: Set[Step.Id] = breakpoints
    def contains(stepId: Step.Id): Boolean     = breakpoints.contains_(stepId)
    def +(stepId:        Step.Id): Breakpoints = breakpoints + stepId
    def -(stepId:        Step.Id): Breakpoints = breakpoints - stepId
    def merge(breakpointsDelta: BreakpointsDelta): Breakpoints =
      breakpoints ++ breakpointsDelta.collect:
        case (stepId, Breakpoint.Enabled) => stepId

object BreakpointsDelta:
  def apply(breakpointsDelta: Set[(Step.Id, Breakpoint)]): BreakpointsDelta =
    breakpointsDelta
  def fromStepsWithBreakpoints[F[_]](
    stepsWithBreakpoints: List[(EngineStep[F], Breakpoint)]
  ): BreakpointsDelta =
    stepsWithBreakpoints.map((s, b) => (s.id, b)).toSet
