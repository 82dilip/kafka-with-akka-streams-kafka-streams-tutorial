package com.lightbend.java.modelserver.actor.actors;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.lightbend.java.modelserver.actor.presistence.ExecutionState;
import com.lightbend.java.modelserver.actor.presistence.FilePersistence;
import com.lightbend.java.model.Model;
import com.lightbend.java.model.ModelServingInfo;
import com.lightbend.java.model.ModelWithDescriptor;
import com.lightbend.model.Winerecord;

import java.util.Optional;

public class ModelServingActor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private Optional<Model> currentModel = Optional.empty();
    private Optional<Model> newModel = Optional.empty();
    private Optional<ModelServingInfo> currentServingInfo = Optional.empty();
    private Optional<ModelServingInfo> newServingInfo = Optional.empty();
    private String dataType = null;

    public ModelServingActor(String dataType){
        this.dataType = dataType;
    }

    @Override
    public void preStart() {
        ExecutionState state = FilePersistence.restoreState(dataType);
        newModel = state.getMpdel();
        newServingInfo = state.getServingInfo();
    }

    private void processModel(ModelWithDescriptor modelWithDescriptor) {

        newModel = Optional.of(modelWithDescriptor.getModel());
        newServingInfo = Optional.of(new ModelServingInfo(modelWithDescriptor.getDescriptor().getName(),
                modelWithDescriptor.getDescriptor().getDescription(), System.currentTimeMillis()));
        FilePersistence.saveState(dataType, newModel.get(), newServingInfo.get());
        return;
    }

    public Optional<Double> processData(Winerecord.WineRecord dataRecord) {
        if(newModel.isPresent()){
            // update the model
            if(currentModel.isPresent())
                currentModel.get().cleanup();
            currentModel = newModel;
            currentServingInfo = newServingInfo;
            newModel = Optional.empty();
        }

        // Actually score
        if(currentModel.isPresent()) {
            // Score the model
            long start = System.currentTimeMillis();
            double quality = (double) currentModel.get().score(dataRecord);
            long duration = System.currentTimeMillis() - start;
            currentServingInfo.get().update(duration);
            System.out.println("Calculated quality - " + quality + " in " + duration + "ms");
            return Optional.of(quality);
        }
        else{
            // No model currently
            System.out.println("No model available - skipping");
            return Optional.empty();
        }
    }

    public ModelServingInfo getServingInfo() {
        System.out.println("Using model server " + dataType);
        return currentServingInfo.orElse(new ModelServingInfo());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ModelWithDescriptor.class, modelWithDescriptor -> {
                    processModel(modelWithDescriptor);
                    getSender().tell("Done", getSelf());
                })
                .match(Winerecord.WineRecord.class, dataRecord -> {
                    getSender().tell(processData(dataRecord), getSelf());
                })
                .match(GetState.class, dataType -> {
                    getSender().tell(getServingInfo(), getSelf());
                })
                .build();
    }

    public static Props props(String dataType) {
         return Props.create(ModelServingActor.class, () -> new ModelServingActor(dataType));
    }
}
