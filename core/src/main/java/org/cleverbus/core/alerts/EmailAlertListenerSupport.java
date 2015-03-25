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

package org.cleverbus.core.alerts;

import org.cleverbus.api.common.EmailService;
import org.cleverbus.common.log.Log;
import org.cleverbus.spi.alerts.AlertInfo;
import org.cleverbus.spi.alerts.AlertListener;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;


/**
 * Listener implementation that sends email notifications to admin mails.
 * <p/>
 * If alert is activated then email is sent - there are default {@link #DEFAULT_ALERT_SUBJECT subject}
 * and {@link #DEFAULT_ALERT_BODY body} but if there are defined subject or body for specified alert
 * then these values are used. Also formats for {@link #formatSubject(AlertInfo) subject}
 * and {@link #formatBody(AlertInfo, long) body} can be changed.
 *
 * @author <a href="mailto:petr.juza@cleverlance.com">Petr Juza</a>
 * @since 0.4
 */
public class EmailAlertListenerSupport implements AlertListener {

    private static final String DEFAULT_ALERT_SUBJECT = "Alert (%s) notification";

    private static final String DEFAULT_ALERT_BODY = "Actual count '%d' of the alert (%s) exceeded limit '%d'";

    @Autowired
    private EmailService emailService;

    @Override
    public boolean supports(AlertInfo alert) {
        // default for every alert
        return true;
    }

    /**
     * Default implementation is to send email notification.
     * If you need to implement whatever else then override {@link #onAlert(AlertInfo)} with custom actions.
     *
     * @param alert the activated alert
     * @param actualCount the actual count of items
     */
    @Override
    public final void onAlert(AlertInfo alert, long actualCount) {
        Log.debug("onAlert (" + alert.toHumanString() + ")");

        // use default subject or body if not defined specific one
        String subject = StringUtils.isNotEmpty(alert.getNotificationSubject())
                         ? formatSubject(alert)
                         : String.format(DEFAULT_ALERT_SUBJECT, alert.getId());

        String body = StringUtils.isNotEmpty(alert.getNotificationBody())
                      ? formatBody(alert, actualCount)
                      : String.format(DEFAULT_ALERT_BODY, actualCount, alert.getId(), alert.getLimit());

        Assert.state(StringUtils.isNotEmpty(subject), "subject can't be empty");
        Assert.state(StringUtils.isNotEmpty(body), "body can't be empty");

        // send email
        try {
            sendEmail(subject, body);
        } catch (Exception ex) {
            Log.error("Error occurred during email sending for alert id=" + alert.getId(), ex);
        }

        // do whatever else
        try {
            onAlert(alert);
        } catch (Exception ex) {
            Log.error("Error occurred during final action for alert id=" + alert.getId(), ex);
        }
    }

    /**
     * Does action when specified alert is activated.
     *
     * @param alert the activated alert
     */
    protected void onAlert(AlertInfo alert) {
        // no action by default
    }

    /**
     * Formats custom subject from specified alert. Format uses the following values in the same order:
     * <ul>
     *     <li>alert identification
     * </ul>
     * Override this method if you want to change subject formatting.
     *
     * @param alert the activated alert
     * @return formatted subject
     */
    protected String formatSubject(AlertInfo alert) {
        return String.format(alert.getNotificationSubject(), alert.getId());
    }

    /**
     * Formats custom body from specified alert. Format uses the following values in the same order:
     * <ul>
     *     <li>actual count
     *     <li>alert limit
     * </ul>
     * Override this method if you want to change body formatting.
     *
     * @param alert the activated alert
     * @param actualCount the actual count of items
     * @return formatted body
     */
    protected String formatBody(AlertInfo alert, long actualCount) {
        return String.format(alert.getNotificationBody(), actualCount, alert.getLimit());
    }

    /**
     * Sends notification email to administrators.
     *
     * @param subject the subject
     * @param body the body
     */
    protected final void sendEmail(String subject, String body) {
        emailService.sendEmailToAdmins(subject, body);
    }

    /**
     * Gets email service.
     *
     * @return email service
     */
    protected final EmailService getEmailService() {
        return emailService;
    }
}
