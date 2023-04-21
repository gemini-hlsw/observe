// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.common

import clue.GraphQLOperation
import clue.annotation.GraphQL
import lucuma.schemas.ObservationDB
import lucuma.core.enums.Site
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import lucuma.core.math
import lucuma.core.enums
import lucuma.core.model
import cats.syntax.functor.*
import lucuma.core.model.sequence.{Atom, GmosGratingConfig, Step}

import java.time
import lucuma.core.model.{ExecutionEvent, Observation, Target}

// gql: import lucuma.schemas.decoders._
// gql: import io.circe.refined._

object ObsQueriesGQL {

  // I don't know why, but these implicits prevent several warnings in the generated code
//  implicit val obsIdCodex: Decoder[Observation.Id] with Encoder[Observation.Id]         =
//    Observation.Id.GidId
//  implicit val atomIdCodex: Decoder[Atom.Id] with Encoder[Atom.Id]                      = Atom.Id.UidId
//  implicit val stepIdCodex: Decoder[Step.Id] with Encoder[Step.Id]                      = Step.Id.UidId
//  implicit val targetIdCodex: Decoder[Target.Id] with Encoder[Target.Id]                = Target.Id.GidId
//  implicit val eventIdCodex: Decoder[ExecutionEvent.Id] with Encoder[ExecutionEvent.Id] =
//    ExecutionEvent.Id.GidId

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

    given [T]: Decoder[math.Offset.Component[T]] =
      Decoder.instance(c =>
        c.downField("microarcseconds")
          .as[Long]
          .map(
            math.Angle.signedMicroarcseconds.reverse
              .andThen(math.Offset.Component.angle[T].reverse)
              .get
          )
      )

    given Decoder[math.Offset] = Decoder.instance(c =>
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

//    given Decoder[GmosSite] = List[Decoder[GmosSite]](
//      Decoder[Site.GN.type].widen,
//      Decoder[Site.GS.type].widen
//    ).reduceLeft(_ or _)

//    def seqFpuDecoder[S <: Site](using
//      d1: Decoder[GmosFpu.GmosBuiltinFpu[S]],
//      d2: Decoder[GmosFpu.GmosCustomMask[S]]
//    ): Decoder[GmosFpu[S]] = List[Decoder[GmosFpu[S]]](
//      Decoder[GmosFpu.GmosBuiltinFpu[S]].widen,
//      Decoder[GmosFpu.GmosCustomMask[S]].widen
//    ).reduceLeft(_ or _)

//    implicit val fpuSouthDecoder: Decoder[GmosFpu[Site.GS.type]] = seqFpuDecoder[Site.GS.type]
//    implicit val fpuNorthDecoder: Decoder[GmosFpu[Site.GN.type]] = seqFpuDecoder[Site.GN.type]

    sealed trait GmosSite[T <: Site] {
      type Detector      = T match {
        case Site.GN.type => GmosSite.North.Detector
        case Site.GS.type => GmosSite.South.Detector
      }
      type GratingConfig = T match {
        case Site.GN.type => GmosSite.North.Grating
        case Site.GS.type => GmosSite.South.Grating
      }
      type BuiltInFpu    = T match {
        case Site.GN.type => GmosSite.North.BuiltInFpu
        case Site.GS.type => GmosSite.South.BuiltInFpu
      }
      type Filter        = T match {
        case Site.GN.type => GmosSite.North.Filter
        case Site.GS.type => GmosSite.South.Filter
      }
      type StageMode     = T match {
        case Site.GN.type => GmosSite.North.StageMode
        case Site.GS.type => GmosSite.South.StageMode
      }
    }
    object GmosSite                  {

      object North {
        type Detector   = enums.GmosNorthDetector
        type Grating    = GmosGratingConfig.North
        type BuiltInFpu = enums.GmosNorthFpu
        type Filter     = enums.GmosNorthFilter
        type StageMode  = enums.GmosNorthStageMode
      }
      object South {
        type Detector   = enums.GmosSouthDetector
        type Grating    = GmosGratingConfig.South
        type BuiltInFpu = enums.GmosSouthFpu
        type Filter     = enums.GmosSouthFilter
        type StageMode  = enums.GmosSouthStageMode
      }
    }

    case class GmosCustomMask(
      filename:  String,
      slitWidth: enums.GmosCustomSlitWidth
    )
    case class GmosFpu[S <: Site](
      customMask: Option[GmosCustomMask],
      builtin:    Option[GmosSite[S]#BuiltInFpu]
    )

    case class GmosReadout(
      xBin:        enums.GmosXBinning,
      yBin:        enums.GmosYBinning,
      ampCount:    enums.GmosAmpCount,
      ampGain:     enums.GmosAmpGain,
      ampReadMode: enums.GmosAmpReadMode
    )
    trait GmosInstrumentConfig[S <: Site]         {
      val exposure: time.Duration
      val readout: GmosReadout
      val dtax: enums.GmosDtax
      val roi: enums.GmosRoi
      val gratingConfig: Option[GmosSite[S]#GratingConfig]
      val filter: Option[GmosSite[S]#Filter]
      val fpu: Option[GmosFpu[S]]
    }
    sealed trait SeqStepConfig
    object SeqStepConfig                          {
      case class SeqScienceStep(offset: math.Offset) extends SeqStepConfig
      case class Gcal(
        filter:   enums.GcalFilter,
        diffuser: enums.GcalDiffuser,
        shutter:  enums.GcalShutter
      ) extends SeqStepConfig
      case object Dark                               extends SeqStepConfig
      case object Bias                               extends SeqStepConfig
    }
    sealed trait InsConfig[T <: enums.Instrument] {
      type StaticConfig = T match {
        case enums.Instrument.GmosNorth.type => InsConfig.GmosNorth.StaticConfig
        case enums.Instrument.GmosSouth.type => InsConfig.GmosSouth.StaticConfig
      }
      type StepConfig   = T match {
        case enums.Instrument.GmosNorth.type => InsConfig.GmosNorth.StepConfig
        case enums.Instrument.GmosSouth.type => InsConfig.GmosSouth.StepConfig
      }
    }
    object InsConfig                              {
      trait Gmos[S <: Site] {
        type StaticConfig = GmosStatic[S]
        type StepConfig   = GmosInstrumentConfig[S]
      }
      object GmosNorth extends Gmos[Site.GN.type]
      object GmosSouth extends Gmos[Site.GS.type]
    }

    trait SeqStep[I <: enums.Instrument]  {
      val id: model.sequence.Step.Id
      val instrumentConfig: InsConfig[I]#StepConfig
      val stepConfig: SeqStepConfig
    }
    trait SeqAtom[I <: enums.Instrument]  {
      val id: model.sequence.Atom.Id
      val steps: List[SeqStep[I]]
    }
    trait Sequence[I <: enums.Instrument] {
      val nextAtom: Option[SeqAtom[I]]
      val possibleFuture: List[SeqAtom[I]]
    }
    trait GmosNodAndShuffle               {
      val posA: math.Offset
      val posB: math.Offset
      val eOffset: enums.GmosEOffsetting
      val shuffleOffset: Int
      val shuffleCycles: Int
    }
    trait GmosStatic[S <: Site]           {
      val stageMode: GmosSite[S]#StageMode
      val detector: GmosSite[S]#Detector
      val mosPreImaging: enums.MosPreImaging
      val nodAndShuffle: Option[GmosNodAndShuffle]
    }

    object Data {
      object Observation {
        object Execution {
          object Config {
            object GmosNorthExecutionConfig {
              trait StaticN extends GmosStatic[Site.GN.type]

              object StaticN {
                type StageMode = GmosSite.North.StageMode

                trait NodAndShuffle extends GmosNodAndShuffle

                object NodAndShuffle {
                  type PosA = math.Offset
                  type PosB = math.Offset
                }
              }

              trait AcquisitionN extends Sequence[enums.Instrument.GmosNorth.type]

              object AcquisitionN {
                trait NextAtom extends SeqAtom[enums.Instrument.GmosNorth.type]

                object NextAtom {
                  trait Steps extends SeqStep[enums.Instrument.GmosNorth.type]

                  object Steps {
                    // object StepType
                    trait InstrumentConfig extends GmosInstrumentConfig[Site.GN.type]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      type GratingConfig = GmosGratingConfig.North

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[Site.GN.type]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }

                trait PossibleFuture extends SeqAtom[enums.Instrument.GmosNorth.type]

                object PossibleFuture {
                  trait Steps extends SeqStep[enums.Instrument.GmosNorth.type]

                  object Steps {
                    trait InstrumentConfig extends GmosInstrumentConfig[Site.GN.type]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      type GratingConfig = GmosGratingConfig.North

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[Site.GN.type]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }
              }

              trait ScienceN extends Sequence[enums.Instrument.GmosNorth.type]

              object ScienceN {
                trait NextAtom extends SeqAtom[enums.Instrument.GmosNorth.type]

                object NextAtom {
                  trait Steps extends SeqStep[enums.Instrument.GmosNorth.type]

                  object Steps {
                    trait InstrumentConfig extends GmosInstrumentConfig[Site.GN.type]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      type GratingConfig = GmosGratingConfig.North

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[Site.GN.type]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }

                trait PossibleFuture extends SeqAtom[enums.Instrument.GmosNorth.type]

                object PossibleFuture {
                  trait Steps extends SeqStep[enums.Instrument.GmosNorth.type]

                  object Steps {
                    trait InstrumentConfig extends GmosInstrumentConfig[Site.GN.type]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      type GratingConfig = GmosGratingConfig.North

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[Site.GN.type]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }
              }
            }

            object GmosSouthExecutionConfig {
              trait StaticS extends GmosStatic[Site.GS.type]

              object StaticS {
                type StageMode = GmosSite.South.StageMode

                trait NodAndShuffle extends GmosNodAndShuffle

                object NodAndShuffle {
                  type PosA = math.Offset
                  type PosB = math.Offset
                }
              }

              trait AcquisitionS extends Sequence[enums.Instrument.GmosSouth.type]

              object AcquisitionS {
                trait NextAtom extends SeqAtom[enums.Instrument.GmosSouth.type]

                object NextAtom {
                  trait Steps extends SeqStep[enums.Instrument.GmosSouth.type]

                  object Steps {
                    trait InstrumentConfig extends GmosInstrumentConfig[Site.GS.type]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      type GratingConfig = GmosGratingConfig.South

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[Site.GS.type]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }

                trait PossibleFuture extends SeqAtom[enums.Instrument.GmosSouth.type]

                object PossibleFuture {
                  trait Steps extends SeqStep[enums.Instrument.GmosSouth.type]

                  object Steps {
                    trait InstrumentConfig extends GmosInstrumentConfig[Site.GS.type]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      type GratingConfig = GmosGratingConfig.South

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[Site.GS.type]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }
              }

              trait ScienceS extends Sequence[enums.Instrument.GmosSouth.type]

              object ScienceS {
                trait NextAtom extends SeqAtom[enums.Instrument.GmosSouth.type]

                object NextAtom {
                  trait Steps extends SeqStep[enums.Instrument.GmosSouth.type]

                  object Steps {
                    // object StepType
                    trait InstrumentConfig extends GmosInstrumentConfig[Site.GS.type]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      type GratingConfig = GmosGratingConfig.South

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[Site.GS.type]
                      type Readout = GmosReadout
                    }

                    type StepConfig = SeqStepConfig
                  }
                }

                trait PossibleFuture extends SeqAtom[enums.Instrument.GmosSouth.type]

                object PossibleFuture {
                  trait Steps extends SeqStep[enums.Instrument.GmosSouth.type]

                  object Steps {
                    trait InstrumentConfig extends GmosInstrumentConfig[Site.GS.type]

                    object InstrumentConfig {
                      type Exposure = time.Duration

                      type GratingConfig = GmosGratingConfig.South

                      object GratingConfig {
                        type Wavelength = math.Wavelength
                      }

                      type Fpu     = GmosFpu[Site.GS.type]
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
