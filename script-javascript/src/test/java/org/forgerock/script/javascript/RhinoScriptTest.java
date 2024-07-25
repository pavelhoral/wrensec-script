package org.forgerock.script.javascript;

import static org.testng.Assert.assertEquals;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.script.SimpleBindings;
import org.forgerock.script.ScriptName;
import org.forgerock.script.engine.CompilationHandler;
import org.forgerock.script.engine.CompiledScript;
import org.forgerock.script.source.DirectoryContainer;
import org.forgerock.script.source.ScriptSource;
import org.forgerock.script.source.SourceContainer;
import org.forgerock.script.source.SourceUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RhinoScriptTest {

    private SourceContainer container;

    private RhinoScriptEngine engine;

    @BeforeClass
    public void initEngine() throws Exception {
        ClassLoader classLoader = RhinoScriptTest.class.getClassLoader();

        container = new DirectoryContainer("test", classLoader.getResource("scripts/"));

        engine = (RhinoScriptEngine) new RhinoScriptEngineFactory().getScriptEngine(
                Collections.emptyMap(),
                Collections.singleton(container),
                classLoader);
    }


    public CompiledScript loadScript(String name) throws Exception {
        ScriptSource source = container.findScriptSource(new ScriptName(name, SourceUnit.AUTO_DETECT));

        AtomicReference<CompiledScript> result = new AtomicReference<CompiledScript>();
        engine.compileScript(new CompilationHandler() {
            @Override
            public void setCompiledScript(CompiledScript script) {
                result.set(script);
            }

            @Override
            public void setClassLoader(ClassLoader classLoader) {
            }

            @Override
            public void handleException(Exception exception) {
                throw new IllegalStateException(exception);
            }

            @Override
            public ScriptSource getScriptSource() {
                return source;
            }

            @Override
            public ClassLoader getParentClassLoader() {
                return null;
            }
        });
        return result.get();
    }


    @Test
    public void testSimpleEval() throws Exception {
        Object result = loadScript("simple.js").eval(null, null);
        assertEquals(result, "HELLO WORLD");
    }

    @Test
    public void testModuleLoad() throws Exception {
        Object result = loadScript("main.js").eval(null, null);
        assertEquals(result, "HELLO MODULE!");
    }

    @Test
    public void testModuleGlobal() throws Exception {
        Object result = loadScript("main.js").eval(null, new SimpleBindings(Map.of("welcomeName", "BINDING")));
        assertEquals(result, "HELLO BINDING!");
    }

    @Test
    public void testRepeatedCall() throws Exception {
        CompiledScript script = loadScript("main.js");
        script.eval(null, null);
        script.eval(null, null);
    }

}
