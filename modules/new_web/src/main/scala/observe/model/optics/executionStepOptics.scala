// Copyright (c) 2016-2022 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package observe.model.optics

import monocle.Optional
import observe.model.ExecutionStep
import observe.model.enums.SystemName
import monocle.Prism
import lucuma.core.model.sequence.StepConfig
import monocle.Lens
import observe.model.Parameters
import observe.model.ExecutionStepConfig
import observe.model.ParamName
import observe.model.ParamValue
import monocle.Iso
import monocle.function.At.atMap
import monocle.std.option.some
import monocle.std.string.*
import observe.model.enums.ExecutionStepType
import observe.model.OffsetConfigResolver
import lucuma.core.math.Offset
import monocle.Fold
import monocle.Getter
import lucuma.core.optics.Format
import lucuma.core.math.Angle
import lucuma.core.syntax.all.*
import monocle.Traversal
import observe.model.enums.Guiding
import monocle.function.FilterIndex

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
  param:  String
): Optional[ExecutionStepConfig, ParamValue] =
  systemConfigL(system)
    .andThen(          // observe parameters
      some[Parameters]
    )
    .andThen(          // focus on the option
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

def stepObserveOptional[A](
  systemName: SystemName,
  param:      String,
  prism:      Prism[ParamValue, A]
): Optional[ExecutionStep, A] =
  ExecutionStep.config
    .andThen(       // configuration of the step
      configParamValueO(systemName, param)
    )
    .andThen(prism) // step type

val stepTypeO: Optional[ExecutionStep, ExecutionStepType] =
  stepObserveOptional(
    SystemName.Observe,
    "observeType",
    ParamValue.value.andThen(stringToStepTypeP)
  )

// Composite lens to find if the step is N&S
val isNodAndShuffleO: Optional[ExecutionStep, Boolean] =
  stepObserveOptional(SystemName.Instrument, "useNS", ParamValue.value.andThen(stringToBoolean))

// Composite lens to find the sequence obs class
val stepClassO: Optional[ExecutionStep, ParamValue] =
  stepObserveOptional(SystemName.Observe, "class", Iso.id)

// Lens to find offsets
def offsetO[T, A](implicit
  resolver: OffsetConfigResolver[T, A]
): Optional[ExecutionStep, ParamValue] =
  stepObserveOptional(resolver.systemName, resolver.configItem, Iso.id)

def offsetF[T, A](implicit
  resolver: OffsetConfigResolver[T, A]
): Fold[ExecutionStep, Option[Offset.Component[A]]] =
  offsetO[T, A].andThen(ParamValue.value).andThen(Getter(signedComponentFormat[A].getOption))

val stringToGuidingP: Prism[String, Guiding] =
  Prism(Guiding.fromString)(_.configValue)

// Lens to find guidingWith configurations
val telescopeGuidingWithT: Traversal[ExecutionStep, Guiding] =
  ExecutionStep.config
    .andThen(          // configuration of the step
      systemConfigL(SystemName.Telescope)
    )
    .andThen(          // Observe config
      some[Parameters]
    )
    .andThen(          // some
      paramValuesWithPrefixT(
        SystemName.Telescope.withParam("guideWith")
      )
    )
    .andThen(ParamValue.value)
    .andThen(          // find the guiding with params
      stringToGuidingP // to guiding
    )
