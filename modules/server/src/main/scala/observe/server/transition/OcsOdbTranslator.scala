package observe.server.transition

import edu.gemini.seqexec.odb.SeqexecSequence
import edu.gemini.shared.util.immutable.MapOp
import edu.gemini.spModel.config2.{ Config, ConfigSequence, ItemEntry, ItemKey }
import edu.gemini.spModel.gemini.calunit.CalUnitConstants._
import edu.gemini.spModel.gemini.calunit.CalUnitParams.Shutter
import edu.gemini.spModel.guide.StandardGuideOptions
import edu.gemini.spModel.seqcomp.SeqConfigNames.{ CALIBRATION_KEY, TELESCOPE_KEY }
import edu.gemini.spModel.target.obsComp.TargetObsCompConstants.GUIDE_WITH_OIWFS_PROP
import lucuma.core.`enum`._
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation
import observe.common.ObsQueriesGQL.ObsQuery.Data.Observation.Config.{
  GmosNorthConfig,
  GmosSouthConfig
}
import observe.common.ObsQueriesGQL.ObsQuery.{
  GmosSite,
  GmosStatic,
  InsConfig,
  SeqStep,
  SeqStepConfig
}
import observe.server.tcs.Tcs
import observe.server.ConfigUtilOps._
import observe.server.transition.GmosTranslator._
import cats.implicits._

import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters._

object OcsOdbTranslator {

  def translate(obs: Observation): SeqexecSequence = {
    val steps = obs.config.toList.flatMap {
      case GmosNorthConfig(_, _, staticN, acquisitionN, scienceN) =>
        (acquisitionN.atoms.flatMap(_.steps) ++ scienceN.atoms.flatMap(_.steps)).map(x =>
          convertGmosStep[GmosSite.North](staticN, x)
        )
      case GmosSouthConfig(_, _, staticS, acquisitionS, scienceS) =>
        (acquisitionS.atoms.flatMap(_.steps) ++ scienceS.atoms.flatMap(_.steps)).map(x =>
          convertGmosStep[GmosSite.South](staticS, x)
        )
    }

    SeqexecSequence(obs.name.map(_.value).getOrElse("Untitled"),
                    Map.empty,
                    new ConfigSequence(steps.toArray),
                    List.empty
    )
  }

  def convertGmosStep[S <: GmosSite: GmosSiteConversions](
    static: GmosStatic[S],
    step:   SeqStep[InsConfig.Gmos[S]]
  ): Config = {
    val stepItems: Map[ItemKey, AnyRef] = (step.stepConfig match {
      case SeqStepConfig.SeqScienceStep(offset) =>
        List[(ItemKey, AnyRef)](
          (TELESCOPE_KEY / Tcs.P_OFFSET_PROP)         -> offset.p,
          (TELESCOPE_KEY / Tcs.Q_OFFSET_PROP)         -> offset.q,
          (TELESCOPE_KEY / Tcs.GUIDE_WITH_PWFS1_PROP) -> StandardGuideOptions.Value.park,
          (TELESCOPE_KEY / Tcs.GUIDE_WITH_PWFS2_PROP) -> StandardGuideOptions.Value.park,
          (TELESCOPE_KEY / GUIDE_WITH_OIWFS_PROP)     -> StandardGuideOptions.Value.park,
          (TELESCOPE_KEY / Tcs.GUIDE_WITH_AOWFS_PROP) -> StandardGuideOptions.Value.park
        )
      case SeqStepConfig.Gcal(shutter)          =>
        List[(ItemKey, AnyRef)](
          (CALIBRATION_KEY / SHUTTER_PROP) -> (
            shutter match {
              case GcalShutter.Open   => Shutter.OPEN
              case GcalShutter.Closed => Shutter.CLOSED
            }
          )
        )
    }).toMap

    val instrumentItems: Map[ItemKey, AnyRef] = GmosTranslator.instrumentParameters(static, step.instrumentConfig)

    new ConfigImpl(stepItems ++ instrumentItems)
  }

  class ConfigImpl(items: Map[ItemKey, AnyRef] = Map.empty) extends Config {
    private val itemMap: mutable.Map[ItemKey, AnyRef] = mutable.Map(items.toSeq: _*)

    override def clear(): Unit = itemMap.clear()

    override def containsItem(key: ItemKey): Boolean = itemMap.contains(key)

    override def itemEntries(): Array[ItemEntry] = itemMap.toArray.map { case (itemKey, value) =>
      new ItemEntry(itemKey, value)
    }

    override def itemEntries(parent: ItemKey): Array[ItemEntry] =
      itemMap.view
        .filterKeys(_.getParent.equals(parent))
        .toArray
        .map { case (itemKey, value) => new ItemEntry(itemKey, value) }

    override def isEmpty: Boolean = itemMap.isEmpty

    override def getKeys: Array[ItemKey] = itemMap.keys.toArray

    override def getKeys(parent: ItemKey): Array[ItemKey] = itemMap.keys.filter {
      _.getParent.equals(parent)
    }.toArray

    override def getItemValue(key: ItemKey): AnyRef = itemMap(key)

    override def getAll(parent: ItemKey): Config = new ConfigImpl(itemMap.view.filterKeys {
      _.getParent.equals(parent)
    }.toMap)

    override def getAll(parents: Array[ItemKey]): Config = new ConfigImpl(itemMap.view.filterKeys {
      k => parents.exists(_.equals(k.getParent))
    }.toMap)

    override def groupBy[K](f: MapOp[ItemEntry, K]): util.Map[K, Array[ItemEntry]] =
      itemMap
        .groupBy { case (k, v) => f(new ItemEntry(k, v)) }
        .view
        .mapValues(_.toArray.map { case (k, v) => new ItemEntry(k, v) })
        .toMap
        .asJava

    override def putItem(key: ItemKey, item: Object): AnyRef = itemMap.addOne((key, item))

    override def putAll(config: Config): Unit = itemMap.addAll(config.itemEntries.toList.map { x =>
      (x.getKey, x.getItemValue)
    })

    override def remove(key: ItemKey): AnyRef = itemMap.subtractOne(key)

    override def removeAll(parent: ItemKey): Unit = itemMap.subtractAll(itemMap.keys.filter {
      _.getParent.equals(parent)
    })

    override def removeAll(parents: Array[ItemKey]): Unit =
      itemMap.subtractAll(itemMap.keys.filter(k => parents.toList.exists(_.equals(k.getParent))))

    override def removeAll(config: Config): Unit =
      itemMap.filterInPlace { case (k, v) =>
        Option(config.getItemValue(k)).forall(!v.equals(_))
      }

    override def retainAll(parent: ItemKey): Unit =
      itemMap.filterInPlace { case (k, _) =>
        k.getParent.equals(parent)
      }

    override def retainAll(parents: Array[ItemKey]): Unit =
      itemMap.filterInPlace { case (k, _) =>
        parents.toList.exists(k.getParent.equals(_))
      }

    override def retainAll(config: Config): Unit =
      itemMap.filterInPlace { case (k, v) =>
        Option(config.getItemValue(k)).exists(v.equals(_))
      }

    override def matches(config: Config): Boolean =
      itemMap.size === config.size &&
        itemMap.forall { case (k, v) => Option(config.getItemValue(k)).exists(v.equals(_)) }

    override def size(): Int = itemMap.size
  }

}
