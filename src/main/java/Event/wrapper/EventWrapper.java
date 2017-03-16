package Event.wrapper;


import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class EventWrapper extends AbstractWrapper<EventRequest.Event,EventInfo>
{

    private Executor thread = Executors.newSingleThreadExecutor();

    @Override
    public void init()
    {
        setConsumerTimeout(100L);
        setKafka(new KafkaImpl<EventRequest.Event>("EventRes", "EventReq", EventRequest.Event.getDefaultInstance()));
        super.setConverter(consumerRecord -> (new EventInfo(
                consumerRecord.key(),
                consumerRecord.value().getLocalTask(),
                consumerRecord.value().getServicesList(),
                consumerRecord.value().getSelectionsList(),
                consumerRecord.value().getTime()
        )));
        thread.execute(this::mainProcess);
    }

}
