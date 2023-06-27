// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.client.components.sequence

package object steps {
  extension [A](a: A) {
    def controlButtonsActive(using resolver: ControlButtonResolver[A]): Boolean =
      resolver.controlButtonsActive(a)
  }
}
