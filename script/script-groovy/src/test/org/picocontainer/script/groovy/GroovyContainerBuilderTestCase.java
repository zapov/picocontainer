/*****************************************************************************
 * Copyright (C) 2003-2011 PicoContainer Committers. All rights reserved.    *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
 *****************************************************************************/
package org.picocontainer.script.groovy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import groovy.lang.Binding;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;
import org.picocontainer.DefaultPicoContainer;
import org.picocontainer.PicoBuilder;
import org.picocontainer.PicoContainer;
import org.picocontainer.script.AbstractScriptedContainerBuilderTestCase;
import org.picocontainer.script.ContainerBuilder;
import org.picocontainer.script.NoOpPostBuildContainerAction;
import org.picocontainer.script.TestHelper;
import org.picocontainer.script.testmodel.A;

/**
 * @author Aslak Helles&oslash;y
 * @author Paul Hammant
 */
public class GroovyContainerBuilderTestCase extends AbstractScriptedContainerBuilderTestCase {

    @Test public void testContainerCanBeBuiltWithParent() {
        Reader script = new StringReader("" +
                "builder = new org.picocontainer.script.groovy.GroovyNodeBuilder()\n" +
                "def pico = builder.container(parent:parent) { \n" +
                "  component(StringBuffer)\n" +
                "}");
        PicoContainer parent = new DefaultPicoContainer();
        PicoContainer pico = buildContainer(new GroovyContainerBuilder(script, getClass().getClassLoader()), parent, "SOME_SCOPE");
        //PicoContainer.getParent() is now ImmutablePicoContainer
        assertNotSame(parent, pico.getParent());
        assertEquals(StringBuffer.class, pico.getComponent(StringBuffer.class).getClass());
    }

    @Test public void testAdditionalBindingViaSubClassing() {
                Reader script = new StringReader("" +
                "builder = new org.picocontainer.script.groovy.GroovyNodeBuilder()\n" +
                "def pico = builder.container(parent:parent) { \n" +
                "  component(key:String.class, instance:foo)\n" +
                "}");

		PicoContainer parent = new DefaultPicoContainer();
		PicoContainer pico = buildContainer(new SubclassGroovyContainerBuilder(script, getClass().getClassLoader()),
		        parent, "SOME_SCOPE");

		assertNotSame(parent, pico.getParent());
		assertEquals("bar", pico.getComponent(String.class));
	}

    @Test public void testBuildingWithDefaultBuilder() {
        // NOTE script does NOT define a "builder"
        Reader script = new StringReader("" +
                "def pico = builder.container(parent:parent) { \n" +
                "  component(key:String.class, instance:'foo')\n" +
                "}");

		PicoContainer parent = new DefaultPicoContainer();
		PicoContainer pico = buildContainer(new GroovyContainerBuilder(script, getClass().getClassLoader()), parent,
		        "SOME_SCOPE");

		assertNotSame(parent, pico.getParent());
		assertEquals("foo", pico.getComponent(String.class));
	}

    @Test public void testBuildingWithAppendingNodes() {
        Reader script = new StringReader("" +
                "def pico = builder.container(parent:parent) { \n" +
                			"}\n" + 
                			"\n" + 
                			"builder.append(container:pico) {" +
                			"  component(key:String.class, instance:'foo')\n" + 
                			"}\n"
		        + "");

		PicoContainer parent = new DefaultPicoContainer();
		PicoContainer pico = buildContainer(new GroovyContainerBuilder(script, getClass().getClassLoader()), parent,
		        "SOME_SCOPE");

		assertNotSame(parent, pico.getParent());
		assertEquals("foo", pico.getComponent(String.class));
	}

    @Test public void testBuildingWithPicoSyntax() {
        Reader script = new StringReader("" +
                "parent.addComponent('foo', java.lang.String)\n"  +
                "def pico = new org.picocontainer.DefaultPicoContainer(parent)\n" +
                "pico.addComponent(org.picocontainer.script.testmodel.A)\n" +
                "");

        PicoContainer parent = new DefaultPicoContainer();
        PicoContainer pico = buildContainer(new GroovyContainerBuilder(script, getClass().getClassLoader()), parent, "SOME_SCOPE");

        assertNotSame(parent, pico.getParent());
        assertNotNull(pico.getComponent(A.class));
        assertNotNull(pico.getComponent("foo"));
    }



    @Test public void testBuildingWithPicoSyntaxAndNullParent() {
        Reader script = new StringReader("" +
                "def pico = new org.picocontainer.DefaultPicoContainer(parent)\n" +
                "pico.addComponent(org.picocontainer.script.testmodel.A)\n" +
                "");

        PicoContainer parent = null;
        PicoContainer pico = buildContainer(new GroovyContainerBuilder(script, getClass().getClassLoader()), parent, "SOME_SCOPE");

        assertNotSame(parent, pico.getParent());
        assertNotNull(pico.getComponent(A.class));
    }

	/**
	 * Child SubclassGroovyContainerBuilder which adds additional bindings
	 */
	private class SubclassGroovyContainerBuilder extends GroovyContainerBuilder {
		public SubclassGroovyContainerBuilder(final Reader script, ClassLoader classLoader) {
			super(script, classLoader);
		}

		protected void handleBinding(Binding binding) {
			super.handleBinding(binding);

			binding.setVariable("foo", "bar");
		}

	}
	
	@Test
	public void testRunningGroovyScriptWithinCustomClassLoader() throws MalformedURLException, ClassNotFoundException {
		Reader script = new StringReader("" +
				"def pico = new org.picocontainer.PicoBuilder().withCaching().withLifecycle().build();\n" +
				"pico.addComponent(\"TestComp\", TestComp);"
			);
        File testCompJar = TestHelper.getTestCompJarFile();
        assertTrue(testCompJar.isFile());
        URL compJarURL = testCompJar.toURI().toURL();
        URLClassLoader cl  = new URLClassLoader(new URL[] {compJarURL}, getClass().getClassLoader());
        assertNotNull(cl.loadClass("TestComp"));
        
        PicoContainer pico = buildContainer(new GroovyContainerBuilder(script, cl), null, null);
        assertNotNull(pico.getComponent("TestComp"));
        assertEquals("TestComp", pico.getComponent("TestComp").getClass().getName());
	}
	
	
	@Test public void testAutoStar1tingContainerBuilderStarts() {
        A.reset();
        Reader script = new StringReader("" +
                "def pico = builder.container(parent:parent) { \n" +
                "  component(org.picocontainer.script.testmodel.A)\n" +
                "}");
        PicoContainer parent = new PicoBuilder().withLifecycle().withCaching().build();
        PicoContainer pico = buildContainer(new GroovyContainerBuilder(script, getClass().getClassLoader()), parent, "SOME_SCOPE");
        //PicoContainer.getParent() is now ImmutablePicoContainer
        assertNotSame(parent, pico.getParent());
        assertEquals("<A",A.componentRecorder);		
        A.reset();
	}
	
    @Test public void testNonAutoStartingContainerBuildDoesntAutostart() {
        A.reset();
        Reader script = new StringReader("" +
        		"import org.picocontainer.script.testmodel.A\n" +
                "def pico = builder.container(parent:parent) { \n" +
                "  component(A)\n" +
                "}");
        PicoContainer parent = new PicoBuilder().withLifecycle().withCaching().build();
        ContainerBuilder containerBuilder = new GroovyContainerBuilder(script, getClass().getClassLoader()).setPostBuildAction(new NoOpPostBuildContainerAction());
        PicoContainer pico = buildContainer(containerBuilder, parent, "SOME_SCOPE");
        //PicoContainer.getParent() is now ImmutablePicoContainer
        assertNotSame(parent, pico.getParent());
        assertEquals("",A.componentRecorder);
        A.reset();
    }
	

}
