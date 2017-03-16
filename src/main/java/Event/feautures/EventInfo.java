package Event.feautures;


import ru.splat.facade.feautures.TransactionRequest;

import java.util.List;

public class EventInfo implements TransactionRequest
{


    private long transactionId;
    private int localTask;
    private List<Integer> services;
    private List<Integer> outcomes;
    private Long time;


    public EventInfo(long transactionId, int localTask, List<Integer> services, List<Integer> outcomes, long time) {
        this.transactionId = transactionId;
        this.localTask = localTask;
        this.services = services;
        this.outcomes = outcomes;
        this.time = time;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EventInfo eventInfo = (EventInfo) o;

        return transactionId == eventInfo.transactionId;

    }

    @Override
    public int hashCode() {
        return (int) (transactionId ^ (transactionId >>> 32));
    }

    @Override
    public String toString()
    {
        return "EventInfo{" +
                "transactionId=" + transactionId +
                ", localTask=" + localTask +
                ", services=" + services +
                ", outcomes=" + outcomes +
                ", time=" + time +
                '}';
    }

    @Override
    public long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(long transactionId) {
        this.transactionId = transactionId;
    }

    @Override
    public int getLocalTask() {
        return localTask;
    }

    public void setLocalTask(int localTask) {
        this.localTask = localTask;
    }

    @Override
    public List<Integer> getServices() {
        return services;
    }

    public void setServices(List<Integer> services) {
        this.services = services;
    }

    public List<Integer> getOutcomes() {
        return outcomes;
    }

    public void setOutcomes(List<Integer> outcomes) {
        this.outcomes = outcomes;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }
}
