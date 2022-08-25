// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe

import react.common.style.Css
import cats.effect.Sync
import org.scalajs.dom
import cats.syntax.all.given
import lucuma.core.util.Enumerated

enum Theme(val tag: String, val clazz: Css):
  case Light extends Theme("light", Css("light-theme"))
  case Dark extends Theme("dark", Css("dark-theme"))

  def setup[F[_]: Sync]: F[Unit] = 
    Sync[F].delay {
      dom.document.body.classList.add(this.clazz.htmlClass)
      Enumerated[Theme].all.filterNot(_ === this).foreach(otherTheme => dom.document.body.classList.remove(otherTheme.clazz.htmlClass))
    }

object Theme:
  given Enumerated[Theme] = Enumerated.from(Light, Dark).withTag(_.tag)

  def current[F[_]: Sync]: F[Theme] = 
    Sync[F].delay {
      Enumerated[Theme].all.find(theme => dom.document.body.classList.contains(theme.clazz.htmlClass)).getOrElse(Theme.Light)
    }