package au.org.ala.profile.hub

import org.apache.http.entity.ContentType
import org.codehaus.groovy.grails.web.util.WebUtils

/**
 * XXX Used to add florulaOverrideId to all GET requests if the cookie exists
 */
class WebServiceWrapperService {

    static transactional = false
    static final FLORULA_OVERRIDE_PARAM = 'florulaOverrideId'
    static final FLORULA_COOKIE = 'phf'

    def webService
    def authService
    FlorulaCookieService florulaCookieService

    Map get(String url, Map params = [:], ContentType contentType = ContentType.APPLICATION_JSON, boolean includeApiKey = true, boolean includeUser = true, Map customHeaders = [:]) {
        // only add it if there is no current user
        if (!authService.userId) {
            def florulaIds = extractFlorulaIds()
            if (florulaIds) {
                florulaIds.each {
                    params += [(FLORULA_OVERRIDE_PARAM + '-' + it.key): it.value]
                }
            }
        }
        return webService.get(url, params, contentType, includeApiKey, includeUser, customHeaders)
    }

    Map<String, String> extractFlorulaIds() {
        def wr = WebUtils.retrieveGrailsWebRequest()
        def request = wr.request
        return florulaCookieService.getCookieValue(request)
    }
}