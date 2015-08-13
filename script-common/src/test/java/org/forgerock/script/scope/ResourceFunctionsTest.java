/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

package org.forgerock.script.scope;

import static org.forgerock.json.resource.Router.uriTemplate;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import javax.script.Bindings;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.http.context.AbstractContext;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.Responses;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.forgerock.http.Context;
import org.forgerock.http.context.RootContext;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ConnectionFactory;
import org.forgerock.json.resource.MemoryBackend;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.Resources;
import org.forgerock.json.resource.Router;
import org.forgerock.script.engine.ScriptEngine;
import org.forgerock.script.engine.ScriptEngineFactory;
import org.forgerock.script.registry.ScriptRegistryImpl;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;

/**
 * Test the ResourceFunctions
 */
public class ResourceFunctionsTest {

    private ConnectionFactory getConnectionFactory() {
        // Get the OSGi Router
        final Router router = new Router();
        router.addRoute(uriTemplate("Users"), new MemoryBackend());
        router.addRoute(uriTemplate("Groups"), new MemoryBackend());
        return Resources.newInternalConnectionFactory(router);
    }

    final ActionResponse trueResult = Responses.newActionResponse(new JsonValue(true));

    final RequestHandler resource = mock(RequestHandler.class);

    final ConnectionFactory connectionFactory = Resources.newInternalConnectionFactory(resource);

    Context context = new RootContext();

    OperationParameter parameter = new OperationParameter(context);

    Function<JsonValue> action = ResourceFunctions.newActionFunction(connectionFactory);

    @Test(enabled = false)
    public void jsr223Test() throws Exception {

        // Example to use the stateless functions
        // ConnectionFunction.READ.call({ConnectionFactory,Context}, argument)

        ConnectionFactory connectionFactory = getConnectionFactory();

        Bindings globalBinding = new SimpleBindings(new ConcurrentHashMap<String, Object>());

        globalBinding.put("global", FunctionFactory.getResource(connectionFactory));

        // Use Spring or SCR to create this bean
        ScriptRegistryImpl registry = new ScriptRegistryImpl(
                new HashMap<String, Object>(), ServiceLoader.load(ScriptEngineFactory.class), globalBinding);

        // Find the Engine for the Script name
        ScriptEngine engine = registry.getEngineByName("JavaScript");

        // Merge, deep copy and compile
        Bindings runtime = null;// engine.compileBindings(null, null,
                                // registry.getBindings());

        // JSR 223 - 1
        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

        // JSR 223 - 2
        Function custom = new Function<JsonValue>() {

            private static final long serialVersionUID = 1L;

            @Override
            public JsonValue call(Parameter scope, Function<?> callback, Object... arguments)
                    throws ResourceException {
                return new JsonValue("Works!!");
            }
        };

        runtime = new SimpleBindings();

        // Compile single object
        runtime.put("custom", engine.compileObject(null, custom));

        scriptEngineManager.getEngineByName("JavaScript").eval("custom()", runtime);
    }

    @Test
    public void testAction() throws Exception {
        doAnswer(new Answer<Promise<ActionResponse, ResourceException>>() {
            public Promise<ActionResponse, ResourceException> answer(InvocationOnMock invocation) throws Throwable {
                // Context context = (Context) invocation.getArguments()[0];
                // ActionRequest request = (ActionRequest) invocation.getArguments()[1];
                return Promises.newResultPromise(trueResult);
            }
        }).when(resource).handleAction(any(Context.class), any(ActionRequest.class));

        // action(String endPoint[, String id], String type, Map params, Map content[, List fieldFilter][,Map context])
        // action(String resourceName, [String actionId,] Map params, Map content[, List fieldFilter][,Map context])
        Object[] arguments = new Object[] {
                "resourceName",
                "actionId",
                new HashMap<String, Object>(),
                new HashMap<String, Object>(),
                new ArrayList<String>()
        };
        Assert.assertTrue(action.call(parameter, null, arguments).asBoolean());
    }

    public static class CustomContext extends AbstractContext {
        protected CustomContext(Context parent) {
            super(parent, "custom");
        }
        protected CustomContext(JsonValue savedContext, ClassLoader classLoader) {
            super(savedContext, classLoader);
        }
    }

    @Test
    public void actionTestWithContext() throws Exception {

        doAnswer(new Answer<Promise<ActionResponse, ResourceException>>() {
            public Promise<ActionResponse, ResourceException> answer(InvocationOnMock invocation) throws Throwable {
                Context context = (Context) invocation.getArguments()[0];
                // return a true result if we're called with the custom context
                return Promises.newResultPromise(
                        Responses.newActionResponse(new JsonValue("custom".equals(context.getContextName()))));
            }
        }).when(resource).handleAction(any(Context.class), any(ActionRequest.class));

        Context localContext = new CustomContext(context);

        // action(String endPoint[, String id], String type, Map params, Map content[, List fieldFilter][,Map context])
        // action(String resourceName, [String actionId,] Map params, Map content[, List fieldFilter][,Map context])
        Object[] arguments = new Object[]{
                "resourceName",
                "actionId",
                new HashMap<String, Object>(),
                new HashMap<String, Object>(),
                new ArrayList<String>(),
                localContext
        };
        Assert.assertTrue(action.call(parameter, null, arguments).asBoolean());
    }

    @Test(expectedExceptions = NoSuchMethodException.class)
    public void testActionMissingActionId() throws Exception {
        doAnswer(new Answer<Promise<ActionResponse, ResourceException>>() {
            public Promise<ActionResponse, ResourceException> answer(InvocationOnMock invocation) throws Throwable {
                // ActionRequest request = (ActionRequest)
                // invocation.getArguments()[1];
                return Promises.newResultPromise(trueResult);
            }
        }).when(resource).handleAction(any(Context.class), any(ActionRequest.class));

        // action(String endPoint[, String id], String type, Map params, Map content[, List fieldFilter][,Map context])
        // action(String resourceName, [String actionId,] Map params, Map content[, List fieldFilter][,Map context])
        Object[] arguments = new Object[] {
                "resourceName",
                null,
                "actionId",
                new HashMap<String, Object>(),
                new HashMap<String, Object>(),
                new ArrayList<String>(),
                new HashMap<String, Object>()
        };
        action.call(parameter, null, arguments);
    }

    @Test(expectedExceptions = NoSuchMethodException.class)
    public void testActionMissingContent() throws Exception {
        doAnswer(new Answer<Promise<ActionResponse, ResourceException>>() {
            public Promise<ActionResponse, ResourceException> answer(InvocationOnMock invocation) throws Throwable {
                // ActionRequest request = (ActionRequest)
                // invocation.getArguments()[1];
                return Promises.newResultPromise(trueResult);
            }
        }).when(resource).handleAction(any(Context.class), any(ActionRequest.class));

        // action(String endPoint[, String id], String type, Map params, Map content[, List fieldFilter][,Map context])
        // action(String resourceName, [String actionId,] Map params, Map content[, List fieldFilter][,Map context])
        Object[] arguments = new Object[] {
                "resourceName",
                "actionId",
                new HashMap<String, Object>(),
                new ArrayList<String>(),
                new HashMap<String, Object>()
        };
        action.call(parameter, null, arguments);
    }

}
