// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.common

import clue.GraphQLOperation
import clue.annotation.GraphQL
import lucuma.schemas.ObservationDB
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import lucuma.core.math
import lucuma.core.enum
import lucuma.core.model
import cats.syntax.functor._

import java.time
import lucuma.core.model.Atom
import lucuma.core.model.Observation
import lucuma.core.model.Step
import lucuma.core.model.Target

// gql: import lucuma.schemas.decoders._
// gql: import io.circe.refined._

object ObsQueriesGQL {

  // I don't know why, but these implicits prevent several warnings in the generated code
  implicit val obsIdCodex: Decoder[Observation.Id] with Encoder[Observation.Id] = Observation.Id.GidId
  implicit val atomIdCodex: Decoder[Atom.Id] with Encoder[Atom.Id] = Atom.Id.GidId
  implicit val stepIdCodex: Decoder[Step.Id] with Encoder[Step.Id] = Step.Id.GidId
  implicit val targetIdCodex: Decoder[Target.Id] with Encoder[Target.Id] = Target.Id.GidId

  @GraphQL
  trait ActiveObservationIdsQuery extends GraphQLOperation[ObservationDB] {
    val document = """
      query {
        observations(programId: "p-2") {
          nodes {
            id
            name
          }
        }
      }
    """
  }

  @GraphQL
  trait ObsQuery extends GraphQLOperation[ObservationDB] {
    val document = """
      query($obsId: ObservationId!) {
        observation(observationId: $obsId) {
          id
          name
          targets {
            firstScienceTarget {
              targetId: id
              targetName: name
            }
          }
          constraintSet {
            imageQuality
            cloudExtinction
            skyBackground
            waterVapor
          }
          status
          activeStatus
          plannedTime {
            execution {
              microseconds
            }
          }
          config:manualConfig {
            instrument
            plannedTime {
              total {
                microseconds
              }
            }
            ... on GmosNorthConfig {
              staticN:static {
                stageMode
                detector
                mosPreImaging
                nodAndShuffle {
                  posA {
                    p {
                      microarcseconds
                    }
                    q {
                      microarcseconds
                    }
                  }
                  posB {
                    p {
                      microarcseconds
                    }
                    q {
                      microarcseconds
                    }
                  }
                  eOffset
                  shuffleOffset
                  shuffleCycles
                }
              }
              acquisitionN:acquisition {
                ...northSequenceFields
              }
              scienceN:science {
                ...northSequenceFields
              }
            }
            ... on GmosSouthConfig {
              staticS:static {
                stageMode
                detector
                mosPreImaging
                nodAndShuffle {
                  posA {
                    p {
                      microarcseconds
                    }
                    q {
                      microarcseconds
                    }
                  }
                  posB {
                    p {
                      microarcseconds
                    }
                    q {
                      microarcseconds
                    }
                  }
                  eOffset
                  shuffleOffset
                  shuffleCycles
                }
              }
              acquisitionS: acquisition {
                ...southSequenceFields
              }
              scienceS:science {
                ...southSequenceFields
              }
            }
          }
        }
      }

      fragment northSequenceFields on GmosNorthSequence {
        atoms {
          id
          steps {
            id
            stepType
            instrumentConfig {
              exposure {
                microseconds
              }
              grating {
                disperser
                order
                wavelength {
                  picometers
                }
              }
              filter
              fpu {
                ... on GmosNorthBuiltinFpu {
                  builtin
                }
                ... on GmosCustomMask {
                  filename
                  slitWidth
                }
              }
              readout {
                xBin
                yBin
                ampCount
                ampGain
                ampReadMode
              }
              dtax
              roi
            }
            stepConfig {
              ... on Gcal {
                filter
                diffuser
                shutter
              }
              ... on Science {
                offset {
                  p {
                    microarcseconds
                  }
                  q {
                    microarcseconds
                  }
                }
              }
            }
          }
        }
      }

      fragment southSequenceFields on GmosSouthSequence {
        atoms {
          id
          steps {
            id
            stepType
            instrumentConfig {
              exposure {
                microseconds
              }
              grating {
                disperser
                order
                wavelength {
                  picometers
                }
              }
              fpu {
                ... on GmosSouthBuiltinFpu {
                  builtin
                }
                ... on GmosCustomMask {
                  filename
                  slitWidth
                }
              }
              filter
              readout {
                xBin
                yBin
                ampCount
                ampGain
                ampReadMode
              }
              dtax
              roi
            }
            stepConfig {
              ... on Gcal {
                filter
                diffuser
                shutter
              }
              ... on Science {
                offset {
                  p {
                    microarcseconds
                  }
                  q {
                    microarcseconds
                  }
                }
              }
            }
          }
        }
      }

    """

    implicit def offsetComponentDecoder[T]: Decoder[math.Offset.Component[T]] = Decoder.instance(c =>
      c.downField("microarcseconds")
        .as[Long]
        .map(math.Angle.signedMicroarcseconds.reverse.andThen(math.Offset.Component.angle[T].reverse).get)
    )

    implicit val offsetDecoder: Decoder[math.Offset] = Decoder.instance(c =>
      for {
        p <- c.downField("p").as[math.Offset.P]
        q <- c.downField("q").as[math.Offset.Q]
      } yield math.Offset(p, q)
    )

    implicit val seqStepConfigDecoder: Decoder[SeqStepConfig] = List[Decoder[SeqStepConfig]](
      Decoder[SeqStepConfig.SeqScienceStep].widen,
      Decoder[SeqStepConfig.Gcal].widen
    ).reduceLeft(_ or _)

    implicit val seqSiteDecoder: Decoder[GmosSite] = List[Decoder[GmosSite]](
      Decoder[GmosSite.North].widen,
      Decoder[GmosSite.South].widen
    ).reduceLeft(_ or _)

    def seqFpuDecoder[Site <: GmosSite](implicit d1: Decoder[GmosFpu.GmosBuiltinFpu[Site]],
      d2: Decoder[GmosFpu.GmosCustomMask[Site]]): Decoder[GmosFpu[Site]] = List[Decoder[GmosFpu[Site]]](
      Decoder[GmosFpu.GmosBuiltinFpu[Site]].widen,
      Decoder[GmosFpu.GmosCustomMask[Site]].widen
    ).reduceLeft(_ or _)
//
//    def gmosBuiltinFpuDecoder[Site <: GmosSite]: Decoder[GmosFpu.GmosBuiltinFpu[Site]] =
//      Decoder.instance(_.downField("builtin")
//      .as[Site#BuiltInFpu]).map()
//
//    def gmosCustomMaskDecoder[Site <: GmosSite]: Decoder[GmosFpu.GmosCustomMask[Site]] =
//      Decoder.instance{ c =>
//        for {
//          mask  <- c.downField("filename").as[String]
//          slitW <- c.downField("slitWidth").as[enum.GmosCustomSlitWidth]
//        } yield GmosFpu.GmosCustomMask()
//      }
//
    implicit val fpuSouthDecoder: Decoder[GmosFpu[GmosSite.South]] = seqFpuDecoder[GmosSite.South]
    implicit val fpuNorthDecoder: Decoder[GmosFpu[GmosSite.North]] = seqFpuDecoder[GmosSite.North]

    sealed trait GmosSite {
      type Detector <: AnyRef
      type Disperser <: AnyRef
      type BuiltInFpu <: AnyRef
      type Filter <: AnyRef
      type StageMode <: AnyRef
    }
    object GmosSite       {
      case class North() extends GmosSite {
        override type Detector   = enum.GmosNorthDetector
        override type Disperser  = enum.GmosNorthDisperser
        override type BuiltInFpu = enum.GmosNorthFpu
        override type Filter     = enum.GmosNorthFilter
        override type StageMode  = enum.GmosNorthStageMode
      }
      case class South() extends GmosSite {
        override type Detector   = enum.GmosSouthDetector
        override type Disperser  = enum.GmosSouthDisperser
        override type BuiltInFpu = enum.GmosSouthFpu
        override type Filter     = enum.GmosSouthFilter
        override type StageMode  = enum.GmosSouthStageMode
      }
    }

    trait GmosGrating[Site <: GmosSite]          {
      val disperser: Site#Disperser
      val order: enum.GmosDisperserOrder
      val wavelength: math.Wavelength
    }
    sealed trait GmosFpu[Site <: GmosSite]
    object GmosFpu {
      case class GmosBuiltinFpu[Site <: GmosSite](builtin: Site#BuiltInFpu) extends GmosFpu[Site]
      case class GmosCustomMask[Site <: GmosSite](
        filename: String,
        slitWidth: enum.GmosCustomSlitWidth
      ) extends GmosFpu[Site]
//      trait GmosBuiltinFpu[Site <: GmosSite] extends GmosFpu[Site] {
//        val builtin: Site#BuiltInFpu
//      }
//      trait GmosCustomMask[Site <: GmosSite] extends GmosFpu[Site] {
//        val filename: String
//        val slitWidth: enum.GmosCustomSlitWidth
//      }
    }
    case class GmosReadout                           (
      xBin: enum.GmosXBinning,
      yBin: enum.GmosYBinning,
      ampCount: enum.GmosAmpCount,
      ampGain: enum.GmosAmpGain,
      ampReadMode: enum.GmosAmpReadMode
    )
    trait GmosInstrumentConfig[Site <: GmosSite] {
      val exposure: time.Duration
      val grating: Option[GmosGrating[Site]]
      val fpu: Option[GmosFpu[Site]]
      val filter: Option[Site#Filter]
      val readout: GmosReadout
      val roi: enum.GmosRoi
      val dtax: enum.GmosDtax
    }
    sealed trait SeqStepConfig
    object SeqStepConfig                       {
      case class SeqScienceStep(offset: math.Offset) extends SeqStepConfig
      case class Gcal(filter: enum.GcalFilter, diffuser: enum.GcalDiffuser, shutter: enum.GcalShutter) extends SeqStepConfig
    }
    trait InsConfig{
      type StaticConfig
      type StepConfig
    }
    object InsConfig {
      trait Gmos[S <: GmosSite] extends InsConfig {
        override type StaticConfig = GmosStatic[S]
        override type StepConfig = GmosInstrumentConfig[S]
      }
      type GmosNorth = Gmos[GmosSite.North]
      type GmosSouth = Gmos[GmosSite.South]
    }

    trait SeqStep[I <: InsConfig]             {
      val id: model.Step.Id
      val instrumentConfig: I#StepConfig
      val stepType: enum.StepType
      val stepConfig: SeqStepConfig
    }
    trait SeqAtom[I <: InsConfig]             {
      val id: model.Atom.Id
      val steps: List[SeqStep[I]]
    }
    trait Sequence[I <: InsConfig]            {
      val atoms: List[SeqAtom[I]]
    }
    trait GmosNodAndShuffle {
      val posA: math.Offset
      val posB: math.Offset
      val eOffset: enum.GmosEOffsetting
      val shuffleOffset: Int
      val shuffleCycles: Int
    }
    trait GmosStatic[Site <: GmosSite]           {
      val stageMode: Site#StageMode
      val detector: Site#Detector
      val mosPreImaging: enum.MosPreImaging
      val nodAndShuffle: Option[GmosNodAndShuffle]
    }

    object Data {
      object Observation {
        object PlannedTime {
          type Execution = time.Duration
        }
        object Config {
          object GmosNorthConfig {
            trait StaticN extends GmosStatic[GmosSite.North]
            object StaticN {
              type StageMode = GmosSite.North#StageMode
              trait NodAndShuffle extends GmosNodAndShuffle
              object NodAndShuffle {
                type PosA = math.Offset
                type PosB = math.Offset
              }
            }
            trait AcquisitionN extends Sequence[InsConfig.GmosNorth]
            object AcquisitionN {
              trait Atoms extends SeqAtom[InsConfig.GmosNorth]
              object Atoms {
                trait Steps extends SeqStep[InsConfig.GmosNorth]
                object Steps {
                  // object StepType
                  trait InstrumentConfig extends GmosInstrumentConfig[GmosSite.North]
                  object InstrumentConfig {
                    type Exposure = time.Duration
                    trait Grating extends GmosGrating[GmosSite.North]
                    object Grating {
                      type Wavelength = math.Wavelength
                    }
                    type Fpu = GmosFpu[GmosSite.North]
                    type Readout = GmosReadout
                  }
                  type StepConfig = SeqStepConfig
                }
              }
            }
            trait ScienceN extends Sequence[InsConfig.GmosNorth]
            object ScienceN     {
              trait Atoms extends SeqAtom[InsConfig.GmosNorth]
              object Atoms {
                trait Steps extends SeqStep[InsConfig.GmosNorth]
                object Steps {
                  // object StepType
                  trait InstrumentConfig extends GmosInstrumentConfig[GmosSite.North]
                  object InstrumentConfig {
                    type Exposure = time.Duration
                    trait Grating extends GmosGrating[GmosSite.North]
                    object Grating {
                      type Wavelength = math.Wavelength
                    }
                    type Fpu = GmosFpu[GmosSite.North]
                    type Readout = GmosReadout
                  }
                  type StepConfig = SeqStepConfig
                }
              }
            }
          }

          object GmosSouthConfig {
            trait StaticS extends GmosStatic[GmosSite.South]
            object StaticS {
              type StageMode = GmosSite.South#StageMode
              trait NodAndShuffle extends GmosNodAndShuffle
              object NodAndShuffle {
                type PosA = math.Offset
                type PosB = math.Offset
              }
            }
            trait AcquisitionS extends Sequence[InsConfig.GmosSouth]
            object AcquisitionS {
              trait Atoms extends SeqAtom[InsConfig.GmosSouth]
              object Atoms {
                trait Steps extends SeqStep[InsConfig.GmosSouth]
                object Steps {
                  // object StepType
                  trait InstrumentConfig extends GmosInstrumentConfig[GmosSite.South]
                  object InstrumentConfig {
                    type Exposure = time.Duration
                    trait Grating extends GmosGrating[GmosSite.South]
                    object Grating {
                      type Wavelength = math.Wavelength
                    }
                    type Fpu = GmosFpu[GmosSite.South]
                    type Readout = GmosReadout
                  }
                  type StepConfig = SeqStepConfig
                }
              }
            }
            trait ScienceS extends Sequence[InsConfig.GmosSouth]
            object ScienceS     {
              trait Atoms extends SeqAtom[InsConfig.GmosSouth]
              object Atoms {
                trait Steps extends SeqStep[InsConfig.GmosSouth]
                object Steps {
                  // object StepType
                  trait InstrumentConfig extends GmosInstrumentConfig[GmosSite.South]
                  object InstrumentConfig {
                    type Exposure = time.Duration
                    trait Grating extends GmosGrating[GmosSite.South]
                    object Grating {
                      type Wavelength = math.Wavelength
                    }
                    type Fpu = GmosFpu[GmosSite.South]
                    type Readout = GmosReadout
                  }
                  type StepConfig = SeqStepConfig
                }
              }
            }
          }
        }
      }
    }
  }

//  @GraphQL
//  trait ProgramObservationsEditSubscription extends GraphQLOperation[ObservationDB] {
//    val document = """
//      subscription {
//        observationEdit(programId:"p-2") {
//          id
//        }
//      }
//    """
//  }
//
//  @GraphQL
//  trait ObservationEditSubscription extends GraphQLOperation[ObservationDB] {
//    val document = """
//      subscription($obsId: ObservationId!) {
//        observationEdit(observationId: $obsId) {
//          id
//        }
//      }
//    """
//  }

}
