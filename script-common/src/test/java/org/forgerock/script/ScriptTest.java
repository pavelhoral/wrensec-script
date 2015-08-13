/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 ForgeRock AS.. All rights reserved.
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

package org.forgerock.script;

import org.forgerock.http.Context;
import org.forgerock.http.context.RootContext;
import org.forgerock.http.routing.RoutingMode;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ConnectionFactory;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.MemoryBackend;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.Resources;
import org.forgerock.json.resource.Responses;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.SingletonResourceProvider;
import org.forgerock.json.resource.SecurityContext;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.script.engine.ScriptEngineFactory;
import org.forgerock.script.exception.ScriptCompilationException;
import org.forgerock.script.exception.ScriptThrownException;
import org.forgerock.script.registry.ScriptRegistryImpl;
import org.forgerock.script.scope.Function;
import org.forgerock.script.scope.FunctionFactory;
import org.forgerock.script.source.DirectoryContainer;
import org.forgerock.script.source.EmbeddedScriptSource;
import org.forgerock.script.source.ScriptSource;
import org.forgerock.script.source.SourceContainer;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.resource.Router.uriTemplate;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * A NAME does ...
 *
 * @author Laszlo Hordos
 */
public abstract class ScriptTest {

    private ScriptRegistryImpl scriptRegistry = null;

    ConnectionFactory connectionFactory = null;

    protected abstract Map<String, Object> getConfiguration();

    protected abstract String getLanguageName();

    protected abstract URL getScriptContainer(String name);

    @BeforeClass
    public void initScriptRegistry() throws Exception {
        Map<String, Object> configuration = new HashMap<String, Object>(1);
        configuration.put(getLanguageName(), getConfiguration());

        scriptRegistry =
                new ScriptRegistryImpl(configuration,
                        ServiceLoader.load(ScriptEngineFactory.class), null, null);

        RequestHandler resource = mock(RequestHandler.class);
        SingletonResourceProvider singletonProvider = mock(SingletonResourceProvider.class);

        final Router router = new Router();
        router.addRoute(uriTemplate("/Users"), new MemoryBackend());
        router.addRoute(uriTemplate("/Groups"), new MemoryBackend());
        router.addRoute(uriTemplate("mock/{id}"), singletonProvider);

        connectionFactory = Resources.newInternalConnectionFactory(router);

        doAnswer(new Answer<Promise<ResourceResponse,ResourceException>>() {
            public Promise<ResourceResponse,ResourceException> answer(InvocationOnMock invocation) throws Throwable {
                ReadRequest request = (ReadRequest) invocation.getArguments()[1];
                return Promises.newResultPromise(
                        Responses.newResourceResponse(request.getResourcePath(), "1", json(object())));
            }
        }).when(resource).handleRead(any(Context.class), any(ReadRequest.class));

        scriptRegistry.put("router", FunctionFactory.getResource(connectionFactory));

        URL container = getScriptContainer("/container/");
        Assert.assertNotNull(container);

        scriptRegistry.addSourceUnit(new DirectoryContainer("container", container));
        scriptRegistry.addSourceUnit(new EmbeddedScriptSource(ScriptEntry.Visibility.PUBLIC,
                "egy = egy + 2;egy", new ScriptName("test1", getLanguageName())));

    }

    public ScriptRegistry getScriptRegistry() {
        return scriptRegistry;
    }

    @Test
    public void testEval() throws Exception {
        ScriptName scriptName = new ScriptName("test1", getLanguageName());
        ScriptEntry scriptEntry = getScriptRegistry().takeScript(scriptName);
        Assert.assertNotNull(scriptEntry);
        scriptEntry.put("egy", 1);
        assertThat(scriptEntry.getScript(new RootContext()).eval()).isEqualTo(3);

        // Load Script from Directory
        scriptEntry = getScriptRegistry().takeScript(new ScriptName("sample", getLanguageName()));
        Assert.assertNotNull(scriptEntry);

        // Set ServiceLevel Scope
        scriptEntry.put("egy", 1);
        Script script = scriptEntry.getScript(new RootContext());

        // Set RequestLevel Scope
        script.put("ketto", 2);
        assertThat(script.eval()).isEqualTo(3);
    }

    protected abstract EmbeddedScriptSource getScriptSourceWithException();

    @Test(expectedExceptions = ScriptThrownException.class)
    public void testException() throws Exception {
        ScriptSource scriptSource = getScriptSourceWithException();

        getScriptRegistry().addSourceUnit(scriptSource);
        Assert.assertNotNull(getScriptRegistry().takeScript(scriptSource.getName()));
        getScriptRegistry().takeScript(scriptSource.getName()).getScript(new RootContext()).eval(
                new SimpleBindings());
    }

    @Test
    public void testResource() throws Exception {
        ScriptName scriptName = new ScriptName("resource", getLanguageName());
        ScriptEntry scriptEntry = getScriptRegistry().takeScript(scriptName);
        Assert.assertNotNull(scriptEntry);

        Script script = scriptEntry.getScript(new RootContext());
        // Set RequestLevel Scope
        script.put("ketto", 2);
        script.putSafe("callback", mock(Function.class));

        JsonValue createContent = new JsonValue(new LinkedHashMap<String, Object>());
        createContent.put("externalId", "701984");
        createContent.put("userName", "bjensen@example.com");
        createContent.put("assignedDashboard", Arrays.asList("Salesforce", "Google",
                "ConstantContact"));
        createContent.put("displayName", "Babs Jensen");
        createContent.put("nickName", "Babs");

        JsonValue updateContent = createContent.copy();
        updateContent.put("_id", UUID.randomUUID().toString());
        updateContent.put("profileUrl", "https://login.example.com/bjensen");

        final Context context = new SecurityContext(new RootContext(), "bjensen@example.com", null);
        script.put("context", context);

        CreateRequest createRequest = Requests.newCreateRequest("/Users", "701984", createContent);
        script.put("createRequest", createRequest);
        ReadRequest readRequest = Requests.newReadRequest("/Users/701984");
        script.put("readRequest", readRequest);
        UpdateRequest updateRequest = Requests.newUpdateRequest("/Users/701984", updateContent);
        script.put("updateRequest", updateRequest);
        PatchRequest patchRequest =
                Requests.newPatchRequest("/Users/701984", PatchOperation
                        .replace("userName", "ddoe"));
        script.put("patchRequest", patchRequest);
        QueryRequest queryRequest = Requests.newQueryRequest("/Users/");
        script.put("queryRequest", queryRequest);
        DeleteRequest deleteRequest = Requests.newDeleteRequest("/Users/701984");
        script.put("deleteRequest", deleteRequest);
        ActionRequest actionRequest = Requests.newActionRequest("/Users", "clear");
        script.put("actionRequest", actionRequest);
        script.eval();
    }

    @DataProvider(name = "Data-Provider-Function")
    public Object[][] scriptProvider() throws ScriptException {
        ScriptEntry scriptEntry =
                getScriptRegistry().takeScript(new ScriptName("sample", getLanguageName()));
        scriptEntry.put("egy", 1);
        return new Object[][] { { scriptEntry } };
    }

    @Test(threadPoolSize = 4, invocationCount = 10, timeOut = 1000,
            dataProvider = "Data-Provider-Function", dependsOnMethods = { "testEval" })
    public void parameterIntTest(ScriptEntry scriptEntry) throws Exception {
        Script script = scriptEntry.getScript(new RootContext());
        script.put("ketto", 2);
        assertThat(script.eval()).isEqualTo(3);
    }

    @Test
    public void testListener() throws Exception {
        ScriptName scriptName = new ScriptName("listener", getLanguageName());
        final SourceContainer parentContainer =
                new DirectoryContainer("root", getScriptContainer("/"));
        ScriptSource scriptSource = new EmbeddedScriptSource("2 * 2", scriptName) {
            public ScriptName[] getDependencies() {
                return new ScriptName[] { parentContainer.getName() };
            }
        };
        final Object[] status = new Object[2];
        ScriptListener listener = new ScriptListener() {
            public void scriptChanged(ScriptEvent event) throws ScriptException {
                status[0] = Integer.valueOf(event.getType());
                status[1] = event.getScriptLibraryEntry();
            }
        };

        getScriptRegistry().addScriptListener(scriptName, listener);
        // verifyZeroInteractions(listener);

        getScriptRegistry().addSourceUnit(scriptSource);
        Assert.assertEquals(status[0], ScriptEvent.REGISTERED);
        assertThat(((ScriptEntry) status[1]).getScript(new RootContext()).eval()).isEqualTo(4);

        scriptName = new ScriptName("listener", getLanguageName(), "1");
        scriptSource = new EmbeddedScriptSource("2 * 2", scriptName) {
            public ScriptName[] getDependencies() {
                return new ScriptName[] { parentContainer.getName() };
            }
        };
        getScriptRegistry().addSourceUnit(scriptSource);
        Assert.assertEquals(status[0], ScriptEvent.MODIFIED);
        assertThat(((ScriptEntry) status[1]).getScript(new RootContext()).eval()).isEqualTo(4);
        getScriptRegistry().removeSourceUnit(scriptSource);
        Assert.assertEquals(status[0], ScriptEvent.UNREGISTERING);
        try {
            ((ScriptEntry) status[1]).getScript(new RootContext()).eval();
            Assert.fail("Script MUST fail!");
        } catch (ScriptException e) {
            /* Expected */
        } catch (Exception e) {
            Assert.fail("Expecting script to fail with ScriptException");
        }

        getScriptRegistry().addSourceUnit(scriptSource);
        Assert.assertEquals(status[0], ScriptEvent.REGISTERED);
        assertThat(((ScriptEntry) status[1]).getScript(new RootContext()).eval()).isEqualTo(4);
        getScriptRegistry().removeSourceUnit(parentContainer);
        Assert.assertEquals(status[0], ScriptEvent.UNREGISTERING);

        getScriptRegistry().addSourceUnit(scriptSource);
        Assert.assertEquals(status[0], ScriptEvent.REGISTERED);
        getScriptRegistry().deleteScriptListener(scriptName, listener);

        getScriptRegistry().addSourceUnit(scriptSource);
        Assert.assertEquals(status[0], ScriptEvent.REGISTERED);

    }

    @Test(expectedExceptions = ScriptCompilationException.class)
    public void testCompiler() throws Exception {
        ScriptName scriptName = new ScriptName("invalid", getLanguageName());
        try {
            ScriptSource scriptSource = new EmbeddedScriptSource("must-fail(\"syntax error')", scriptName);
            Assert.assertNull(getScriptRegistry().takeScript(scriptName));

            getScriptRegistry().addSourceUnit(scriptSource);
        } catch (ScriptCompilationException e) {
            Assert.assertNull(getScriptRegistry().takeScript(scriptName));
            throw e;
        }
    }
}
