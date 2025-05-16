// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.utils

import cats.effect.IO

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.annotation.JSImport

// Speech files were generated with Amazon Polly. Settings: Generative, Ruth
private trait AudioResourceRaw extends js.Object

extension (raw: AudioResourceRaw) private def resource: String = raw.toString

@JSImport("@resources/sounds/soundactivated.mp3", JSImport.Default)
@js.native
private object SoundActivatedMp3 extends AudioResourceRaw

@JSImport("@resources/sounds/soundactivated.webm", JSImport.Default)
@js.native
private object SoundActivatedWebM extends AudioResourceRaw

@JSImport("@resources/sounds/beep-22.mp3", JSImport.Default)
@js.native
private object BeepMp3 extends AudioResourceRaw

@JSImport("@resources/sounds/beep-22.webm", JSImport.Default)
@js.native
private object BeepWebM extends AudioResourceRaw

@JSImport("@resources/sounds/sequencepaused.mp3", JSImport.Default)
@js.native
private object SequencePausedMp3 extends AudioResourceRaw

@JSImport("@resources/sounds/sequencepaused.webm", JSImport.Default)
@js.native
private object SequencePausedWebM extends AudioResourceRaw

@JSImport("@resources/sounds/acquisitionprompt.mp3", JSImport.Default)
@js.native
private object AcquisitionPromptMp3 extends AudioResourceRaw

@JSImport("@resources/sounds/acquisitionprompt.webm", JSImport.Default)
@js.native
private object AcquisitionPromptWebM extends AudioResourceRaw

@JSImport("@resources/sounds/sequencecomplete.mp3", JSImport.Default)
@js.native
private object SequenceCompleteMp3 extends AudioResourceRaw

@JSImport("@resources/sounds/sequencecomplete.webm", JSImport.Default)
@js.native
private object SequenceCompleteWebM extends AudioResourceRaw

@JSImport("@resources/sounds/sequenceerror.mp3", JSImport.Default)
@js.native
private object SequenceErrorMp3 extends AudioResourceRaw

@JSImport("@resources/sounds/sequenceerror.webm", JSImport.Default)
@js.native
private object SequenceErrorWebM extends AudioResourceRaw

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
    if mp3.canPlayMp3 || !webm.canPlayWebM then mp3 else webm

enum Audio(mp3: AudioResourceRaw, webm: AudioResourceRaw):
  private val rawAudio: RawAudio =
    RawAudio.selectPlayable(new RawAudio(mp3.resource), new RawAudio(webm.resource))

  val play: IO[Unit] = IO(rawAudio.play())

  case SoundActivated    extends Audio(SoundActivatedMp3, SoundActivatedWebM)
  case StepBeep          extends Audio(BeepMp3, BeepWebM)
  case SequencePaused    extends Audio(SequencePausedMp3, SequencePausedWebM)
  case AcquisitionPrompt extends Audio(AcquisitionPromptMp3, AcquisitionPromptWebM)
  case SequenceComplete  extends Audio(SequenceCompleteMp3, SequenceCompleteWebM)
  case SequenceError     extends Audio(SequenceErrorMp3, SequenceErrorWebM)
