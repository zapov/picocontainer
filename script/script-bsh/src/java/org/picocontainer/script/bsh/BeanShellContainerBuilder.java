package org.picocontainer.script.bsh;

import java.io.IOException;
import java.io.Reader;
import java.net.URL;

import org.picocontainer.PicoContainer;
import org.picocontainer.script.ScriptedContainerBuilder;
import org.picocontainer.script.ScriptedPicoContainerMarkupException;

import bsh.EvalError;
import bsh.Interpreter;

/**
 * {@inheritDoc}
 * The script has to assign a "pico" variable with an instance of
 * {@link org.picocontainer.PicoContainer}.
 * There is an implicit variable named "parent" that may contain a reference to a parent
 * container. It is recommended to use this as a constructor argument to the instantiated
 * PicoContainer.
 *
 * @author Aslak Helles&oslash;y
 * @author Michael Rimov
 * @author Mauro Talevi
 */
public class BeanShellContainerBuilder extends ScriptedContainerBuilder {

    public BeanShellContainerBuilder(Reader script, ClassLoader classLoader) {
        super(script, classLoader);
    }

    public BeanShellContainerBuilder(URL script, ClassLoader classLoader) {
    	super(script, classLoader);
    }


    protected PicoContainer createContainerFromScript(PicoContainer parentContainer, Object assemblyScope) {
        Interpreter i = new Interpreter();
        try {
            i.set("parent", parentContainer);
            i.set("assemblyScope", assemblyScope);
            i.setClassLoader(this.getClassLoader());
            i.eval(getScriptReader(), i.getNameSpace(), "picocontainer.bsh");
            return (PicoContainer) i.get("pico");
        } catch (EvalError e) {
            throw new ScriptedPicoContainerMarkupException(e);
        } catch (IOException e) {
            throw new ScriptedPicoContainerMarkupException(e);
        }
    }
}
