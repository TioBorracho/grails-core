/* Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.commons;

import grails.util.Holders;
import grails.util.Metadata;
import groovy.lang.GroovyObjectSupport;
import groovy.util.ConfigObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.codehaus.groovy.grails.commons.cfg.ConfigurationHelper;
import org.codehaus.groovy.grails.plugins.GrailsPluginManager;
import org.codehaus.groovy.grails.plugins.support.aware.GrailsConfigurationAware;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;

abstract class AbstractGrailsApplication extends GroovyObjectSupport implements GrailsApplication, ApplicationContextAware, BeanClassLoaderAware {
    protected ClassLoader classLoader;
    protected ConfigObject config;
    @SuppressWarnings("rawtypes")
    protected Map flatConfig = Collections.emptyMap();
    protected ApplicationContext parentContext;
    protected ApplicationContext mainContext;
    protected Metadata applicationMeta = Metadata.getCurrent();
    
    public AbstractGrailsApplication() {
        ConfigurationHelper.clearCachedConfig(this);
    }
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.parentContext = applicationContext;
    }

    @Override
    public Metadata getMetadata() {
        return applicationMeta;
    }

    @Override
    public boolean isWarDeployed() {
        return getMetadata().isWarDeployed();
    }
    
    public ConfigObject getConfig() {
        return config;
    }

    public void setConfig(ConfigObject config) {
        this.config = config;
        Holders.setConfig(config);
        updateFlatConfig();
    }

    @SuppressWarnings("rawtypes")
    public void updateFlatConfig() {
        if (config == null) {
            flatConfig = new LinkedHashMap();
        } else {
            flatConfig = config.flatten(new LinkedHashMap());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getFlatConfig() {
        return flatConfig;
    }

    @Override
    public void configChanged() {
        updateFlatConfig();
        final ArtefactHandler[] handlers = getArtefactHandlers();
        if(handlers != null) {
            for (ArtefactHandler handler : handlers) {
                if (handler instanceof GrailsConfigurationAware) {
                    ((GrailsConfigurationAware)handler).setConfiguration(config);
                }
            }
        }
    }
    
    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
    
    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Class getClassForName(String className) {
        return ClassUtils.resolveClassName(className, getClassLoader());
    }    
    
    public ApplicationContext getMainContext() {
        return mainContext;
    }

    public void setMainContext(ApplicationContext context) {
        mainContext = context;
        if (mainContext == null) {
            return;
        }
        if (!mainContext.containsBean("pluginManager")) {
            return;
        }
        if (mainContext instanceof ConfigurableApplicationContext) {
            if (!((ConfigurableApplicationContext) mainContext).isActive()) {
                // unrefreshed context - plugin manager will get the context from GrailsRuntimeConfiguration
                return;
            }
        }
        mainContext.getBean("pluginManager", GrailsPluginManager.class).setApplicationContext(context);
    }

    @Override
    public ApplicationContext getParentContext() {
        return parentContext;
    }
}
