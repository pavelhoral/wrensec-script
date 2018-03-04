/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.script.javascript;

import java.util.Arrays;

import org.forgerock.json.resource.ActionResponse;
import org.forgerock.script.scope.Parameter;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Wrapper;

/**
 * Provides a {@code Scriptable} wrapper for a {@code ActionResponse} object.
 */
public class ScriptableActionResponse extends NativeObject implements Wrapper {

    private static final long serialVersionUID = 1L;

    private static final String FIELD_CONTENT = "content";

    /** The response being wrapped. */
    final transient Parameter parameter;

    /** The response being wrapped. */
    private final ActionResponse response;

    /**
     * Constructs a new scriptable wrapper around the specified response.
     *
     * @param response
     *            the response to be wrapped.
     * @throws NullPointerException
     *             if the specified response is {@code null}.
     */
    public ScriptableActionResponse(final Parameter parameter, final ActionResponse response) {
        if (null == response) {
            throw new NullPointerException();
        }
        this.parameter = parameter;
        this.response = response;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object get(String name, Scriptable start) {
        if (FIELD_CONTENT.equals(name)) {
            return Converter.wrap(parameter, response.getJsonContent(), start, false);
        } else {
            return NOT_FOUND;
        }
    }

    @Override
    public Object get(int index, Scriptable start) {
        return NOT_FOUND;
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return FIELD_CONTENT.equals(name);
    }

    @Override
    public boolean has(int index, Scriptable start) {
        return false;
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        // don't allow setting fields or resource name
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        // setting by index not supported
    }

    @Override
    public void delete(String name) {
    }

    @Override
    public void delete(int index) {
    }

    /** the generic response properties common to all response types */
    private static final Object[] PROPERTIES = new Object[] {
            FIELD_CONTENT
    };

    @Override
    public Object[] getIds() {
        return PROPERTIES;
    }

    protected static Object[] concatIds(Object... properties) {
        Object[] result = Arrays.copyOf(PROPERTIES, PROPERTIES.length + properties.length);
        System.arraycopy(properties, 0, result, PROPERTIES.length, properties.length);
        return result;
    }

    @Override
    public boolean hasInstance(Scriptable instance) {
        return false; // no support for javascript instanceof
    }

    @Override
    public Object unwrap() {
        return response;
    }

    public String toString() {
        if (response == null) {
            return "null";
        }

        return response.getJsonContent().toString();
    }

    @Override
    public Object getDefaultValue(Class<?> hint) {
        if (hint == null || hint == String.class) {
            return toString();
        } else {
            return super.getDefaultValue(hint);
        }
    }
}
