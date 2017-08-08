package edu.gemini.seqexec.server

import java.util.logging.Logger

import edu.gemini.seqexec.model.dhs.ImageFileId

import scalaz.EitherT
import scalaz.concurrent.Task

object GmosSouthControllerSim extends GmosSouthController {
  private val Log = Logger.getLogger(getClass.getName)

  override def getConfig: SeqAction[GmosConfig] = ???

  override def observe(obsid: ImageFileId): SeqAction[ImageFileId] = EitherT( Task {
    Log.info("Taking Gmos South observation with label " + obsid)
    Thread.sleep(5000)
    Log.info("Gmos South observation completed")
    TrySeq(obsid)
  } )

  override def applyConfig(config: GmosConfig): SeqAction[Unit] = EitherT( Task {
    Log.info("Applying Gmos South configuration " + config)
    TrySeq(())
  } )
}

