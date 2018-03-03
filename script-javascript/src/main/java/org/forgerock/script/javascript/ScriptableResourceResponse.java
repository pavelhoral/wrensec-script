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

import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.script.scope.Parameter;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Wrapper;

/**
 * Provides a {@code Scriptable} wrapper for a {@code ResourceResponse} object.
 */
class ScriptableResourceResponse extends NativeObject implements Wrapper {

    private static final long serialVersionUID = 1L;

    /** The response being wrapped. */
    final transient Parameter parameter;

    /** The response being wrapped. */
    private final ResourceResponse response;

    /**
     * Constructs a new scriptable wrapper around the specified list.
     *
     * @param response
     *            the response to be wrapped.
     * @throws NullPointerException
     *             if the specified map is {@code null}.
     */
    public ScriptableResourceResponse(final Parameter parameter, final ResourceResponse response) {
        this.parameter = parameter;
        this.response = response;
    }

    @Override
    public String getClassName() {
        return "ScriptableResourceResponse";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object get(String name, Scriptable start) {
        if (ResourceResponse.FIELD_ID.equals(name)) {
            return Converter.wrap(parameter, response.getId(), start, false);
        } else if (ResourceResponse.FIELD_REVISION.equals(name)) {
            return Converter.wrap(parameter, response.getRevision(), start, false);
        } else if (ResourceResponse.FIELD_CONTENT.equals(name)) {
            return Converter.wrap(parameter, response.getContent(), start, false);
        } else {
            return super.get(name, start);
        }
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return ResourceResponse.FIELD_ID.equals(name)
                || ResourceResponse.FIELD_REVISION.equals(name)
                || ResourceResponse.FIELD_CONTENT.equals(name)
                || super.has(name, start);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        super.put(name, start);
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
    }

    private static Object[] PROPERTIES = new Object[] {
            ResourceResponse.FIELD_ID,
            ResourceResponse.FIELD_REVISION,
            ResourceResponse.FIELD_CONTENT };

    @Override
    public Object[] getIds() {
        return PROPERTIES;
    }


    @Override
    public Object unwrap() {
        return response;
    }

    public String toString() {
        if (response == null) {
            return "null";
        }

        return response.getContent().toString();
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
