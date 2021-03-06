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

package org.cleverbus.api.entity;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;

import org.cleverbus.api.common.HumanReadable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.util.Assert;


/**
 * Evidence of calls to external systems (billing, VF, ...).
 * This table serves for checking of duplication calls to external systems and checks obsolete calls.
 * <p/>
 * Entity ID contains real entity ID for operations which change existing data
 * or correlationID for operations which creates new data.
 * <p/>
 * Special case are confirmations which have operation with the name "{@value #CONFIRM_OPERATION}"
 * and entity ID will be set to {@link Message#getMsgId() message ID}.
 * There are only confirmations which failed previously.
 *
 * @author <a href="mailto:petr.juza@cleverlance.com">Petr Juza</a>
 */
@Entity
@Table(name = "external_call",
        uniqueConstraints = @UniqueConstraint(name = "uq_operation_entity_id",
                columnNames = {"operation_name", "entity_id"}))
public class ExternalCall implements HumanReadable {

    public static final String CONFIRM_OPERATION = "confirmation";

    @Id
    @Column(name = "call_id")
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "msg_id", nullable = false)
    private Message message;

    @Column(name = "msg_id", nullable = false, insertable = false, updatable = false)
    private Long msgId;

    @Column(name = "operation_name", length = 100, nullable = false)
    private String operationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 20, nullable = false)
    private ExternalCallStateEnum state;

    @Column(name = "entity_id", length = 150, nullable = false)
    private String entityId;

    @Column(name = "msg_timestamp", nullable = false)
    private Date msgTimestamp;

    @Column(name = "creation_timestamp", nullable = false)
    private Date creationTimestamp;

    @Version
    @Column(name = "last_update_timestamp", nullable = false)
    private Date lastUpdateTimestamp;

    @Column(name = "failed_count", nullable = false)
    private int failedCount = 0;

    /** Default public constructor. */
    public ExternalCall() {
    }

    /**
     * Creates new {@link ExternalCallStateEnum#FAILED failed} confirmation call.
     *
     * @param msg the message
     * @return external call entity
     */
    public static ExternalCall createFailedConfirmation(Message msg) {
        Assert.notNull(msg, "the msg must not be null");

        Date currDate = new Date();

        ExternalCall extCall = new ExternalCall();
        extCall.setMessage(msg);
        extCall.setOperationName(CONFIRM_OPERATION);
        extCall.setState(ExternalCallStateEnum.FAILED);
        extCall.setEntityId(msg.getCorrelationId());
        extCall.setCreationTimestamp(currDate);
        extCall.setLastUpdateTimestamp(currDate);
        extCall.setMsgTimestamp(msg.getMsgTimestamp());
        extCall.setFailedCount(1);

        return extCall;
    }

    /**
     * Creates a new external call with {@link ExternalCallStateEnum#PROCESSING processing} state.
     *
     * @param msg the message
     * @return external call entity
     */
    public static ExternalCall createProcessingCall(String operationName, String entityId, Message msg) {
        Assert.notNull(operationName, "operationName (uri) must not be null");
        Assert.notNull(entityId, "entityId (operation key) must not be null");
        Assert.notNull(msg, "msg must not be null");

        Date currDate = new Date();

        ExternalCall extCall = new ExternalCall();
        extCall.setCreationTimestamp(currDate);
        extCall.setFailedCount(0);

        extCall.setMessage(msg);
        extCall.setMsgId(msg.getMsgId());
        extCall.setMsgTimestamp(msg.getMsgTimestamp());

        extCall.setOperationName(operationName);
        extCall.setEntityId(entityId);

        extCall.setState(ExternalCallStateEnum.PROCESSING);
        extCall.setLastUpdateTimestamp(currDate);

        return extCall;
    }

    /**
     * Gets unique external call ID.
     *
     * @return unique ID
     */
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gets parent message ID.
     *
     * @return message ID
     */
    public Long getMsgId() {
        return msgId;
    }

    public void setMsgId(Long msgId) {
        this.msgId = msgId;
    }

    /**
     * Gets main asynch. message.
     *
     * @return message
     */
    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    /**
     * Gets target operation identification (e.g. target system name + operation name).
     *
     * @return operation name
     */
    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    /**
     * Gets state of processing.
     *
     * @return state
     */
    public ExternalCallStateEnum getState() {
        return state;
    }

    public void setState(ExternalCallStateEnum state) {
        this.state = state;
    }

    /**
     * Gets entity ID.
     *
     * @return entity ID
     */
    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    /**
     * Gets creation date of this entity.
     *
     * @return creation date
     */
    public Date getCreationTimestamp() {
        return creationTimestamp;
    }

    public void setCreationTimestamp(Date creationTimestamp) {
        this.creationTimestamp = creationTimestamp;
    }

    /**
     * Gets timestamp when the entity was changed last time.
     *
     * @return timestamp
     */
    public Date getLastUpdateTimestamp() {
        return lastUpdateTimestamp;
    }

    public void setLastUpdateTimestamp(Date lastUpdateTimestamp) {
        this.lastUpdateTimestamp = lastUpdateTimestamp;
    }

    /**
     * Gets message timestamp from source system.
     *
     * @return message timestamp
     * @see Message#getMsgTimestamp()
     */
    public Date getMsgTimestamp() {
        return msgTimestamp;
    }

    public void setMsgTimestamp(Date msgTimestamp) {
        this.msgTimestamp = msgTimestamp;
    }

    /**
     * Gets count of failed tries.
     * This value has sense only when {@link #isConfirmationCall()} is {@code true}.
     *
     * @return failedCount of failed tries
     */
    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    /**
     * Gets {@code true} when external call represents confirmation call.
     *
     * @return {@code true} for confirmation call, otherwise {@code false}
     */
    public boolean isConfirmationCall() {
        return operationName.equals(CONFIRM_OPERATION);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj instanceof ExternalCall) {
            ExternalCall en = (ExternalCall) obj;

            return new EqualsBuilder()
                    .append(id, en.id)
                    .isEquals();
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(id)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("msgId", msgId)
                .append("operationName", operationName)
                .append("state", state)
                .append("entityId", entityId)
                .append("msgTimestamp", msgTimestamp)
                .append("creationTimestamp", creationTimestamp)
                .append("lastUpdateTimestamp", lastUpdateTimestamp)
                .append("failedCount", failedCount)
                .toString();
    }

    @Override
    public String toHumanString() {
        return "(id = " + id + ", operationName = " + operationName + ", entityId = " + entityId + ")";
    }
}
