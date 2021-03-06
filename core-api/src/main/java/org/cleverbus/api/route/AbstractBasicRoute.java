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

package org.cleverbus.api.route;

import java.util.Set;

import javax.annotation.Nullable;
import javax.xml.namespace.QName;

import org.cleverbus.api.asynch.AsynchConstants;
import org.cleverbus.api.entity.ExternalSystemExtEnum;
import org.cleverbus.api.entity.ServiceExtEnum;
import org.cleverbus.api.exception.BusinessException;
import org.cleverbus.api.exception.LockFailureException;
import org.cleverbus.api.exception.MultipleDataFoundException;
import org.cleverbus.api.exception.NoDataFoundException;
import org.cleverbus.api.exception.ValidationIntegrationException;
import org.cleverbus.common.log.Log;

import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.Header;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.apache.camel.processor.DefaultExchangeFormatter;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.util.MessageHelper;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;


/**
 * Parent route definition that defines common route rules, e.g. exception policy, error handling etc.
 *
 * @author <a href="mailto:petr.juza@cleverlance.com">Petr Juza</a>
 */
public abstract class AbstractBasicRoute extends SpringRouteBuilder {

    /**
     * Suffix for synchronous routes.
     */
    public static final String ROUTE_SUFFIX = "_route";

    /**
     * Suffix for asynchronous incoming routes.
     */
    public static final String IN_ROUTE_SUFFIX = "_in_route";

    /**
     * Suffix for asynchronous outbound routes.
     */
    public static final String OUT_ROUTE_SUFFIX = "_out_route";

    /**
     * Suffix for outbound routes with external systems.
     */
    public static final String EXTERNAL_ROUTE_SUFFIX = "_external_route";

    // note: I prefer using this before calling repeatedly lookup method for getting bean implementation
    @Autowired(required = false)
    private WebServiceUriBuilder wsUriBuilder;

    private DefaultExchangeFormatter historyFormatter;

    protected AbstractBasicRoute() {
        // setup exchange formatter to be used for message history dump
        historyFormatter = new DefaultExchangeFormatter();
        historyFormatter.setShowExchangeId(true);
        historyFormatter.setMultiline(true);
        historyFormatter.setShowHeaders(true);
        historyFormatter.setStyle(DefaultExchangeFormatter.OutputStyle.Fixed);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final void configure() throws Exception {
        doErrorHandling();

        doConfigure();
    }

    /**
     * Defines global (better to say semi-global because it's scoped for one route builder) exception policy
     * and common error handling.
     * <p/>
     * Default implementation catches common {@link Exception} and if it's synchronous message
     * (see {@link AsynchConstants#ASYNCH_MSG_HEADER}) then redirect to {@link AsynchConstants#URI_EX_TRANSLATION}.
     * If it's asynchronous message then determines according to exception's type if redirect to
     * {@link AsynchConstants#URI_ERROR_FATAL} or {@link AsynchConstants#URI_ERROR_HANDLING} route URI.
     *
     * @throws Exception can be thrown during configuration
     */
    @SuppressWarnings("unchecked")
    protected void doErrorHandling() throws Exception {
        // note: IO exceptions must be handled where is call to external system

        onException(Exception.class)
            .handled(true)
            .log(LoggingLevel.ERROR,
                    "exception caught (asynch = '${header." + AsynchConstants.ASYNCH_MSG_HEADER + "}') "
                            + "- ${property." + Exchange.EXCEPTION_CAUGHT + ".message}")

            .bean(this, "printMessageHistory")

            .choice()
                .when().method(this, "isAsynch")
                    // process exception and redirect message to next route
                    .routingSlip(method(this, "exceptionHandling"))
                .end()

                .otherwise()
                    // synchronous
                    .to(AsynchConstants.URI_EX_TRANSLATION)
            .end();
    }

    @Handler
    public void printMessageHistory(Exchange exchange) {
        // print message history
        String routeStackTrace = MessageHelper.dumpMessageHistoryStacktrace(exchange, historyFormatter, false);
        Log.debug(routeStackTrace);
    }

    @Handler
    public boolean isAsynch(@Header(AsynchConstants.ASYNCH_MSG_HEADER) Boolean asynch) {
        return BooleanUtils.isTrue(asynch);
    }

    /**
     * Handles specified exception.
     *
     * @param ex the thrown exception
     * @param asynch {@code true} if it's asynchronous message processing otherwise synchronous processing
     * @return next route URI
     */
    @Handler
    public String exceptionHandling(Exception ex, @Header(AsynchConstants.ASYNCH_MSG_HEADER) Boolean asynch) {
        Assert.notNull(ex, "the ex must not be null");
        Assert.isTrue(BooleanUtils.isTrue(asynch), "it must be asynchronous message");

        String nextUri;

        if (ExceptionUtils.indexOfThrowable(ex, ValidationException.class) >= 0
                || ExceptionUtils.indexOfThrowable(ex, ValidationIntegrationException.class) >= 0) {
            Log.warn("Validation error, no further processing - " + ex.getMessage());
            nextUri = AsynchConstants.URI_ERROR_FATAL;

        } else if (ExceptionUtils.indexOfThrowable(ex, BusinessException.class) >= 0) {
            Log.warn("Business exception, no further processing.");
            nextUri = AsynchConstants.URI_ERROR_FATAL;

        } else if (ExceptionUtils.indexOfThrowable(ex, NoDataFoundException.class) >= 0) {
            Log.warn("No data found, no further processing.");
            nextUri = AsynchConstants.URI_ERROR_FATAL;

        } else if (ExceptionUtils.indexOfThrowable(ex, MultipleDataFoundException.class) >= 0) {
            Log.warn("Multiple data found, no further processing.");
            nextUri = AsynchConstants.URI_ERROR_FATAL;

        } else if (ExceptionUtils.indexOfThrowable(ex, LockFailureException.class) >= 0) {
            Log.warn("Locking exception.");
            nextUri = AsynchConstants.URI_ERROR_HANDLING;

        } else {
            Log.error("Unspecified exception - " + ex.getClass().getSimpleName() + " (" + ex.getMessage() + ")");
            nextUri = AsynchConstants.URI_ERROR_HANDLING;
        }

        return nextUri;
    }

    /**
     * Defines routes and route specific configuration.
     *
     * @throws Exception can be thrown during configuration
     */
    protected abstract void doConfigure() throws Exception;

    /**
     * Constructs a "to" URI for sending WS messages to external systems,
     * i.e., Camel Web Service Endpoint URI for contacting an external system via <strong>SOAP 1.1</strong>.
     *
     * @param connectionUri the URI to connect to the external system, e.g.: http://localhost:8080/vfmock/ws/mm7
     * @param messageSenderRef the message sender ref (bean id/name in Spring context)
     * @param soapAction the SOAP action to be invoked,
     *                   can be {@code null} for implicit handling of SOAP messages by the external system
     * @return the Camel Endpoint URI for producing (sending via To) SOAP messages to external system
     */
    protected String getOutWsUri(String connectionUri, String messageSenderRef, String soapAction) {
        return wsUriBuilder.getOutWsUri(connectionUri, messageSenderRef, soapAction);
    }

    /**
     * Shorthand for {@link #getOutWsUri(String, String, String)}.
     */
    protected String getOutWsUri(String connectionUri, String messageSenderRef) {
        return getOutWsUri(connectionUri, messageSenderRef, null);
    }

    /**
     * Shorthand for {@link #getOutWsSoap12Uri(String, String, String)}.
     */
    protected String getOutWsSoap12Uri(String connectionUri, String messageSenderRef) {
        return wsUriBuilder.getOutWsSoap12Uri(connectionUri, messageSenderRef, null);
    }

    /**
     * Constructs a "to" URI for sending WS messages to external systems,
     * i.e., Camel Web Service Endpoint URI for contacting an external system via <strong>SOAP 1.2</strong>.
     *
     * @param connectionUri the URI to connect to the external system, e.g.: http://localhost:8080/vfmock/ws/mm7
     * @param messageSenderRef the message sender ref (bean id/name in Spring context)
     * @param soapAction the SOAP action to be invoked,
     *                   can be {@code null} for implicit handling of SOAP messages by the external system
     * @return the Camel Endpoint URI for producing (sending via To) SOAP messages to external system
     */
    protected String getOutWsSoap12Uri(String connectionUri, String messageSenderRef, String soapAction) {
        return wsUriBuilder.getOutWsSoap12Uri(connectionUri, messageSenderRef, soapAction);
    }

    /**
     * Gets "from" URI for handling incoming WS messages with default "endpointMapping" bean.
     *
     * @return from URI
     * @param qName the operation QName (namespace + local part)
     */
    protected String getInWsUri(QName qName) {
        return wsUriBuilder.getInWsUri(qName, "endpointMapping", null);
    }

    /**
     * Gets "from" URI for handling incoming WS messages with default "endpointMapping" bean.
     *
     * @return from URI
     * @param qName the operation QName (namespace + local part)
     * @param params the endpoint URI parameters (without leading signs ? or &)
     */
    protected String getInWsUri(QName qName, @Nullable String params) {
        return wsUriBuilder.getInWsUri(qName, "endpointMapping", params);
    }

    /**
     * Gets route ID for synchronous routes.
     *
     * @param service       the service name
     * @param operationName the operation name
     * @return route ID
     * @see #getInRouteId(ServiceExtEnum, String)
     * @see #getOutRouteId(ServiceExtEnum, String)
     * @see #getExternalRouteId(ExternalSystemExtEnum, String)
     */
    public static String getRouteId(ServiceExtEnum service, String operationName) {
        Assert.notNull(service, "the service must not be null");
        Assert.hasText(operationName, "the operationName must not be empty");

        return service.getServiceName() + "_" + operationName + ROUTE_SUFFIX;
    }

    /**
     * Gets route ID for asynchronous incoming routes.
     *
     * @param service       the service name
     * @param operationName the operation name
     * @return route ID
     * @see #getOutRouteId(ServiceExtEnum, String)
     */
    public static String getInRouteId(ServiceExtEnum service, String operationName) {
        Assert.notNull(service, "the service must not be null");
        Assert.hasText(operationName, "the operationName must not be empty");

        return service.getServiceName() + "_" + operationName + IN_ROUTE_SUFFIX;
    }

    /**
     * Gets route ID for asynchronous outbound routes.
     *
     * @param service       the service name
     * @param operationName the operation name
     * @return route ID
     * @see #getInRouteId(ServiceExtEnum, String)
     */
    public static String getOutRouteId(ServiceExtEnum service, String operationName) {
        Assert.notNull(service, "the service must not be null");
        Assert.hasText(operationName, "the operationName must not be empty");

        return service.getServiceName() + "_" + operationName + OUT_ROUTE_SUFFIX;
    }

    /**
     * Gets route ID for routes which communicates with external systems.
     *
     * @param system        the external system
     * @param operationName the operation name
     * @return route ID
     * @see #getRouteId(ServiceExtEnum, String)
     */
    public static String getExternalRouteId(ExternalSystemExtEnum system, String operationName) {
        Assert.notNull(system, "the system must not be null");
        Assert.hasText(operationName, "the operationName must not be empty");

        return system.getSystemName() + "_" + operationName + EXTERNAL_ROUTE_SUFFIX;
    }

    /**
     * Adds new event notifier.
     * <p/>
     * Use manual adding via this method or use {@link EventNotifier} annotation
     * for automatic registration. Don't use both.
     *
     * @param eventNotifier the event notifier
     */
    protected final void addEventNotifier(EventNotifier eventNotifier) {
        Assert.notNull(eventNotifier, "the eventNotifier must not be null");

        getContext().getManagementStrategy().addEventNotifier(eventNotifier);
    }

    /**
     * Returns bean by its type from registry.
     *
     * @param type the type of the registered bean
     * @return bean of specified type
     */
    protected final <T> T getBean(Class<T> type) {
        Set<T> beans = getContext().getRegistry().findByType(type);

        Assert.state(beans.size() == 1, "there is more beans of type " + type);

        return beans.iterator().next();
    }

    /**
     * Returns class name of the route implementation class.
     * <p/>
     * This is because of using {@code bean(this, "createResponseForGetCounterData")} - if there is no toString()
     * method then {@link StackOverflowError} is thrown.
     *
     * @return class name
     */
    @Override
    public String toString() {
        return getClass().getName();
    }
}
