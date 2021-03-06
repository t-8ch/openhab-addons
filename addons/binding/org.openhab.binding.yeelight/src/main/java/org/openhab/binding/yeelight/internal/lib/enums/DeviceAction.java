/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.yeelight.internal.lib.enums;

/**
 * @author Coaster Li - Initial contribution
 */
public enum DeviceAction {
    open,
    close,
    brightness,
    color,
    colortemperature,
    increase_bright,
    decrease_bright,
    increase_ct,
    decrease_ct;

    private String mStrValue;
    private int mIntValue;

    public void putValue(String value) {
        this.mStrValue = value;
    }

    public void putValue(int value) {
        this.mIntValue = value;
    }

    public String strValue() {
        return mStrValue;
    }

    public int intValue() {
        return mIntValue;
    }
}
