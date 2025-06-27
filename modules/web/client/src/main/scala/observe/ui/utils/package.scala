// Copyright (c) 2016-2025 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.ui.utils

import org.scalajs.dom
import org.scalajs.dom.html

type Canvas = html.Canvas
type Ctx2D  = dom.CanvasRenderingContext2D

def textWidth(text: String, font: String): Double = {
  val canvas  = dom.document.createElement("canvas").asInstanceOf[Canvas]
  val ctx     = canvas.getContext("2d").asInstanceOf[Ctx2D]
  ctx.font = font
  val metrics = ctx.measureText(text)
  metrics.width
}

def tableTextWidth(text: String): Double =
  textWidth(text, "bold 14px sans-serif")

extension [A](set: Set[A])
  def toggle(a: A): Set[A] =
    if set.contains(a)
    then set - a
    else set + a
