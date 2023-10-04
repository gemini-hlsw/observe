// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui

import cats.effect.IO
import cats.effect.unsafe.implicits._
import observe.ui.components.MainApp
import org.scalajs.dom
import org.scalajs.dom.Element

import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("Main")
object Main:

  @JSExport
  def runIOApp(): Unit = run.unsafeRunAndForget()

  private val setupDOM: IO[Element] = IO:
    Option(dom.document.getElementById("root")).getOrElse:
      val elem = dom.document.createElement("div")
      elem.id = "root"
      dom.document.body.appendChild(elem)
      elem

  private def run: IO[Unit] =
    setupDOM.map(MainApp().renderIntoDOM(_)).void
