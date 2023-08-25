// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.gcal

import scala.Function.const
import cats.effect.Sync
import cats.syntax.all.*
import lucuma.core.enums.{GcalArc, GcalContinuum, GcalShutter}
import lucuma.core.model.sequence.StepConfig
import lucuma.core.model.sequence.StepConfig.Gcal.Lamp.*
import observe.model.enums.Resource
import observe.server.ConfigResult
import observe.server.System
import observe.server.gcal.GcalController.*

/**
 * Created by jluhrs on 3/21/17.
 */
final case class Gcal[F[_]: Sync] private (controller: GcalController[F], cfg: GcalConfig)
    extends System[F] {

  override val resource: Resource = Resource.Gcal

  /**
   * Called to configure a system, returns a F[ConfigResult[F]]
   */
  override def configure: F[ConfigResult[F]] =
    controller.applyConfig(cfg).map(const(ConfigResult(this)))

  override def notifyObserveStart: F[Unit] = Sync[F].unit

  override def notifyObserveEnd: F[Unit] = Sync[F].unit

}

object Gcal {

  def fromConfig[F[_]: Sync](
    isCP:   Boolean,
    config: StepConfig.Gcal
  ): GcalController[F] => Gcal[F] = {

    val arLamp     = ArLampState(
      if (config.lamp.arcs.exists(_.contains(GcalArc.ArArc))) LampState.On else LampState.Off
    )
    val cuarLamp   = CuArLampState(
      if (config.lamp.arcs.exists(_.contains(GcalArc.CuArArc))) LampState.On else LampState.Off
    )
    val tharLamp   = ThArLampState(
      if (config.lamp.arcs.exists(_.contains(GcalArc.ThArArc))) LampState.On else LampState.Off
    )
    val xeLamp     = XeLampState(
      if (config.lamp.arcs.exists(_.contains(GcalArc.XeArc))) LampState.On else LampState.Off
    )
    val qh5WLamp   = QH5WLampState(
      if (config.lamp.continuum.exists(_ === GcalContinuum.QuartzHalogen5W)) LampState.On
      else LampState.Off
    )
    val qh100WLamp = QH100WLampState(
      if (config.lamp.continuum.exists(_ === GcalContinuum.QuartzHalogen100W)) LampState.On
      else LampState.Off
    )
    val irLampCP   = config.lamp.continuum
      .filter(x => x === GcalContinuum.IrGreyBodyHigh || x === GcalContinuum.IrGreyBodyLow)
      .as(LampState.On)
    val irLampMK   = config.lamp.continuum.collect {
      case GcalContinuum.IrGreyBodyLow  => LampState.Off
      case GcalContinuum.IrGreyBodyHigh => LampState.On
    }
    val irLamp     = (if (isCP) irLampCP else irLampMK).map(IrLampState.apply)
    val shutter    = config.shutter
    val filter     = config.filter
    val diffuser   = config.diffuser

    (controller: GcalController[F]) =>
      new Gcal[F](
        controller,
        if (
          config.lamp.continuum.isEmpty && config.lamp.arcs.isEmpty && shutter === GcalShutter.Closed
        ) GcalConfig.GcalOff
        else
          GcalConfig.GcalOn(arLamp,
                            cuarLamp,
                            qh5WLamp,
                            qh100WLamp,
                            tharLamp,
                            xeLamp,
                            irLamp,
                            shutter,
                            filter,
                            diffuser
          )
      )
  }

  // GCAL that always turn off its lamps except for the IR lamp. Used to assure GCAL light does not interfere in a non
  // calibration step
  def defaultGcal[F[_]: Sync](controller: GcalController[F]): Gcal[F] =
    new Gcal[F](controller, GcalConfig.GcalOffIgnoringIr)
}
