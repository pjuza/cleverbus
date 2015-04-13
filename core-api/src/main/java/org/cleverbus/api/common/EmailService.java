/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cleverbus.api.common;

import org.cleverbus.api.common.email.Email;

/**
 * Service for sending emails.
 *
 * @author <a href="mailto:petr.juza@cleverlance.com">Petr Juza</a>
 * @see Email
 */
public interface EmailService {

    public static final String BEAN = "emailService";

    /**
     * Sends email to administrators.
     *
     * @param subject the subject
     * @param body the body
     */
    void sendEmailToAdmins(String subject, String body);

    /**
     * Sends formatted email to recipients.
     * <p>
     * For example, message &quot;Hi {}. My name is {}.&quot;, &quot;Alice&quot;, &quot;Bob&quot;
     * will return the string "Hi Alice. My name is Bob.".
     *
     * @param recipients the comma separated recipients; if empty then email won't be send
     * @param subject the subject
     * @param body the body with possible placeholders {@code {}}
     * @param values the values for placeholders; count of values have to correspond with count of placeholders
     */
    void sendFormattedEmail(String recipients, String subject, String body, Object... values);

    /**
     * Send email defined in {@link Email}.
     *
     * @param email email that will be send
     */
    void sendEmail(Email email);
}
