/*
 * Copyright (C) 2015
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.cleverbus.core.common.asynch.repair;

import static java.lang.Math.min;

import java.util.Date;
import java.util.List;

import org.cleverbus.api.asynch.AsynchConstants;
import org.cleverbus.api.entity.Message;
import org.cleverbus.api.entity.MsgStateEnum;
import org.cleverbus.api.exception.IntegrationException;
import org.cleverbus.api.exception.InternalErrorEnum;
import org.cleverbus.common.log.Log;
import org.cleverbus.core.common.dao.MessageDao;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

/**
 * DB implementation of {@link RepairMessageService} interface.
 *
 * @author <a href="mailto:petr.juza@cleverlance.com">Petr Juza</a>
 */
public class RepairMessageServiceDbImpl implements RepairMessageService {

    private static final int BATCH_SIZE = 10;

    private TransactionTemplate transactionTemplate;

    @Autowired
    private MessageDao messageDao;

    @Autowired
    private ProducerTemplate producerTemplate;

    /**
     * How often to run repair process (in seconds).
     */
    @Value("${asynch.repairRepeatTime}")
    private int repeatInterval;

    /**
     * Count of partly fails before message will be marked as completely FAILED.
     */
    @Value("${asynch.countPartlyFailsBeforeFailed}")
    private int countPartlyFailsBeforeFailed;


    @Override
    public void repairProcessingMessages() {
        // find messages in PROCESSING state
        List<Message> messages = findProcessingMessages();

        Log.debug("Found {} message(s) for repairing ...", messages.size());

        // repair messages in batches
        int batchStartIncl = 0;
        int batchEndExcl;
        while (batchStartIncl < messages.size()) {
            batchEndExcl = min(batchStartIncl + BATCH_SIZE, messages.size());
            updateMessagesInDB(messages.subList(batchStartIncl, batchEndExcl));
            batchStartIncl = batchEndExcl;
        }
    }

    private List<Message> findProcessingMessages() {
        return transactionTemplate.execute(new TransactionCallback<List<Message>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<Message> doInTransaction(TransactionStatus status) {
                return messageDao.findProcessingMessages(repeatInterval);
            }
        });
    }

    /**
     * Updates bulk of messages in DB.
     *
     * @param messages the messages for update
     */
    private void updateMessagesInDB(final List<Message> messages) {
        final Date currDate = new Date();

        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                for (final Message msg : messages) {
                    // checks if failed count exceeds limit for failing
                    if (msg.getFailedCount() >= countPartlyFailsBeforeFailed) {
                        Log.warn("The message " + msg.toHumanString() + " was in PROCESSING state and exceeded "
                                + "max. count of failures. Message is redirected to processing of failed message.");

                        // redirect to "FAILED" route
                        producerTemplate.send(AsynchConstants.URI_ERROR_FATAL, ExchangePattern.InOnly, new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                IntegrationException ex = new IntegrationException(InternalErrorEnum.E116);
                                exchange.setProperty(Exchange.EXCEPTION_CAUGHT, ex);

                                exchange.getIn().setHeader(AsynchConstants.MSG_HEADER, msg);
                            }
                        });

                    } else {
                        msg.setLastUpdateTimestamp(currDate);
                        msg.setState(MsgStateEnum.PARTLY_FAILED);
                        // note: increase count of failures because if message stays in PROCESSING state it's almost sure
                        //  because of any error
                        msg.setFailedCount(msg.getFailedCount() + 1);

                        messageDao.update(msg);

                        Log.warn("The message " + msg.toHumanString() + " was in PROCESSING state "
                                + "and changed to PARTLY_FAILED.", msg.getMsgId(), msg.getCorrelationId());
                    }
                }
            }
        });
    }

    @Required
    public void setTransactionManager(JpaTransactionManager transactionManager) {
        Assert.notNull(transactionManager, "the transactionManager must not be null");

        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }
}
