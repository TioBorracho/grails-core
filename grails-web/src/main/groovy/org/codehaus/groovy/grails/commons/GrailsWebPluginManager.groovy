package org.codehaus.groovy.grails.commons

import groovy.transform.CompileStatic
import org.codehaus.groovy.grails.plugins.DefaultGrailsPluginManager
import org.codehaus.groovy.grails.plugins.GrailsPlugin
import org.springframework.boot.context.embedded.ServletContextInitializer
import org.springframework.core.io.Resource

import javax.servlet.ServletContext

/**
 * @author Graeme Rocher
 */
@CompileStatic
class GrailsWebPluginManager extends DefaultGrailsPluginManager{

    public static final String SERVLET_CONTEXT_INIT_METHOD = 'doWithServletContext'

    GrailsWebPluginManager(String resourcePath, GrailsApplication application) {
        super(resourcePath, application)
    }

    GrailsWebPluginManager(String[] pluginResources, GrailsApplication application) {
        super(pluginResources, application)
    }

    GrailsWebPluginManager(Class<?>[] plugins, GrailsApplication application) {
        super(plugins, application)
    }

    GrailsWebPluginManager(Resource[] pluginFiles, GrailsApplication application) {
        super(pluginFiles, application)
    }

    void doWithServletContext(ServletContext servletContext) {
        for(GrailsPlugin plugin in allPlugins) {
            def instance = plugin.instance
            if(instance instanceof ServletContextInitializer) {
                ((ServletContextInitializer)instance).onStartup(servletContext)
            }
        }
    }
}
