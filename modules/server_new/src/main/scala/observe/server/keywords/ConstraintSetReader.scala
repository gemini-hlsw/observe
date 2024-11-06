// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import cats.Applicative
import cats.syntax.all.*
import lucuma.core.model.ConstraintSet
import lucuma.core.model.ElevationRange

trait ConstraintSetReader[F[_]] {
  def imageQualityStr: F[String]

  def cloudExtinctionStr: F[String]

  def waterVaporStr: F[String]

  def backgroundLightStr: F[String]

  def imageQualityDbl: F[Double]

  def cloudExtinctionDbl: F[Double]

  def waterVaporDbl: F[Double]

  def backgroundLightDbl: F[Double]

  def elevationRange: F[ElevationRange]
}

object ConstraintSetReader {
  def apply[F[_]: Applicative](
    constraintSet:      ConstraintSet,
    conditionSetReader: ConditionSetReader[F]
  ): ConstraintSetReader[F] = new ConstraintSetReader[F] {

    export conditionSetReader.*

    override def elevationRange: F[ElevationRange] = constraintSet.elevationRange.pure[F]
  }
}
