// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package reactST.primereact.components

import react.fa.FontAwesomeIcon
import react.common.*
import scala.annotation.targetName

// TODO Move all these to prime-react facade

extension (button: Button.Builder)
  @targetName("buttonIconFA")
  inline def iconFA(v: FontAwesomeIcon): Button.Builder =
    button.icon(v.clazz(PrimeStyles.Button.Icon).raw)

extension (tag: Tag.Builder)
  @targetName("tagIconFA")
  inline def iconFA(v: FontAwesomeIcon): Tag.Builder =
    tag.icon(v.clazz(PrimeStyles.Tag.Icon).raw)
