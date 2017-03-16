package facade.service;

import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.transaction.annotation.Transactional;


import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

public class ServiceFacade<KafkaRecord extends Message, InternalTrType extends TransactionRequest>
{
    private Logger LOGGER;

    private ExactlyOnceRepositoryInterface<TransactionResult> exactlyOnceRepository;

    private BusinessService<InternalTrType> businessService;

    @Transactional
    public List<TransactionResult> customProcessMessage(Set<InternalTrType> transactionRequests) throws Exception
    {

        if (transactionRequests == null || transactionRequests.isEmpty()) return null;

        LOGGER.info(businessService.getClass().getName() + " Batch after set-filter");
        LOGGER.info(businessService.getClass().getName() + " " + Arrays.toString(transactionRequests.toArray()));

        LOGGER.info(businessService.getClass().getName() + " Batch from Idemp");
        List<TransactionResult> readyTransactions = exactlyOnceRepository
                .filterByTable(transactionRequests.stream()
                        .map(TransactionRequest::getTransactionId)
                        .collect(Collectors.toList())
                );
        LOGGER.info(businessService.getClass().getName() + " " + Arrays.toString(readyTransactions.toArray()));

        Set<Long> readyTransactionIds = readyTransactions.stream()
                .map(TransactionResult::getTransactionId)
                .collect(Collectors.toSet());

        List<InternalTrType> transactionsToStart = transactionRequests.stream()
                .filter(tr -> !readyTransactionIds.contains(tr.getTransactionId()))
                .collect(Collectors.toList());

        LOGGER.info(businessService.getClass().getName() + " Batch for Business Service");
        LOGGER.info(businessService.getClass().getName() + " " + Arrays.toString(transactionsToStart.toArray()));
        // Бизнес-логика плагина
        List<TransactionResult> transactionResults = businessService.processTransactions(transactionsToStart);

        // добрасываем идемпотентность

        LOGGER.info("Start Write Idemp");
        LOGGER.info("Size = " + transactionResults.size());
        exactlyOnceRepository.insertFilterTable(transactionResults);
        LOGGER.info("Stop Write Idemp");
        readyTransactions.addAll(transactionResults);
//        if (readyTransactions.size() == 1) throw new RuntimeException();

        LOGGER.info(businessService.getClass().getName() + " Completed transaction");
        LOGGER.info(businessService.getClass().getName() + " " + Arrays.toString(readyTransactions.toArray()));
        return readyTransactions;
    }

    public void commitService()
    {
        businessService.commitBusinessService();
    }

    public void rollbackService()
    {
        businessService.rollbackBusinessSerivce();
    }


    @Required
    public void setExactlyOnceRepository(ExactlyOnceRepositoryInterface exactlyOnceRepository)
    {
        this.exactlyOnceRepository = exactlyOnceRepository;
    }


    @Required
    public void setBusinessService(BusinessService<InternalTrType> businessService)
    {
        this.businessService = businessService;
        LOGGER = getLogger(ServiceFacade.class);
    }
}
