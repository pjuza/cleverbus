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

package org.cleverbus.test;

import org.cleverbus.api.entity.ExternalSystemExtEnum;


/**
 * Enumeration of possible external systems - for test purposes only.
 *
 * @author <a href="mailto:petr.juza@cleverlance.com">Petr Juza</a>
 */
public enum ExternalSystemTestEnum implements ExternalSystemExtEnum {

    CRM,

    BILLING;

    @Override
    public String getSystemName() {
        return name();
    }
}
