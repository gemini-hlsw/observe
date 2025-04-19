// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.utils

import cats.effect.IO

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.annotation.JSImport

private trait AudioResourceRaw extends js.Object

extension (raw: AudioResourceRaw) private def resource: String = raw.toString

@JSImport("@resources/sounds/beep-22.mp3", JSImport.Default)
@js.native
private object BeepResourceMp3 extends AudioResourceRaw

@JSImport("@resources/sounds/beep-22.webm", JSImport.Default)
@js.native
private object BeepResourceWebM extends AudioResourceRaw

/**
 * JS Facade for HTML5 audio
 */
@js.native
@JSGlobal(name = "Audio")
class RawAudio(val src: String) extends js.Object:
  def play(): Unit  = js.native
  def pause(): Unit = js.native
  def canPlayType(tp: String): String = js.native

object RawAudio:
  extension (a: RawAudio)
    private def canPlayMp3: Boolean  = a.canPlayType("audio/mpeg").nonEmpty
    private def canPlayWebM: Boolean = a.canPlayType("audio/webm").nonEmpty

  protected[utils] def selectPlayable(mp3: RawAudio, webm: RawAudio): RawAudio =
    if (mp3.canPlayMp3)
      mp3
    else if (webm.canPlayWebM)
      webm
    else
      mp3

enum Audio(mp3: AudioResourceRaw, webm: AudioResourceRaw):
  private val rawAudio: RawAudio =
    RawAudio.selectPlayable(new RawAudio(mp3.resource), new RawAudio(webm.resource))

  val play: IO[Unit] = IO(rawAudio.play())

  case StepBeep extends Audio(BeepResourceMp3, BeepResourceWebM)
