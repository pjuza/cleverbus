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

package org.cleverbus.core.common.dao;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;

import org.cleverbus.api.entity.ExternalCall;
import org.cleverbus.api.entity.ExternalCallStateEnum;
import org.cleverbus.api.exception.MultipleDataFoundException;

import org.apache.commons.lang.time.DateUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;


/**
 * DAO for {@link ExternalCall} entity.
 *
 * @author <a href="mailto:petr.juza@cleverlance.com">Petr Juza</a>
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class ExternalCallDaoJpaImpl implements ExternalCallDao {

    public static final int MAX_MESSAGES_IN_ONE_QUERY = 50;

    @PersistenceContext(unitName = DbConst.UNIT_NAME)
    private EntityManager em;

    @Override
    public void insert(ExternalCall externalCall) {
        em.persist(externalCall);
    }

    @Override
    public void update(ExternalCall externalCall) {
        em.merge(externalCall);
    }

    @Override
    @Nullable
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public ExternalCall getExternalCall(String operationName, String entityId) {
        Assert.notNull(operationName, "operationName (uri) must not be null");
        Assert.notNull(entityId, "entityId (operation key) must not be null");

        String jSql = "SELECT c FROM " + ExternalCall.class.getName() + " c " +
                    "WHERE c.operationName = ?1 AND c.entityId = ?2";
        TypedQuery<ExternalCall> q = em.createQuery(jSql, ExternalCall.class);
        q.setParameter(1, operationName);
        q.setParameter(2, entityId);

        List<ExternalCall> results = q.getResultList();
        if (results.isEmpty()) {
            return null;
        }

        if (results.size() > 1) {
            throw new MultipleDataFoundException(String.format(
                    "Multiple ExternalCall instances found for operationName/entityId: %s/%s",
                    operationName, entityId));
        }

        return results.get(0);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockExternalCall(ExternalCall extCall) throws PersistenceException {
        Assert.notNull(extCall, "the extCall must not be null");
        Assert.isTrue(extCall.getState() != ExternalCallStateEnum.PROCESSING,
                "the extCall must not be locked in a processing state");

        Assert.isTrue(em.contains(extCall), "the extCall must be attached");
        // note: https://blogs.oracle.com/carolmcdonald/entry/jpa_2_0_concurrency_and
        em.lock(extCall, LockModeType.OPTIMISTIC);
        extCall.setState(ExternalCallStateEnum.PROCESSING);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public ExternalCall findConfirmation(int interval) {
        // find confirmation that was lastly processed before specified interval
        Date lastUpdateLimit = DateUtils.addSeconds(new Date(), -interval);

        String jSql = "SELECT c "
                + "FROM " + ExternalCall.class.getName() + " c "
                + "WHERE c.operationName = :operationName"
                + "     AND c.state = :state"
                + "     AND c.lastUpdateTimestamp < :lastUpdateTimestamp"
                + " ORDER BY c.creationTimestamp";

        TypedQuery<ExternalCall> q = em.createQuery(jSql, ExternalCall.class);
        q.setParameter("operationName", ExternalCall.CONFIRM_OPERATION);
        q.setParameter("state", ExternalCallStateEnum.FAILED);
        q.setParameter("lastUpdateTimestamp", new Timestamp(lastUpdateLimit.getTime()));
        q.setMaxResults(1);
        List<ExternalCall> extCalls = q.getResultList();

        if (extCalls.isEmpty()) {
            return null;
        } else {
            return extCalls.get(0);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExternalCall lockConfirmation(final ExternalCall extCall) {
        Assert.notNull(extCall, "the extCall must not be null");
        Assert.isTrue(extCall.getState() != ExternalCallStateEnum.PROCESSING,
                "the extCall must not be locked in a processing state");
        Assert.isTrue(em.contains(extCall), "the extCall must be attached");

        em.lock(extCall, LockModeType.PESSIMISTIC_WRITE);
        extCall.setState(ExternalCallStateEnum.PROCESSING);
        return extCall;
    }

    @Override
    public List<ExternalCall> findProcessingExternalCalls(int interval) {
        final Date startProcessLimit = DateUtils.addSeconds(new Date(), -interval);

        String jSql = "SELECT c "
                + "FROM " + ExternalCall.class.getName() + " c "
                + "WHERE c.state = '" + ExternalCallStateEnum.PROCESSING + "'"
                + "     AND c.lastUpdateTimestamp < :time";

        TypedQuery<ExternalCall> q = em.createQuery(jSql, ExternalCall.class);
        q.setParameter("time", new Timestamp(startProcessLimit.getTime()));
        q.setMaxResults(MAX_MESSAGES_IN_ONE_QUERY);
        return q.getResultList();
    }
}
