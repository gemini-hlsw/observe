// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import lucuma.core.math.Axis
import observe.model.enums.SystemName

object OffsetType:
  type Telescope
  type NSNodA
  type NSNodB

sealed trait OffsetFormat[A]:
  val format: String

object OffsetFormat:
  given OffsetFormat[OffsetType.NSNodA] = new OffsetFormat[OffsetType.NSNodA]:
    override val format = "A"

  given OffsetFormat[OffsetType.NSNodB] = new OffsetFormat[OffsetType.NSNodB]:
    override val format = "B"

  given OffsetFormat[Axis.P] = new OffsetFormat[Axis.P]:
    override val format = "p"

  given OffsetFormat[Axis.Q] = new OffsetFormat[Axis.Q]:
    override val format = "q"

sealed trait OffsetConfigResolver[T, A]:
  val systemName: SystemName
  val configItem: ParamName

object OffsetConfigResolver:
  sealed trait TelescopeOffsetConfigResolver[A]
      extends OffsetConfigResolver[OffsetType.Telescope, A]:
    val systemName = SystemName.Telescope

  given OffsetConfigResolver[OffsetType.Telescope, Axis.P] =
    new TelescopeOffsetConfigResolver[Axis.P]:
      val configItem = ParamName("p")

  given OffsetConfigResolver[OffsetType.Telescope, Axis.Q] =
    new TelescopeOffsetConfigResolver[Axis.Q]:
      val configItem = ParamName("q")

  sealed trait NSOffsetConfigResolver[T, A] extends OffsetConfigResolver[T, A]:
    val systemName = SystemName.Instrument

  sealed trait NSOffsetConfigResolverA[A] extends NSOffsetConfigResolver[OffsetType.NSNodA, A]

  given OffsetConfigResolver[OffsetType.NSNodA, Axis.P] = new NSOffsetConfigResolverA[Axis.P]:
    val configItem = ParamName("nsBeamA-p")

  given OffsetConfigResolver[OffsetType.NSNodA, Axis.Q] = new NSOffsetConfigResolverA[Axis.Q]:
    val configItem = ParamName("nsBeamA-q")

  sealed trait NSOffsetConfigResolverB[A] extends NSOffsetConfigResolver[OffsetType.NSNodB, A]

  given OffsetConfigResolver[OffsetType.NSNodB, Axis.P] = new NSOffsetConfigResolverB[Axis.P]:
    val configItem = ParamName("nsBeamB-p")

  given OffsetConfigResolver[OffsetType.NSNodB, Axis.Q] = new NSOffsetConfigResolverB[Axis.Q]:
    val configItem = ParamName("nsBeamB-q")
