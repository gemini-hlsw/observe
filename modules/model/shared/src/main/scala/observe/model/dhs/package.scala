// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model

import cats.*
import lucuma.core.util.NewType

import scala.annotation.targetName

package object dhs {

  object ImageFileId extends NewType[String]
  type ImageFileId = ImageFileId.Type

  object DataId extends NewType[String]
  type DataId = DataId.Type

//  given monoidImageFileId: Monoid[ImageFileId] =
//    new Monoid[ImageFileId] {
//      def empty: ImageFileId                                   = toImageFileId(Monoid[String].empty)
//      def combine(x: ImageFileId, y: ImageFileId): ImageFileId =
//        toImageFileId(Monoid[String].combine(x, y))
//    }
//  given monoidDataId: Monoid[DataId]           = new Monoid[DataId] {
//    def empty: DataId                         = toDataId(Monoid[String].empty)
//    def combine(x: DataId, y: DataId): DataId =
//      toDataId(Monoid[String].combine(x, y))
//  }

}
