package com.lightbend.scala.standard.modelserver.scala

import java.util.Objects

import com.lightbend.java.configuration.kafka.ApplicationKafkaParameters
import com.lightbend.scala.modelServer.model.{ModelToServeStats, ModelWithDescriptor}
import com.lightbend.scala.standard.modelserver.scala.store.StoreState
import org.apache.kafka.streams.processor.{AbstractProcessor, ProcessorContext, ProcessorSupplier}
import org.apache.kafka.streams.state.KeyValueStore

import scala.util.Try

class ModelProcessor extends AbstractProcessor[Array[Byte], Try[ModelWithDescriptor]] with ProcessorSupplier[Array[Byte], Try[ModelWithDescriptor]]{

  private var modelStore = null.asInstanceOf[KeyValueStore[Integer, StoreState]]

  import ApplicationKafkaParameters._
  override def process (key: Array[Byte], modelWithDescriptor: Try[ModelWithDescriptor]): Unit = {

    var state = modelStore.get(STORE_ID)
    if (state == null) state = new StoreState

    state.newModel = Some(modelWithDescriptor.get.model)
    state.newState = Some(new ModelToServeStats(modelWithDescriptor.get.descriptor))
    modelStore.put(ApplicationKafkaParameters.STORE_ID, state)
  }

  override def init(context: ProcessorContext): Unit = {
    modelStore = context.getStateStore(STORE_NAME).asInstanceOf[KeyValueStore[Integer, StoreState]]
    Objects.requireNonNull(modelStore, "State store can't be null")
  }

  override def get() = new ModelProcessor()
}
