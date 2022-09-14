// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.common

import clue.GraphQLOperation
import clue.annotation.GraphQL
import lucuma.schemas.ObservationDB
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import lucuma.core.math
import lucuma.core.enums
import lucuma.core.model
import cats.syntax.functor._
import lucuma.core.model.sequence.{Atom, Step}

import java.time
import lucuma.core.model.{ExecutionEvent, Observation, Target}

// gql: import lucuma.schemas.decoders._
// gql: import io.circe.refined._

object ObsQueriesGQL {

  // I don't know why, but these implicits prevent several warnings in the generated code
  implicit val obsIdCodex: Decoder[Observation.Id] with Encoder[Observation.Id]         =
    Observation.Id.GidId
  implicit val atomIdCodex: Decoder[Atom.Id] with Encoder[Atom.Id]                      = Atom.Id.UidId
  implicit val stepIdCodex: Decoder[Step.Id] with Encoder[Step.Id]                      = Step.Id.UidId
  implicit val targetIdCodex: Decoder[Target.Id] with Encoder[Target.Id]                = Target.Id.GidId
  implicit val eventIdCodex: Decoder[ExecutionEvent.Id] with Encoder[ExecutionEvent.Id] =
    ExecutionEvent.Id.GidId

  @GraphQL
  trait ActiveObservationIdsQuery extends GraphQLOperation[ObservationDB] {
    val document = """
      query {
        observations(WHERE: { status: { eq: { EQ: READY } } }) {
          matches {
            id
            title
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
          title
          status
          activeStatus
          plannedTime {
            execution {
              microseconds
            }
          }
          targetEnvironment {
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
          execution {
            config:executionConfig {
              instrument
              ... on GmosNorthExecutionConfig {
                staticN:static {
                  stageMode
                  detector
                  mosPreImaging
                  nodAndShuffle {
                    ...nodAndShuffle
                  }
                }
                acquisitionN:acquisition {
                  ...gmosNorthExecutionSequence
                }
                scienceN:science {
                  ...gmosNorthExecutionSequence
                }
              }
              ... on GmosSouthExecutionConfig {
                staticS:static {
                  stageMode
                  detector
                  mosPreImaging
                  nodAndShuffle {
                    ...nodAndShuffle
                  }
                }
                acquisitionS: acquisition {
                  ...gmosSouthExecutionSequence
                }
                scienceS:science {
                  ...gmosSouthExecutionSequence
                }
              }
            }
          }
        }
      }

      fragment northAtomFields on GmosNorthAtom {
        id
        steps {
          id
          stepConfig {
            ...stepConfig
          }
          instrumentConfig {
            exposure {
              microseconds
            }
            readout {
              ...gmosCcdMode
            }
            dtax
            roi
            gratingConfig {
              grating
              order
              wavelength {
                picometers
              }
            }
            filter
            fpu {
              builtin
              customMask {
                filename
                slitWidth
              }
            }
          }
        }
      }

      fragment southAtomFields on GmosSouthAtom {
        id
        steps {
          id
          stepConfig {
            ...stepConfig
          }
          instrumentConfig {
            exposure {
              microseconds
            }
            readout {
              ...gmosCcdMode
            }
            dtax
            roi
            gratingConfig {
              grating
              order
              wavelength {
                picometers
              }
            }
            filter
            fpu {
              builtin
              customMask {
                filename
                slitWidth
              }
            }
          }
        }
      }

      fragment offset on Offset {
        p {
          microarcseconds
        }
        q {
          microarcseconds
        }
      }

      fragment nodAndShuffle on GmosNodAndShuffle {
        posA {
          ...offset
        }
        posB {
          ...offset
        }
        eOffset
        shuffleOffset
        shuffleCycles
      }

      fragment gmosNorthExecutionSequence on GmosNorthExecutionSequence {
        nextAtom {
          ...northAtomFields
        }
        possibleFuture {
          ...northAtomFields
        }
      }

      fragment gmosSouthExecutionSequence on GmosSouthExecutionSequence {
        nextAtom {
          ...southAtomFields
        }
        possibleFuture {
          ...southAtomFields
        }
      }

      fragment gmosCcdMode on GmosCcdMode {
        xBin
        yBin
        ampCount
        ampGain
        ampReadMode
      }

      fragment gcal on Gcal {
        filter
        diffuser
        shutter
      }

      fragment stepConfig on StepConfig {
        stepType
        ... on Gcal {
          ...gcal
        }
        ... on Science {
          offset {
            ...offset
          }
        }
        ... on Bias {
        }
        ... on Dark {
        }
      }

    """

    implicit def offsetComponentDecoder[T]: Decoder[math.Offset.Component[T]] =
      Decoder.instance(c =>
        c.downField("microarcseconds")
          .as[Long]
          .map(
            math.Angle.signedMicroarcseconds.reverse
              .andThen(math.Offset.Component.angle[T].reverse)
              .get
          )
      )

    implicit val offsetDecoder: Decoder[math.Offset] = Decoder.instance(c =>
      for {
        p <- c.downField("p").as[math.Offset.P]
        q <- c.downField("q").as[math.Offset.Q]
      } yield math.Offset(p, q)
    )

//    implicit val seqStepConfigDecoder: Decoder[SeqStepConfig] = List[Decoder[SeqStepConfig]](
//      Decoder[SeqStepConfig.SeqScienceStep].widen,
//      Decoder[SeqStepConfig.Gcal].widen,
//      Decoder[SeqStepConfig.Bias].widen,
//      Decoder[SeqStepConfig.Dark].widen
//    ).reduceLeft(_ or _)

    implicit val seqSiteDecoder: Decoder[GmosSite] = List[Decoder[GmosSite]](
      Decoder[GmosSite.North].widen,
      Decoder[GmosSite.South].widen
    ).reduceLeft(_ or _)

//    def seqFpuDecoder[Site <: GmosSite](implicit
//      d1: Decoder[GmosFpu.GmosBuiltinFpu[Site]],
//      d2: Decoder[GmosFpu.GmosCustomMask[Site]]
//    ): Decoder[GmosFpu[Site]] = List[Decoder[GmosFpu[Site]]](
//      Decoder[GmosFpu.GmosBuiltinFpu[Site]].widen,
//      Decoder[GmosFpu.GmosCustomMask[Site]].widen
//    ).reduceLeft(_ or _)

//    implicit val fpuSouthDecoder: Decoder[GmosFpu[GmosSite.South]] = seqFpuDecoder[GmosSite.South]
//    implicit val fpuNorthDecoder: Decoder[GmosFpu[GmosSite.North]] = seqFpuDecoder[GmosSite.North]

    sealed trait GmosSite {
      type Detector <: AnyRef
      type Grating <: AnyRef
      type BuiltInFpu <: AnyRef
      type Filter <: AnyRef
      type StageMode <: AnyRef
    }
    object GmosSite       {
      case class North() extends GmosSite {
        override type Detector   = enums.GmosNorthDetector
        override type Grating    = enums.GmosNorthGrating
        override type BuiltInFpu = enums.GmosNorthFpu
        override type Filter     = enums.GmosNorthFilter
        override type StageMode  = enums.GmosNorthStageMode
      }
      case class South() extends GmosSite {
        override type Detector   = enums.GmosSouthDetector
        override type Grating    = enums.GmosSouthGrating
        override type BuiltInFpu = enums.GmosSouthFpu
        override type Filter     = enums.GmosSouthFilter
        override type StageMode  = enums.GmosSouthStageMode
      }
    }

    trait GmosGrating[Site <: GmosSite]          {
      val grating: Site#Grating
      val order: enums.GmosGratingOrder
      val wavelength: math.Wavelength
    }

    case class GmosCustomMask(
      filename:  String,
      slitWidth: enums.GmosCustomSlitWidth
    )
    case class GmosFpu[Site <: GmosSite](
      customMask: Option[GmosCustomMask],
      builtin:    Option[Site#BuiltInFpu]
    )

    case class GmosReadout(
      xBin:        enums.GmosXBinning,
      yBin:        enums.GmosYBinning,
      ampCount:    enums.GmosAmpCount,
      ampGain:     enums.GmosAmpGain,
      ampReadMode: enums.GmosAmpReadMode
    )
    trait GmosInstrumentConfig[Site <: GmosSite] {
      val exposure: time.Duration
      val readout: GmosReadout
      val dtax: enums.GmosDtax
      val roi: enums.GmosRoi
      val gratingConfig: Option[GmosGrating[Site]]
      val filter: Option[Site#Filter]
      val fpu: Option[GmosFpu[Site]]
    }
    sealed trait SeqStepConfig
    object SeqStepConfig                         {
      case class SeqScienceStep(offset: math.Offset) extends SeqStepConfig
      case class Gcal(
        filter:   enums.GcalFilter,
        diffuser: enums.GcalDiffuser,
        shutter:  enums.GcalShutter
      ) extends SeqStepConfig
      case object Dark extends SeqStepConfig
      case object Bias extends SeqStepConfig
    }
    trait InsConfig                              {
      type StaticConfig
      type StepConfig
    }
    object InsConfig                             {
      trait Gmos[S <: GmosSite] extends InsConfig {
        override type StaticConfig = GmosStatic[S]
        override type StepConfig   = GmosInstrumentConfig[S]
      }
      type GmosNorth = Gmos[GmosSite.North]
      type GmosSouth = Gmos[GmosSite.South]
    }

    trait SeqStep[I <: InsConfig]      {
      val id: model.sequence.Step.Id
      val instrumentConfig: I#StepConfig
      val stepConfig: SeqStepConfig
    }
    trait SeqAtom[I <: InsConfig]      {
      val id: model.sequence.Atom.Id
      val steps: List[SeqStep[I]]
    }
    trait Sequence[I <: InsConfig]     {
      val nextAtom: Option[SeqAtom[I]]
      val possibleFuture: List[SeqAtom[I]]
    }
    trait GmosNodAndShuffle            {
      val posA: math.Offset
      val posB: math.Offset
      val eOffset: enums.GmosEOffsetting
      val shuffleOffset: Int
      val shuffleCycles: Int
    }
    trait GmosStatic[Site <: GmosSite] {
      val stageMode: Site#StageMode
      val detector: Site#Detector
      val mosPreImaging: enums.MosPreImaging
      val nodAndShuffle: Option[GmosNodAndShuffle]
    }

    object Data {
      object Observation {
        object Execution {
          object Config {
            object GmosNorthExecutionConfig {
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
                trait NextAtom extends SeqAtom[InsConfig.GmosNorth]

                object NextAtom {
                  trait Steps extends SeqStep[InsConfig.GmosNorth]

                  object Steps {
                    // object StepType
                    trait InstrumentConfig extends GmosInstrumentConfig[GmosSite.North]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      trait GratingConfig extends GmosGrating[GmosSite.North]

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[GmosSite.North]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }

                trait PossibleFuture extends SeqAtom[InsConfig.GmosNorth]

                object PossibleFuture {
                  trait Steps extends SeqStep[InsConfig.GmosNorth]

                  object Steps {
                    trait InstrumentConfig extends GmosInstrumentConfig[GmosSite.North]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      trait GratingConfig extends GmosGrating[GmosSite.North]

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[GmosSite.North]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }
              }

              trait ScienceN extends Sequence[InsConfig.GmosNorth]

              object ScienceN {
                trait NextAtom extends SeqAtom[InsConfig.GmosNorth]

                object NextAtom {
                  trait Steps extends SeqStep[InsConfig.GmosNorth]

                  object Steps {
                    trait InstrumentConfig extends GmosInstrumentConfig[GmosSite.North]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      trait GratingConfig extends GmosGrating[GmosSite.North]

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[GmosSite.North]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }

                trait PossibleFuture extends SeqAtom[InsConfig.GmosNorth]

                object PossibleFuture {
                  trait Steps extends SeqStep[InsConfig.GmosNorth]

                  object Steps {
                    trait InstrumentConfig extends GmosInstrumentConfig[GmosSite.North]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      trait GratingConfig extends GmosGrating[GmosSite.North]

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[GmosSite.North]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }
              }
            }

            object GmosSouthExecutionConfig {
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
                trait NextAtom extends SeqAtom[InsConfig.GmosSouth]

                object NextAtom {
                  trait Steps extends SeqStep[InsConfig.GmosSouth]

                  object Steps {
                    trait InstrumentConfig extends GmosInstrumentConfig[GmosSite.South]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      trait GratingConfig extends GmosGrating[GmosSite.South]

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[GmosSite.South]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }

                trait PossibleFuture extends SeqAtom[InsConfig.GmosSouth]

                object PossibleFuture {
                  trait Steps extends SeqStep[InsConfig.GmosSouth]

                  object Steps {
                    trait InstrumentConfig extends GmosInstrumentConfig[GmosSite.South]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      trait GratingConfig extends GmosGrating[GmosSite.South]

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[GmosSite.South]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }
              }

              trait ScienceS extends Sequence[InsConfig.GmosSouth]

              object ScienceS {
                trait NextAtom extends SeqAtom[InsConfig.GmosSouth]

                object NextAtom {
                  trait Steps extends SeqStep[InsConfig.GmosSouth]

                  object Steps {
                    // object StepType
                    trait InstrumentConfig extends GmosInstrumentConfig[GmosSite.South]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      trait GratingConfig extends GmosGrating[GmosSite.South]

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[GmosSite.South]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }

                trait PossibleFuture extends SeqAtom[InsConfig.GmosSouth]

                object PossibleFuture {
                  trait Steps extends SeqStep[InsConfig.GmosSouth]

                  object Steps {
                    trait InstrumentConfig extends GmosInstrumentConfig[GmosSite.South]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      trait GratingConfig extends GmosGrating[GmosSite.South]

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[GmosSite.South]
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
  }

  @GraphQL
  trait ProgramObservationsEditSubscription extends GraphQLOperation[ObservationDB] {
    val document = """
      subscription {
        observationEdit(programId:"p-2") {
          id
        }
      }
    """
  }

  @GraphQL
  trait ObservationEditSubscription extends GraphQLOperation[ObservationDB] {
    val document = """
      subscription($obsId: ObservationId!) {
        observationEdit(observationId: $obsId) {
          id
        }
      }
    """
  }

  @GraphQL
  trait AddSequenceEventMutation extends GraphQLOperation[ObservationDB] {
    val document = """
      mutation($vId: VisitId!, $obsId: ObservationId!, $cmd: SequenceCommand!) {
        addSequenceEvent(input: { visitId: $vId, location: { observationId: $obsId }, payload: { command: $cmd } } ) {
          event {
            visitId
            received
          }
        }
      }
      """
  }

  @GraphQL
  trait AddStepEventMutation extends GraphQLOperation[ObservationDB] {
    val document = """
      mutation($vId: VisitId!, $obsId: ObservationId!, $stpId: StepId!, $seqType: SequenceType!, $stg: StepStage!)  {
        addStepEvent(input: { visitId: $vId, location: { observationId: $obsId, stepId: $stpId }, payload: { sequenceType: $seqType, stage: $stg } } ) {
          event {
            received
          }
        }
      }
      """
  }

  @GraphQL
  trait AddDatasetEventMutation extends GraphQLOperation[ObservationDB] {
    val document = """
      mutation($vId: VisitId!, $obsId: ObservationId!, $stpId: StepId!, $dtIdx: PosInt!, $stg: DatasetStage!, $flName: DatasetFilename)  {
        addDatasetEvent(input: { visitId: $vId, location: { observationId: $obsId, stepId: $stpId, index: $dtIdx }, payload: { datasetStage: $stg, filename: $flName } } ) {
          event {
            received
          }
        }
      }
      """
  }

}
