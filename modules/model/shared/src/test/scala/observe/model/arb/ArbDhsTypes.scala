// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.arb

import observe.model.dhs.*
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen

trait ArbDhsTypes {

  given arbImageFileId: Arbitrary[ImageFileId] =
    Arbitrary {
      Gen.alphaNumStr.map(ImageFileId.apply(_))
    }

  given imageFileIdCogen: Cogen[ImageFileId] =
    Cogen[String]
      .contramap(_.value)

  given arbDataId: Arbitrary[DataId] =
    Arbitrary {
      Gen.alphaNumStr.map(DataId.apply(_))
    }

  given dataIdCogen: Cogen[DataId] =
    Cogen[String]
      .contramap(_.value)
}

object ArbDhsTypes extends ArbDhsTypes
