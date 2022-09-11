// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package reactST.primereact.components

import react.fa.FontAwesomeIcon
import react.common.*
import scala.annotation.targetName

// TODO Move all these to prime-react facade

extension (tag: Tag.Builder)
  inline def iconFA(v: FontAwesomeIcon): Tag.Builder =
    tag.icon(v.clazz(PrimeStyles.Tag.Icon).raw)

  @targetName("Tag-clazz")
  inline def clazz(v: Css): Tag.Builder = 
    tag.className(v.htmlClass)

extension (tag: Divider.Builder)
  @targetName("Divider-clazz")
  inline def clazz(v: Css): Divider.Builder = 
    tag.className(v.htmlClass)

extension (tag: TabPanel.Builder)
  @targetName("TabPanel-clazz")
  inline def clazz(v: Css): TabPanel.Builder = 
    tag.className(v.htmlClass)

extension (tag: AccordionTab.Builder)
  @targetName("AccordionTab-clazz")
  inline def clazz(v: Css): AccordionTab.Builder = 
    tag.className(v.htmlClass)

extension (tag: Toolbar.Builder)
  @targetName("Toolbar-clazz")
  inline def clazz(v: Css): Toolbar.Builder = 
    tag.className(v.htmlClass)      