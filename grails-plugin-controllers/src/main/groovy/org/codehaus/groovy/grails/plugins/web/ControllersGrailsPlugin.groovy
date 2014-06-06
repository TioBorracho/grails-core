hceptionResolver
import org.codehaus.groovy.grails.web.filters.HiddenHttpMethodFilter
import org.codehaus.groovy.grails.web.mapping.UrlMappings
import org.codehaus.groovy.grails.web.metaclass.RedirectDynamicMethod
import org.codehaus.groovy.grails.web.multipart.ContentLengthAwareCommonsMultipartResolver
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsControllerUrlMappings
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequestFilter
import org.codehaus.groovy.grails.web.servlet.mvc.RedirectEventListener
import org.codehaus.groovy.grails.web.servlet.mvc.TokenResponseActionResultTransformer
import org.codehaus.groovy.grails.web.servlet.mvc.UrlMappingsInfoHandlerAdapter
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.boot.context.embedded.FilterRegistrationBean
import org.springframework.boot.context.embedded.ServletContextInitializer
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils
import org.springframework.web.filter.CharacterEncodingFilter
import org.springframework.web.filter.DelegatingFilterProxy
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator

import javax.servlet.Servlet
import javax.servlet.ServletContext
import javax.servlet.ServletException

/**
 * Handles the configuration of controllers for Grails.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
class ControllersGrailsPlugin implements ServletContextInitializer{

    def watchedResources = [
        "file:./grails-app/controllers/**/*Controller.groovy",
        "file:./plugins/*/grails-app/controllers/**/*Controller.groovy"]

    def version = GrailsUtil.getGrailsVersion()
    def observe = ['domainClass']
    def dependsOn = [core: version, i18n: version, urlMappings: version]

    def doWithSpring = {
        tokenResponseActionResultTransformer(TokenResponseActionResultTransformer)
        simpleControllerHandlerAdapter(UrlMappingsInfoHandlerAdapter)

        characterEncodingFilter(CharacterEncodingFilter) {
            encoding = application.flatConfig.get('grails.filter.encoding') ?: 'utf-8'
        }
        exceptionHandler(GrailsExceptionResolver) {
            exceptionMappings = ['java.lang.Exception': '/error']
        }

        if (!application.config.grails.disableCommonsMultipart) {
            multipartResolver(ContentLengthAwareCommonsMultipartResolver)
        }
        def handlerInterceptors = springConfig.containsBean("localeChangeInterceptor") ? [ref("localeChangeInterceptor")] : []
        def interceptorsClosure = {
            interceptors = handlerInterceptors
        }
        // allow @Controller annotated beans
        annotationHandlerMapping(RequestMappingHandlerMapping, interceptorsClosure)
        annotationHandlerAdapter(RequestMappingHandlerAdapter)

        viewNameTranslator(DefaultRequestToViewNameTranslator) {
            stripLeadingSlash = false
        }

        def defaultScope = application.config.grails.controllers.defaultScope ?: 'prototype'
        final pluginManager = manager

        instanceControllersApi(ControllersApi, pluginManager) {
            linkGenerator = ref("grailsLinkGenerator")
        }

        for (controller in application.controllerClasses) {
            log.debug "Configuring controller $controller.fullName"
            if (controller.available) {
                "${controller.fullName}"(controller.clazz) { bean ->
                    def beanScope = controller.getPropertyValue("scope") ?: defaultScope
                    bean.scope = beanScope
                    bean.autowire =  "byName"
                    if (beanScope == 'prototype') {
                        bean.beanDefinition.dependencyCheck = AbstractBeanDefinition.DEPENDENCY_CHECK_NONE
                    }
                }
            }
        }
    }

    def doWithDynamicMethods = {ApplicationContext ctx ->

        ControllersApi controllerApi = ctx.getBean("instanceControllersApi", ControllersApi)
        Object gspEnc = application.getFlatConfig().get("grails.views.gsp.encoding")

        if ((gspEnc != null) && (gspEnc.toString().trim().length() > 0)) {
            controllerApi.setGspEncoding(gspEnc.toString())
        }

        def redirectListeners = ctx.getBeansOfType(RedirectEventListener)
        controllerApi.setRedirectListeners(redirectListeners.values())

        Object o = application.getFlatConfig().get(RedirectDynamicMethod.GRAILS_VIEWS_ENABLE_JSESSIONID)
        if (o instanceof Boolean) {
            controllerApi.setUseJessionId(o)
        }

        def enhancer = new MetaClassEnhancer()
        enhancer.addApi(controllerApi)

        for (controller in application.controllerClasses) {
            def controllerClass = controller
            def mc = controllerClass.metaClass
            mc.constructor = {-> ctx.getBean(controllerClass.fullName)}
            if (controllerClass.clazz.getAnnotation(Enhanced)==null) {
                enhancer.enhance mc
            }
            controllerClass.initialize()
        }

        for (GrailsDomainClass domainClass in application.domainClasses) {
            enhanceDomainWithBinding(ctx, domainClass, domainClass.metaClass)
        }
    }

    static void enhanceDomainWithBinding(ApplicationContext ctx, GrailsDomainClass dc, MetaClass mc) {
        if (dc.abstract) {
            return
        }

        def enhancer = new MetaClassEnhancer()
        enhancer.addApi(new ControllersDomainBindingApi())
        enhancer.enhance mc
    }

    def onChange = {event ->
        if (!(event.source instanceof Class)) {
            return
        }

        if (application.isArtefactOfType(DomainClassArtefactHandler.TYPE, event.source)) {
            def dc = application.getDomainClass(event.source.name)
            enhanceDomainWithBinding(event.ctx, dc, GroovySystem.metaClassRegistry.getMetaClass(event.source))
            return
        }

        if (application.isArtefactOfType(ControllerArtefactHandler.TYPE, event.source)) {
            ApplicationContext context = event.ctx
            if (!context) {
                if (log.isDebugEnabled()) {
                    log.debug("Application context not found. Can't reload")
                }
                return
            }

            def defaultScope = application.config.grails.controllers.defaultScope ?: 'prototype'

            def controllerClass = application.addArtefact(ControllerArtefactHandler.TYPE, event.source)
            def beanDefinitions = beans {
                "${controllerClass.fullName}"(controllerClass.clazz) { bean ->
                    def beanScope = controllerClass.getPropertyValue("scope") ?: defaultScope
                    bean.scope = beanScope
                    bean.autowire = "byName"
                    if (beanScope == 'prototype') {
                        bean.beanDefinition.dependencyCheck = AbstractBeanDefinition.DEPENDENCY_CHECK_NONE
                    }
                }
            }
            // now that we have a BeanBuilder calling registerBeans and passing the app ctx will
            // register the necessary beans with the given app ctx
            beanDefinitions.registerBeans(event.ctx)
            controllerClass.initialize()
        }
    }

    @Override
    @CompileStatic
    void onStartup(ServletContext servletContext) throws ServletException {
        def application = GrailsWebUtil.lookupApplication(servletContext)
        def proxy = new DelegatingFilterProxy("characterEncodingFilter")
        proxy.targetFilterLifecycle = true
        FilterRegistrationBean charEncoder = new FilterRegistrationBean(proxy)

        def catchAllMapping = ['/*']
        charEncoder.urlPatterns = catchAllMapping
        charEncoder.onStartup(servletContext)

        if (!application.config.grails.disableHiddenHttpMethodFilter) {
            def hiddenHttpFilter = new FilterRegistrationBean(new HiddenHttpMethodFilter())
            hiddenHttpFilter.urlPatterns = catchAllMapping
            hiddenHttpFilter.onStartup(servletContext)
        }

        def webRequestFilter = new FilterRegistrationBean(new GrailsWebRequestFilter())

        // TODO: Add ERROR dispatcher type
        webRequestFilter.urlPatterns = catchAllMapping
        webRequestFilter.onStartup(servletContext)

        if(application != null) {
            def dbConsoleEnabled = application?.flatConfig?.get('grails.dbconsole.enabled')

            if (!(dbConsoleEnabled instanceof Boolean)) {
                dbConsoleEnabled = Environment.current == Environment.DEVELOPMENT
            }

            if(!dbConsoleEnabled) return


            def classLoader = Thread.currentThread().contextClassLoader
            if(ClassUtils.isPresent('org.h2.server.web.WebServlet', classLoader)) {

                String urlPattern = (application?.flatConfig?.get('grails.dbconsole.urlRoot') ?: "/dbconsole").toString() + '/*'
                ServletRegistrationBean dbConsole = new ServletRegistrationBean(classLoader.loadClass('org.h2.server.web.WebServlet').newInstance() as Servlet, urlPattern)
                dbConsole.loadOnStartup = 2
                dbConsole.initParameters = ['-webAllowOthers':'true']
                dbConsole.onStartup(servletContext)
            }

        }

    }


}
