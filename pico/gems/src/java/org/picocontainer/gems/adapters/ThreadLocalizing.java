/*****************************************************************************
 * Copyright (C) 2003-2010 PicoContainer Committers. All rights reserved.    *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by Joerg Schaible                                           *
 *****************************************************************************/

package org.picocontainer.gems.adapters;

import com.thoughtworks.proxy.Invoker;
import com.thoughtworks.proxy.ProxyFactory;
import com.thoughtworks.proxy.factory.StandardProxyFactory;
import com.thoughtworks.proxy.kit.ReflectionUtils;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.ComponentFactory;
import org.picocontainer.ComponentMonitor;
import org.picocontainer.LifecycleStrategy;
import org.picocontainer.Parameter;
import org.picocontainer.PicoCompositionException;
import org.picocontainer.PicoContainer;
import org.picocontainer.behaviors.AbstractBehavior;
import org.picocontainer.behaviors.Caching;
import org.picocontainer.behaviors.Storing;
import org.picocontainer.references.ThreadLocalReference;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Properties;
import java.util.Set;


/**
 * A {@link ComponentFactory} for components kept in {@link ThreadLocal} instances.
 * <p>
 * This factory has two operating modes. By default it ensures, that every thread uses its own component at any time.
 * This mode ({@link #ENSURE_THREAD_LOCALITY}) makes internal usage of a {@link ThreadLocalized}. If the
 * application architecture ensures, that the thread that creates the component is always also the thread that is th
 * only user, you can set the mode {@link #THREAD_ENSURES_LOCALITY}. In this mode the factory uses a simple
 * {@link org.picocontainer.behaviors.Caching.Cached} that uses a {@link ThreadLocalReference} to cache the component.
 * </p>
 * <p>
 * See the use cases for the subtile difference:
 * </p>
 * <p>
 * <code>THREAD_ENSURES_LOCALITY</code> is applicable, if the pico container is requested for a thread local addComponent
 * from the working thread e.g. in a web application for a request. In this environment it is ensured, that the request
 * is processed from the same thread and the thread local component is reused, if a previous request was handled in the
 * same thread. Note that thi scenario fails badly, if the thread local component is created because of another cached
 * component indirectly by a dependecy. In this case the cached component already have an instance of the thread local
 * component, that may have been created in another thread, since only the component adapter for the thread local
 * component can ensure a unique component for each thread.
 * </p>
 * <p>
 * <code>ENSURES_THREAD_LOCALITY</code> solves this problem. In this case the returned component is just a proxy for
 * the thread local component and this proxy ensures, that a new component is created for each thread. Even if another
 * cached component has an indirect dependency on the thread local component, the proxy ensures unique instances. This
 * is vital for a multithreaded application that uses EJBs.
 * </p>
 * @author J&ouml;rg Schaible
 */
@SuppressWarnings("serial")
public final class ThreadLocalizing extends AbstractBehavior {


	/**
     * <code>ENSURE_THREAD_LOCALITY</code> is the constant for created {@link ComponentAdapter} instances, that ensure
     * unique instances of the component by delivering a proxy for the component.
     */
    public static final boolean ENSURE_THREAD_LOCALITY = true;
    /**
     * <code>THREAD_ENSURES_LOCALITY</code> is the constant for created {@link ComponentAdapter} instances, that
     * create for the current thread a new component.
     */
    public static final boolean THREAD_ENSURES_LOCALITY = false;

    private final boolean ensureThreadLocal;
    private final ProxyFactory proxyFactory;

    /**
     * Constructs a wrapping ThreadLocalizing, that ensures the usage of the ThreadLocal. The Proxy
     * instances are generated by the JDK.
     */
    public ThreadLocalizing() {
        this(new StandardProxyFactory());
    }

    /**
     * Constructs a wrapping ThreadLocalizing, that ensures the usage of the ThreadLocal.
     * @param proxyFactory The {@link ProxyFactory} to use.
     */
    public ThreadLocalizing(final ProxyFactory proxyFactory) {
        this(ENSURE_THREAD_LOCALITY, proxyFactory);
    }

    /**
     * Constructs a wrapping ThreadLocalizing.
     * @param ensure {@link #ENSURE_THREAD_LOCALITY} or {@link #THREAD_ENSURES_LOCALITY}.
     */
    public ThreadLocalizing(final boolean ensure) {
        this(ensure, new StandardProxyFactory());
    }

    /**
     * Constructs a wrapping ThreadLocalizing.
     * @param ensure {@link #ENSURE_THREAD_LOCALITY} or {@link #THREAD_ENSURES_LOCALITY}.
     * @param factory The {@link ProxyFactory} to use.
     */
    protected ThreadLocalizing(final boolean ensure, final ProxyFactory factory) {
        ensureThreadLocal = ensure;
        proxyFactory = factory;
    }

    @Override
	public <T> ComponentAdapter<T> createComponentAdapter(final ComponentMonitor monitor, final LifecycleStrategy lifecycle, final Properties componentProps,
            final Object key, final Class<T> impl, final Parameter... parameters) throws PicoCompositionException {
        if (ensureThreadLocal) {
            return new ThreadLocalized<T>(super.createComponentAdapter(
                    monitor, lifecycle, componentProps, key, impl, parameters), proxyFactory);
        } else {
            return new Caching.Cached<T>(super.createComponentAdapter(
                    monitor, lifecycle, componentProps, key, impl, parameters), new ThreadLocalReference<Storing.Stored.Instance<T>>());
        }
    }


    @Override
	public <T> ComponentAdapter<T> addComponentAdapter(final ComponentMonitor monitor, final LifecycleStrategy lifecycle,
                                                final Properties componentProps, final ComponentAdapter<T> adapter) {
        if (ensureThreadLocal) {
            return monitor.changedBehavior(new ThreadLocalized<T>(
                    super.addComponentAdapter(monitor, lifecycle, componentProps, adapter), proxyFactory));
        } else {
            return monitor.changedBehavior(new Caching.Cached<T>(
                    super.addComponentAdapter(monitor, lifecycle, componentProps, adapter), new ThreadLocalReference<Storing.Stored.Instance<T>>()));
        }

    }

    /**
     * A {@link org.picocontainer.ComponentAdapter} that realizes a {@link ThreadLocal} component instance.
     * <p>
     * The adapter creates proxy instances, that will create the necessary instances on-the-fly invoking the methods of the
     * instance. Use this adapter, if you are instantiating your components in a single thread, but should be different when
     * accessed from different threads. See {@link org.picocontainer.gems.adapters.ThreadLocalizing} for details.
     * </p>
     * <p>
     * Note: Because this implementation uses a {@link java.lang.reflect.Proxy}, you can only access the methods exposed by the implemented
     * interfaces of your component.
     * </p>
     *
     * @author J&ouml;rg Schaible
     */
    @SuppressWarnings("serial")
    public static final class ThreadLocalized<T> extends AbstractChangedBehavior<T> {

        private transient Class[] interfaces;
        private final ProxyFactory proxyFactory;

        /**
         * Construct a ThreadLocalized.
         *
         * @param delegate The {@link org.picocontainer.ComponentAdapter} to delegate.
         * @param proxyFactory The {@link com.thoughtworks.proxy.ProxyFactory} to use.
         * @throws org.picocontainer.PicoCompositionException Thrown if the component does not implement any interface.
         */
        public ThreadLocalized(final ComponentAdapter<T> delegate, final ProxyFactory proxyFactory)
                throws PicoCompositionException {
            super(new Caching.Cached<T>(delegate, new ThreadLocalReference<Storing.Stored.Instance<T>>()));
            this.proxyFactory = proxyFactory;
            interfaces = getInterfaces();
        }

        /**
         * Construct a ThreadLocalized using {@link java.lang.reflect.Proxy} instances.
         *
         * @param delegate The {@link org.picocontainer.ComponentAdapter} to delegate.
         * @throws org.picocontainer.PicoCompositionException Thrown if the component does not implement any interface.
         */
        public ThreadLocalized(final ComponentAdapter<T> delegate) throws PicoCompositionException {
            this(new Caching.Cached<T>(delegate, new ThreadLocalReference<Storing.Stored.Instance<T>>()), new StandardProxyFactory());
        }

        @Override
        public T getComponentInstance(final PicoContainer pico, final Type into) throws PicoCompositionException {

            if (interfaces == null) {
                interfaces = getInterfaces();
            }

            final Invoker invoker = new ThreadLocalInvoker(pico, getDelegate());
            return (T)proxyFactory.createProxy(invoker, interfaces);
        }


        private Class[] getInterfaces() {
            final Object key = getComponentKey();
            final Class[] interfaces;
            if (key instanceof Class && ((Class<?>)key).isInterface()) {
                interfaces = new Class[]{(Class<?>)key};
            } else {
                final Set allInterfaces = ReflectionUtils.getAllInterfaces(getComponentImplementation());
                interfaces = (Class[])allInterfaces.toArray(new Class[allInterfaces.size()]);
            }
            if (interfaces.length == 0) {
                throw new PicoCompositionException("Can't proxy implementation for "
                        + getComponentImplementation().getName()
                        + ". It does not implement any interfaces.");
            }
            return interfaces;
        }

        public String getDescriptor() {
            return "ThreadLocal";
        }


        final static private class ThreadLocalInvoker implements Invoker {

            private final PicoContainer pico;
            private final ComponentAdapter delegate;

            private ThreadLocalInvoker(final PicoContainer pico, final ComponentAdapter delegate) {
                this.pico = pico;
                this.delegate = delegate;
            }

            public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
                final Object delegatedInstance = delegate.getComponentInstance(pico,null);
                if (method.equals(ReflectionUtils.equals)) { // necessary for JDK 1.3
                    return args[0] != null && args[0].equals(delegatedInstance);
                } else {
                    try {
                        return method.invoke(delegatedInstance, args);
                    } catch (final InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                }
            }
        }
    }
}
