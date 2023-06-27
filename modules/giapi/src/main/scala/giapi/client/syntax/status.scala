// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package giapi.client.syntax

import giapi.client.GiapiConfig
import giapi.client.StatusValue

object status {

  extension (s: StatusValue) {
    def intValue: Option[Int]       = StatusValue.intValue(s)
    def stringValue: Option[String] = StatusValue.stringValue(s)
    def floatValue: Option[Float]   = StatusValue.floatValue(s)
    def doubleValue: Option[Double] = StatusValue.doubleValue(s)
  }

  extension (s: Option[StatusValue]) {
    def intValue: Option[Int]                                    = s.flatMap(StatusValue.intValue)
    def stringValue: Option[String]                              = s.flatMap(StatusValue.stringValue)
    def floatValue: Option[Float]                                = s.flatMap(StatusValue.floatValue)
    def doubleValue: Option[Double]                              = s.flatMap(StatusValue.doubleValue)
    def intCfg(using ev: GiapiConfig[Int]): Option[String]       =
      intValue.map(ev.configValue)
    def stringCfg(using ev: GiapiConfig[String]): Option[String] =
      stringValue.map(ev.configValue)
    def floatCfg(using ev: GiapiConfig[Float]): Option[String]   =
      floatValue.map(ev.configValue)
    def doubleCfg(using ev: GiapiConfig[Double]): Option[String] =
      doubleValue.map(ev.configValue)
  }
}
