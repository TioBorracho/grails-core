/*
 * Copyright 2004-2005 Graeme Rocher
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
package org.codehaus.groovy.grails.web.mapping;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import javax.servlet.ServletContext;

import org.codehaus.groovy.grails.validation.ConstrainedProperty;

/**
 * Abstract UrlMapping implementation that provides common basic functionality.
 *
 * @author Graeme Rocher
 * @since 0.5.5
 */
public abstract class AbstractUrlMapping implements UrlMapping {

    protected final ConstrainedProperty[] constraints;
    protected Object controllerName;
    protected Object actionName;
    protected Object namespace;
    protected Object pluginName;
    protected Object viewName;
    protected Object forwardURI;
    protected Object redirectInfo;
    protected ServletContext servletContext;
    @SuppressWarnings("rawtypes")
    protected Map parameterValues = Collections.EMPTY_MAP;
    protected boolean parseRequest;
    protected String mappingName;
    protected String httpMethod = ANY_HTTP_METHOD;
    protected String version = ANY_VERSION;

    /**
     * Base constructor required to construct a UrlMapping instance
     *
     * @param controllerName The name of the controller
     * @param actionName The name of the action
     * @param constraints Any constraints that apply to the mapping
     * @param servletContext
     */
    public AbstractUrlMapping(Object redirectInfo, Object controllerName, Object actionName, Object namespace, Object pluginName, Object viewName, ConstrainedProperty[] constraints, ServletContext servletContext) {
        this.controllerName = controllerName;
        this.actionName = actionName;
        this.namespace = namespace;
        this.pluginName = pluginName;
        this.constraints = constraints;
        this.viewName = viewName;
        this.servletContext = servletContext;
        this.redirectInfo = redirectInfo;
    }

    protected AbstractUrlMapping(Object viewName, ConstrainedProperty[] constraints, ServletContext servletContext) {
        this.viewName = viewName;
        this.constraints = constraints;
        this.servletContext = servletContext;
    }

    protected AbstractUrlMapping(URI uri, ConstrainedProperty[] constraints, ServletContext servletContext) {
        this.forwardURI = uri;
        this.constraints = constraints;
        this.servletContext = servletContext;
    }

    @Override
    public String getHttpMethod() {
        return httpMethod;
    }

    @Override
    public String getVersion() {
        return version;
    }

    /**
     * @see UrlMapping#getConstraints()
     */
    public ConstrainedProperty[] getConstraints() {
        return constraints;
    }

    /**
     * @see UrlMapping#getControllerName()
     */
    public Object getControllerName() {
        return controllerName;
    }

    /**
     * @see org.codehaus.groovy.grails.web.mapping.UrlMapping#getActionName()
     */
    public Object getActionName() {
        return actionName;
    }

    public Object getPluginName() {
        return pluginName;
    }

    public Object getNamespace() {
        return namespace;
    }

    /**
     * @see org.codehaus.groovy.grails.web.mapping.UrlMapping#getViewName()
     */
    public Object getViewName() {
        return viewName;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    public void setParameterValues(Map parameterValues) {
        this.parameterValues = Collections.unmodifiableMap(parameterValues);
    }

    public void setParseRequest(boolean shouldParse) {
        this.parseRequest = shouldParse;
    }

    public String getMappingName() {
        return mappingName;
    }

    public void setMappingName(String name) {
        mappingName = name;
    }

    public boolean hasRuntimeVariable(String name) {
        if (constraints != null) {
            for (int i = 0; i < constraints.length; i++) {
                ConstrainedProperty cp = constraints[i];
                if (cp.getPropertyName().equals(name)) return true;
            }
        }
        return false;
    }

    public Object getRedirectInfo() {
        return redirectInfo;
    }
}
