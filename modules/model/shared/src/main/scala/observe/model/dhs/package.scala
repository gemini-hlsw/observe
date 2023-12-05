// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.dhs

import cats.*
import lucuma.core.util.NewType

object ImageFileId extends NewType[String]:
  given Monoid[ImageFileId] =
    new Monoid[ImageFileId] {
      def empty: ImageFileId = ImageFileId(Monoid[String].empty)

      def combine(x: ImageFileId, y: ImageFileId): ImageFileId =
        ImageFileId(Monoid[String].combine(x.value, y.value))
    }

type ImageFileId = ImageFileId.Type

object DataId extends NewType[String]
type DataId = DataId.Type
