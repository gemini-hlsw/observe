// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.server.keywords

import observe.model.enums.KeywordName
import org.scalacheck.Arbitrary.*
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen
import lucuma.core.util.arb.ArbEnumerated

trait KeywordArbitraries extends ArbEnumerated {
  given Arbitrary[KeywordType] = Arbitrary {
    Gen.oneOf(TypeInt8, TypeInt16, TypeInt32, TypeFloat, TypeDouble, TypeBoolean, TypeString)
  }
  given Cogen[KeywordType]     =
    Cogen[String].contramap(_.productPrefix)

  given Arbitrary[InternalKeyword] = Arbitrary {
    for {
      name  <- arbitrary[KeywordName]
      kt    <- arbitrary[KeywordType]
      value <- Gen.listOfN(17, Gen.alphaChar)
    } yield InternalKeyword(name, kt, value.mkString)
  }
  given Cogen[InternalKeyword]     =
    Cogen[(KeywordName, KeywordType, String)].contramap(x => (x.name, x.keywordType, x.value))

  given Arbitrary[KeywordBag] = Arbitrary {
    arbitrary[List[InternalKeyword]].map(KeywordBag.apply)
  }
  given Cogen[KeywordBag]     =
    Cogen[List[InternalKeyword]].contramap(_.keywords)
}
