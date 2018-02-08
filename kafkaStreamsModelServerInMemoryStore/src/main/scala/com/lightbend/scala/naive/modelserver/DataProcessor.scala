package com.lightbend.scala.naive.modelserver

import com.lightbend.scala.modelServer.model.{DataRecord, ServingResult}
import com.lightbend.scala.naive.modelserver.store.StoreState
import org.apache.kafka.streams.processor.{AbstractProcessor, ProcessorContext}

import scala.util.Success

class DataProcessor extends AbstractProcessor[Array[Byte], Array[Byte]]{

  private var modelStore = null.asInstanceOf[StoreState]
  private var ctx = null.asInstanceOf[ProcessorContext]

  override def process(key: Array[Byte], value: Array[Byte]): Unit = {
    DataRecord.fromByteArray(value) match {
      case Success(dataRecord) => {
        modelStore.newModel match {
          case Some(model) => {
            // close current model first
            modelStore.currentModel match {
              case Some(m) => m.cleanup()
              case _ =>
            }
            // Update model
            modelStore.currentModel = Some(model)
            modelStore.currentState = modelStore.newState
            modelStore.newModel = None
          }
          case _ =>
        }
        modelStore.currentModel match {
          case Some(model) => {
            val start = System.currentTimeMillis()
            val quality = model.score(dataRecord.asInstanceOf[AnyVal]).asInstanceOf[Double]
            val duration = System.currentTimeMillis() - start
//            println(s"Calculated quality - $quality calculated in $duration ms")
            modelStore.currentState.get.incrementUsage(duration)
            ctx.forward(key, ServingResult(true, quality, duration))
          }
          case _ => {
//            println("No model available - skipping")
            ctx.forward(key, ServingResult(false))
          }
        }
        ctx.commit()
      }
      case _ => // ignore
    }
  }

  override def init(context: ProcessorContext): Unit = {
    modelStore = StoreState()
    ctx = context
  }
}
