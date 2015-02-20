package au.org.ala.profile.hub

import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification

@TestFor(BiocacheService)
@TestMixin(GrailsUnitTestMixin)
class BiocacheServiceSpec extends Specification {

    BiocacheService service
    WebService webService

    def setup() {
        grailsApplication.config.biocache.occurrence.search.path = "/occurrence/search.json"
        grailsApplication.config.biocache.base.url = "http://biocache.base"

        webService = Mock(WebService)
        service = new BiocacheService()
        service.grailsApplication = grailsApplication
        service.init()
        service.webService = webService

        mockCodec(org.codehaus.groovy.grails.plugins.codecs.URLCodec)
    }

    def "retrieveImages() should construct the correct Biocache Occurrance Search URL"() {
        setup:
        String expectedUrl = "http://biocache.base/occurrence/search.json?q=searchId+AND+%28data_resource_uid%3Aid1+OR+data_resource_uid%3Aid2+OR+data_resource_uid%3Aid3%29&fq=multimedia:Image&format=json"

        when:
        service.retrieveImages("searchId", "id1,id2,id3")

        then:
        1 * webService.get(expectedUrl)
    }

}