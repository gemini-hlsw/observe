// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components

import japgolly.scalajs.react.Reusability
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import react.common.ReactProps
import react.semanticui.elements.button.Button
import react.semanticui.elements.icon.Icon
import react.semanticui.sizes._
import observe.web.client.actions.FlipSoundOnOff
import observe.web.client.circuit.ObserveCircuit
import observe.web.client.model.SoundSelection
import observe.web.client.reusability._
import observe.web.client.services.WebpackResources._
import web.client.Audio

final case class SoundControl(sound: SoundSelection)
    extends ReactProps[SoundControl](SoundControl.component)

/**
 * Button to toggle sound on/off
 */
object SoundControl {
  private val SoundOn =
    Audio.selectPlayable(new Audio(SoundOnMP3.resource), new Audio(SoundOnWebM.resource))

  implicit val propsReuse: Reusability[SoundControl] = Reusability.derive[SoundControl]

  private def flipSound: Callback =
    ObserveCircuit.dispatchCB(FlipSoundOnOff)

  private val component = ScalaComponent
    .builder[SoundControl]("SoundControl")
    .stateless
    .render_P { p =>
      val icon       = p.sound match {
        case SoundSelection.SoundOn  => Icon("volume up")
        case SoundSelection.SoundOff => Icon("volume off")
      }
      val soundClick = p.sound match {
        case SoundSelection.SoundOn  => Callback.empty
        case SoundSelection.SoundOff => Callback(SoundOn.play())
      }
      Button(icon = icon, inverted = true, size = Medium, onClick = soundClick *> flipSound)
    }
    .configure(Reusability.shouldComponentUpdate)
    .build

}
