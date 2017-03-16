package Event.business;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;


import java.util.*;
import java.util.stream.Collectors;

@ManagedResource(objectName = "Event Limit Winodw:name=Resource")
public class EventBusinessService implements BusinessService<EventInfo>,LimitService<EventInfo>
{

    private long lastDeleteTime = 0;

    private Map<Integer,List<Long>> commitAddDequeMap;

    private Map<Integer, Integer> commitCancelDequeMap;


    @Autowired
    EventRepository eventRepository;

    @ManagedOperation
    public synchronized void setLimit(int id, int lim, int limitTime, String timeType)
    {
        if (timeType.equals("sec")) { limitTime *= 1000;}
        else if (timeType.equals("min")) {limitTime *= 60*1000;}
        else return;
       JmxUtil.set(new Limit(id, lim,limitTime), eventRepository, dequeMap);
    }

    @ManagedOperation
    public String getSelectionFreeLimitFrom(int id)
    {
        return JmxUtil.getLimit(id,eventRepository,dequeMap);
    }

    @Override
    public Set<Integer> convertToSet(List<EventInfo> eventInfoList)
    {
        Set<Integer> buffer = new HashSet<>();

        for (EventInfo eventInfo: eventInfoList)
        {
            buffer.addAll(eventInfo.getOutcomes());
        }

        return buffer;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    private List<TransactionResult> addSelectionLimit(List<EventInfo> eventInfoList)
    {
        LOGGER.info(getClassName() + " Start addSelectionLimit");
        LOGGER.info( getClassName() + " Array for add");
        LOGGER.info(Arrays.toString(eventInfoList.toArray()));

        commitAddDequeMap = new HashMap<>();

        long currentTime = System.currentTimeMillis();

        List<TransactionResult> result = new ArrayList<>(eventInfoList.size());

        addInDeque(eventInfoList, eventRepository);

        for (EventInfo eventInfo: eventInfoList)
        {
            String stringResult = null;
            StringBuilder stringBuilder = new StringBuilder();

            for (Integer integer:eventInfo.getOutcomes())
            {

                Proxy proxy = dequeMap.get(integer);
                if (proxy == null || currentTime - eventInfo.getTime() > proxy.getLimitTime())
                {
                    stringBuilder.append(integer + " ");
                }
                else
                {
                    Deque<Long> deque = proxy.getDeque();
                    while (!deque.isEmpty() && currentTime - deque.getFirst() > proxy.getLimitTime())
                        deque.pollFirst();

                    int commitCount = 0;
                    if (commitAddDequeMap.containsKey(integer))
                    {
                        commitCount = commitAddDequeMap.get(integer).size();
                    }

                    if (deque.size() + commitCount >= proxy.getLimit())
                    {
                        stringBuilder.append(integer + " ");
                    }
                }

            }

            stringResult = stringBuilder.toString();

            if (stringResult.isEmpty())
            {
                for (Integer integer:eventInfo.getOutcomes())
                {
                    if (!commitAddDequeMap.containsKey(integer))
                    {
                        commitAddDequeMap.put(integer, new ArrayList<>());
                    }

                    commitAddDequeMap.get(integer).add(eventInfo.getTime());
                }
            }



            result.add(new TransactionResult(eventInfo.getTransactionId(),
                    Response.ServiceResponse.newBuilder().addAllServices(eventInfo.getServices()).setStringAttachment(stringResult)
                            .setResult(stringResult.isEmpty()?ServiceResult.CONFIRMED.ordinal():ServiceResult.DENIED.ordinal()).build()
            ));
        }

        LOGGER.info(getClassName() + " Stop addSelectionLimit");

        return result;
    }


    @Override
    public List<TransactionResult> processTransactions(List<EventInfo> transactionRequests) throws Exception {

        LOGGER.info(getClassName() + " Start processTransaction");

        lastDeleteTime = scanDeque(lastDeleteTime);

        Map<Integer, TreeSet<EventInfo>> localTaskComplex = new TreeMap<>(Collections.reverseOrder());

        for (EventInfo eventInfo : transactionRequests)
        {
            if (!localTaskComplex.containsKey(eventInfo.getLocalTask()))
            {
                localTaskComplex.put(eventInfo.getLocalTask(), new TreeSet<EventInfo>(new Comparator<EventInfo>() {

                    //TODO Добавить JMX, описание необходимости добавления
                    @Override
                    public int compare(EventInfo o1, EventInfo o2) {
                        return (o1.getTime() >= o2.getTime() ? -1 : 1);
                    }
                }));
            }
            localTaskComplex.get(eventInfo.getLocalTask()).add(eventInfo);
        }

        List<TransactionResult> results = new ArrayList<>();

        for (Map.Entry<Integer, TreeSet<EventInfo>> entry : localTaskComplex.entrySet())
        {
            if (entry.getKey() == TaskTypesEnum.ADD_SELECTION_LIMIT.ordinal())
                results.addAll(addSelectionLimit(entry.getValue().stream().collect(Collectors.toList())));
            else if (entry.getKey() == TaskTypesEnum.CANCEL_SELECTION_LIMIT.ordinal())
                results.addAll(cancelSelectionLimit(entry.getValue().stream().collect(Collectors.toList())));
        }

        LOGGER.info(getClassName() + " Stop processTransaction");
//        throw new Exception();
        return results;
    }

    @Override
    public void commitBusinessService()
    {
        if (commitAddDequeMap != null)
        {
            for (Map.Entry<Integer, List<Long>>  selection : commitAddDequeMap.entrySet())
            {
                Proxy proxy = dequeMap.get(selection.getKey());
                if (dequeMap != null)
                {
                    Deque<Long> deque = proxy.getDeque();
                    if (deque != null)
                    {
                        for (Long time : selection.getValue())
                        {
                            deque.addLast(time);
                            LOGGER.info(getClassName() + " Add in Deque for selection id = " + selection.getKey());
                        }
                    }
                }
            }
            commitAddDequeMap = null;
        }

        if (commitCancelDequeMap != null)
        {
            for (Map.Entry<Integer, Integer>  selection : commitCancelDequeMap.entrySet())
            {
                Proxy proxy = dequeMap.get(selection.getKey());
                if (dequeMap != null)
                {
                    Deque<Long> deque = proxy.getDeque();
                    if (deque != null)
                    {
                        int i = 0;
                        while (!deque.isEmpty() && i <selection.getValue())
                        {
                            i++;
                            deque.pollFirst();
                            LOGGER.info(getClassName() + " Delete from Deque for selection id = " + selection.getKey());
                        }
                    }
                }
            }
            commitCancelDequeMap = null;
        }


    }

    @Override
    public void rollbackBusinessSerivce()
    {
        commitAddDequeMap = null;
        commitCancelDequeMap = null;
        LOGGER.info(getClassName() + " Rollback");
    }

    private List<TransactionResult> cancelSelectionLimit(List<EventInfo> eventInfoList) {

        LOGGER.info(getClassName() + " Start cancelSelectionLimit");
        LOGGER.info(getClassName() + " Array for cancel");
        LOGGER.info(Arrays.toString(eventInfoList.toArray()));

        commitCancelDequeMap = new HashMap<>();

        List<TransactionResult> result = new ArrayList<>();

        long currentTime = System.currentTimeMillis();

        Set<Integer> outcomes = new HashSet<>();
        for (EventInfo eventInfo : eventInfoList) {
            outcomes.addAll(eventInfo.getOutcomes());
            result.add(new TransactionResult(
                    eventInfo.getTransactionId(),
                    Response.ServiceResponse.newBuilder().addAllServices(eventInfo.getServices())
                            .setResult(ServiceResult.CONFIRMED.ordinal()).build()
            ));

            eventInfo.getOutcomes().forEach(p ->
                    {
                        if (dequeMap.containsKey(p)) {
                            Proxy proxy = dequeMap.get(p);
                            Deque<Long> deque = proxy.getDeque();
                            while (!deque.isEmpty() && currentTime - deque.getFirst() > proxy.getLimitTime())
                                deque.pollFirst();

                            if (!deque.isEmpty()) {

                                if (!commitCancelDequeMap.containsKey(p)) {
                                    commitCancelDequeMap.put(p, 0);
                                }

                                Integer integer = commitCancelDequeMap.get(p);
                                commitCancelDequeMap.put(p, integer + 1);
                                // deque.pollFirst();
                            }
                        }
                    }

            );
        }
        LOGGER.info(getClassName() + " Stop cancelSelectionLimit");
        return result;
    }
}
