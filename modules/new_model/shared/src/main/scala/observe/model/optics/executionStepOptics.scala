// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.optics

import lucuma.core.math.Angle
import lucuma.core.math.Offset
import lucuma.core.model.sequence.StepConfig
import lucuma.core.optics.Format
import lucuma.core.syntax.all.*
import monocle.Fold
import monocle.Getter
import monocle.Iso
import monocle.Lens
import monocle.Optional
import monocle.Prism
import monocle.Traversal
import monocle.function.At.atMap
import monocle.function.FilterIndex
import monocle.std.option.some
import monocle.std.string.*
import observe.model.ExecutionStep
import observe.model.ExecutionStepConfig
import observe.model.OffsetConfigResolver
import observe.model.ParamName
import observe.model.ParamValue
import observe.model.Parameters
import observe.model.enums.ExecutionStepType
import observe.model.enums.Guiding
import observe.model.enums.SystemName

// Focus on a param value
def paramValueL(param: ParamName): Lens[Parameters, Option[ParamValue]] =
  Parameters.value.andThen( // map of parameterss
    atMap[ParamName, ParamValue].at(param)
  )                         // parameter containing the name

// Focus on params with a prefix
def paramValuesWithPrefixT(param: ParamName): Traversal[Parameters, ParamValue] =
  Parameters.value.andThen(
    FilterIndex
      .mapFilterIndex[ParamName, ParamValue]
      .filterIndex(
        _.value.startsWith(param.value)
      ) // parameter containing the name
  )

// Possible set of observe parameters
def systemConfigL(system: SystemName): Lens[ExecutionStepConfig, Option[Parameters]] =
  ExecutionStepConfig.value.andThen( // map of systems
    atMap[SystemName, Parameters].at(system)
  )                                  // subsystem name

// Param name of a StepConfig
def configParamValueO(
  system: SystemName,
  param:  ParamName
): Optional[ExecutionStepConfig, ParamValue] =
  systemConfigL(system)
    .andThen( // observe parameters
      some[Parameters]
    )
    .andThen( // focus on the option
      paramValueL(system.withParam(param))
    )
    .andThen(          // find the target name
      some[ParamValue] // focus on the option
    )

val stringToStepTypeP: Prism[String, ExecutionStepType] =
  Prism(ExecutionStepType.fromLabel.get)(_.label)

val signedArcsecFormat: Format[String, Angle] =
  Format[String, BigDecimal](_.parseBigDecimalOption, _.toString)
    .andThen(Angle.signedDecimalArcseconds.reverse.asFormat)

def signedComponentFormat[A]: Format[String, Offset.Component[A]] =
  signedArcsecFormat.andThen(Offset.Component.angle[A].reverse)

val stringToDoubleP: Prism[String, Double] =
  Prism((x: String) => x.parseDoubleOption)(_.toString)

def stepObserveOptional[A](
  systemName: SystemName,
  param:      String,
  prism:      Prism[String, A]
): Optional[ExecutionStep, A] =
  ExecutionStep.config
    .andThen(       // configuration of the step
      configParamValueO(systemName, ParamName(param))
    )
    .andThen(ParamValue.value)
    .andThen(prism) // step type

val stepTypeO: Optional[ExecutionStep, ExecutionStepType] =
  stepObserveOptional(SystemName.Observe, "observeType", stringToStepTypeP)

// Composite lens to find the observe exposure time
val observeExposureTimeO: Optional[ExecutionStep, Double] =
  stepObserveOptional(SystemName.Observe, "exposureTime", stringToDoubleP)

// Composite lens to find the observe coadds
val observeCoaddsO: Optional[ExecutionStep, Int] =
  stepObserveOptional(SystemName.Observe, "coadds", stringToInt)

// Composite lens to find if the step is N&S
val isNodAndShuffleO: Optional[ExecutionStep, Boolean] =
  stepObserveOptional(SystemName.Instrument, "useNS", stringToBoolean)

// Composite lens to find the instrument fpu
val instrumentFPUO: Optional[ExecutionStep, String] =
  stepObserveOptional(SystemName.Instrument, "fpu", Iso.id)

// Composite lens to find the instrument slit width
val instrumentSlitWidthO: Optional[ExecutionStep, String] =
  stepObserveOptional(SystemName.Instrument, "slitWidth", Iso.id)

// Composite lens to find the instrument fpu custom mask
val instrumentFPUCustomMaskO: Optional[ExecutionStep, String] =
  stepObserveOptional(SystemName.Instrument, "fpuCustomMask", Iso.id)

// Composite lens to find the instrument filter
val instrumentFilterO: Optional[ExecutionStep, String] =
  stepObserveOptional(SystemName.Instrument, "filter", Iso.id)

// Composite lens to find the instrument disperser for GMOS
val instrumentDisperserO: Optional[ExecutionStep, String] =
  stepObserveOptional(SystemName.Instrument, "disperser", Iso.id)

// Composite lens to find the central wavelength for a disperser
val instrumentDisperserLambdaO: Optional[ExecutionStep, Double] =
  stepObserveOptional(SystemName.Instrument, "disperserLambda", stringToDoubleP)

// Instrument's mask
val instrumentMaskO: Optional[ExecutionStep, String] =
  stepObserveOptional(SystemName.Instrument, "mask", Iso.id)

// Composite lens to find the instrument observing mode on GPI
val instrumentObservingModeO: Optional[ExecutionStep, String] =
  stepObserveOptional(SystemName.Instrument, "observingMode", Iso.id)

// Composite lens to find the sequence obs class
val stepClassO: Optional[ExecutionStep, String] =
  stepObserveOptional(SystemName.Observe, "class", Iso.id)

// Lens to find offsets
def offsetO[T, A](implicit
  resolver: OffsetConfigResolver[T, A]
): Optional[ExecutionStep, String] =
  stepObserveOptional(resolver.systemName, resolver.configItem.value, Iso.id)

def offsetF[T, A](implicit
  resolver: OffsetConfigResolver[T, A]
): Fold[ExecutionStep, Option[Offset.Component[A]]] =
  offsetO[T, A].andThen(Getter(signedComponentFormat[A].getOption))

val stringToGuidingP: Prism[String, Guiding] =
  Prism(Guiding.fromString)(_.configValue)

// Lens to find guidingWith configurations
val telescopeGuidingWithT: Traversal[ExecutionStep, Guiding] =
  ExecutionStep.config
    .andThen( // configuration of the step
      systemConfigL(SystemName.Telescope)
    )
    .andThen( // Observe config
      some[Parameters]
    )
    .andThen( // some
      paramValuesWithPrefixT(
        SystemName.Telescope.withParam(ParamName("guideWith"))
      )
    )
    .andThen(ParamValue.value)
    .andThen(          // find the guiding with params
      stringToGuidingP // to guiding
    )
