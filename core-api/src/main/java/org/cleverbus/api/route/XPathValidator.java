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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.cleverbus.api.exception.InternalErrorEnum;
import org.cleverbus.api.exception.ValidationIntegrationException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.xml.InvalidXPathExpression;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.Assert;
import org.w3c.dom.NodeList;


/**
 * XPath validator that checks existence of mandatory element values (only those which have text values, not other sub-elements).
 * If there is no specified element (also with any value) then {@link ValidationIntegrationException} is thrown.
 * <p/>
 * Input XML:
 * <pre>
     &lt;cus:setCustomerRequest>
        &lt;cus:customer>
           &lt;cus1:externalCustomerId>700034&lt;/cus1:externalCustomerId>
           &lt;cus1:customerNo>2&lt;/cus1:customerNo>
        &lt;/cus:customer>
     &lt;/cus:setCustomerRequest>      
 * </pre>
 * ParentElm: /cus:setCustomerRequest/cus:customer <br/>
 * elements: [cus1:externalCustomerId, cus1:customerNo]
 *
 * @author <a href="mailto:petr.juza@cleverlance.com">Petr Juza</a>
 */
public class XPathValidator implements Processor {

    private Namespaces ns;

    private String parentElm;

    private String[] elements;

    /**
     * Creates new XPath validator.
     *
     * @param parentElm the parent element (if any)
     * @param ns the namespace(s)
     * @param elements the element names;
     */
    public XPathValidator(@Nullable String parentElm, Namespaces ns, String... elements) {
        Assert.notNull(ns, "the ns must not be null");
        Assert.notEmpty(elements, "the elements must not be empty");

        this.ns = ns;
        this.parentElm = parentElm;
        this.elements = elements;
    }

    /**
     * Creates new XPath validator with empty parent element.
     *
     * @param ns the namespace(s)
     * @param elements the elements
     */
    public XPathValidator(Namespaces ns, String... elements) {
        Assert.notNull(ns, "the ns must not be null");
        Assert.notEmpty(elements, "the elements must not be empty");

        this.ns = ns;
        this.elements = elements;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        List<String> wrongElms = new ArrayList<String>();

        for (String elm : elements) {
            String absElm = StringUtils.trimToEmpty(parentElm);
            if (!absElm.startsWith("/") && StringUtils.isNotEmpty(absElm)) {
                absElm = "/" + absElm;
            }

            absElm += "/" + elm;

            String xpathExp = absElm + "/text()";

            // example: /cusSer:setCustomerRequest/cusSer:customer/cus:customerNo/text()
            try {
                NodeList nodeList = ((NodeList)ns.xpath(xpathExp).evaluate(exchange));

                if (nodeList.getLength() == 0) {
                    // no mandatory element => error
                    wrongElms.add(absElm);
                }
            } catch (InvalidXPathExpression ex) {
                wrongElms.add(absElm);
            }
        }

        // throw exception if wrong
        if (!wrongElms.isEmpty()) {
            String msg = "The following mandatory elements aren't available or don't have any value: \n"
                    + StringUtils.join(wrongElms, ", ");

            throwException(msg);
        }
    }

    /**
     * Throws exception when validation failed.
     * Overrides this method when you need to throw different exception.
     *
     * @param msg the exception message
     */
    protected void throwException(String msg) {
        throw new ValidationIntegrationException(InternalErrorEnum.E110, msg);
    }
}
