// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.web.server.http4s.encoder

import cats.effect.Concurrent
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.booPickle.instances.BooPickleInstances
import observe.model.Conditions
import observe.model.Observation
import observe.model.Operator
import observe.model.*
import observe.model.boopickle.ModelBooPicklers
import observe.model.enums.CloudCover
import observe.model.enums.ImageQuality
import observe.model.enums.SkyBackground
import observe.model.enums.WaterVapor

/**
 * Contains http4s implicit encoders of model objects
 */
trait BooEncoders extends ModelBooPicklers with BooPickleInstances {
  // Decoders, Included here instead of the on the object definitions to avoid
  // a circular dependency on http4s
  given [F[_]: Concurrent]: EntityDecoder[F, UserLoginRequest]     =
    booOf[F, UserLoginRequest]
  given [F[_]]: EntityEncoder[F, UserDetails]                      =
    booEncoderOf[F, UserDetails]
  given [F[_]]: EntityEncoder[F, Operator]                         =
    booEncoderOf[F, Operator]
  given [F[_]: Concurrent]: EntityDecoder[F, Observation.Id]       =
    booOf[F, Observation.Id]
  given [F[_]: Concurrent]: EntityDecoder[F, List[Observation.Id]] =
    booOf[F, List[Observation.Id]]
  given [F[_]]: EntityEncoder[F, SequencesQueue[Observation.Id]]   =
    booEncoderOf[F, SequencesQueue[Observation.Id]]
  given [F[_]: Concurrent]: EntityDecoder[F, Conditions]           =
    booOf[F, Conditions]
  given [F[_]: Concurrent]: EntityDecoder[F, ImageQuality]         =
    booOf[F, ImageQuality]
  given [F[_]: Concurrent]: EntityDecoder[F, WaterVapor]           =
    booOf[F, WaterVapor]
  given [F[_]: Concurrent]: EntityDecoder[F, SkyBackground]        =
    booOf[F, SkyBackground]
  given [F[_]: Concurrent]: EntityDecoder[F, CloudCover]           =
    booOf[F, CloudCover]
}

/**
 * Contains http4s implicit encoders of model objects, from the point of view of a client
 */
trait ClientBooEncoders extends ModelBooPicklers with BooPickleInstances {
  given [F[_]]: EntityEncoder[F, UserLoginRequest] =
    booEncoderOf[F, UserLoginRequest]
  given [F[_]]: EntityEncoder[F, WaterVapor]       =
    booEncoderOf[F, WaterVapor]
  given [F[_]]: EntityEncoder[F, ImageQuality]     =
    booEncoderOf[F, ImageQuality]
  given [F[_]]: EntityEncoder[F, SkyBackground]    =
    booEncoderOf[F, SkyBackground]
  given [F[_]]: EntityEncoder[F, CloudCover]       =
    booEncoderOf[F, CloudCover]
}
