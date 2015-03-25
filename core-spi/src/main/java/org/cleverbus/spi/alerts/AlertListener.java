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

package org.cleverbus.spi.alerts;

/**
 * Listener notifies when any alert is activated.
 *
 * @author <a href="mailto:petr.juza@cleverlance.com">Petr Juza</a>
 * @since 0.4
 */
public interface AlertListener {

    /**
     * Does this listener support the specified alert?
     *
     * @return {@code true} if specified alert is supported by this listener otherwise {@code false}
     * @see CoreAlertsEnum
     */
    boolean supports(AlertInfo alert);

    /**
     * Notifies that specified alert is activated.
     *
     * @param alert the alert
     * @param actualCount the actual count of items
     */
    void onAlert(AlertInfo alert, long actualCount);
}
