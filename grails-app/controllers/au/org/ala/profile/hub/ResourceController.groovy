package au.org.ala.profile.hub

import au.org.ala.profile.security.Secured
import au.org.ala.web.AuthService
import au.org.ala.web.CASRoles
import grails.converters.JSON
import org.apache.commons.io.FilenameUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.json.JSONArray
import org.codehaus.groovy.grails.web.json.JSONObject

import static org.apache.http.HttpStatus.SC_BAD_REQUEST
import static au.org.ala.profile.security.Role.ROLE_PROFILE_EDITOR

class ResourceController extends BaseController {

    static final String ADMIN_ROLE = 'ROLE_ADMIN'

    static final String DOCUMENT_SYSTEM_ID = 'profiles'

    GrailsApplication grailsApplication
    DocumentResourceService  documentResourceService
    AuthService authService

    def  index() {
        log.debug("Resource Index")
     render "It works"
    }

    def list() {
        String parentId = (params.parentId?: '' )

        Map searchParams = [projectId:parentId]
        Map result = documentResourceService.search(searchParams)
        [documents:modelAsJavascript(result.documents), admin:isUserAdmin()]
    }

    /**
     * Check user is an admin
     * @return user admin status
     */
    boolean isUserAdmin(){
        authService.userInRole(ADMIN_ROLE) || authService.userInRole(CASRoles.ROLE_ADMIN)
    }

    String modelAsJavascript(def model) {

        if (!(model instanceof JSONObject) && !(model instanceof JSONArray)) {
            model = model as JSON

        }
        def json = (model?:[:] as JSON)
        def modelJson = json.toString()
        modelJson.encodeAsJavaScript()
    }

    /**
     * Proxies to the ecodata document controller.
     * @param documentId the documentId of the document to update (if not supplied, a create operation will be assumed).
     * @return the result of the update.
     */
    def documentUpdate(String documentId) {

        log.debug("In documentUpdate for ID: ${documentId}")

        String opusId = params.opusId
        String profileId = params.profileId

        log.debug("opusId: ${opusId}")
        log.debug("profileId: ${profileId}")


        if (request.respondsTo('getFile')) {
            def f = request.getFile('files')
            def originalFilename = f.getOriginalFilename()
            if(originalFilename){
                def extension = FilenameUtils.getExtension(originalFilename)?.toLowerCase()
                if (extension && !grailsApplication.config.upload.extensions.blacklist.contains(extension)){

                    log.debug("Document: ${params.document}")

                    Map document = JSON.parse(params.document)
                    document.systemId = DOCUMENT_SYSTEM_ID

                    def result = documentResourceService .updateDocument(document, f)

                    // This is returned to the browswer as a text response due to workaround the warning
                    // displayed by IE8/9 when JSON is returned from an iframe submit.
                    response.setContentType('text/plain;charset=UTF8')
                    render (result.resp as JSON).toString();
                } else {
                    response.setStatus(SC_BAD_REQUEST)
                    //flag error for extension
                    def error = [error: "Files with the extension '.${extension}' are not permitted.",
                                 statusCode: "400",
                                 detail: "Files with the extension ${extension} are not permitted."] as JSON
                    response.setContentType('text/plain;charset=UTF8')
                    render error.toString()
                }
            } else {
                //flag error for extension
                response.setStatus(SC_BAD_REQUEST)
                def error = [error: "Unable to retrieve the file name.",
                             statusCode: "400",
                             detail: "Unable to retrieve the file name."] as JSON
                response.setContentType('text/plain;charset=UTF8')
                render error.toString()
            }
        } else {
            // This is returned to the browswer as a text response due to workaround the warning
            // displayed by IE8/9 when JSON is returned from an iframe submit.

            log.debug("Document: ${params.document}")

            Map document = JSON.parse(params.document)
            document.systemId = DOCUMENT_SYSTEM_ID

            def result = documentResourceService.updateDocument(opusId, profileId, document)

            log.debug("Result: ${result}")
            response.setContentType('text/plain;charset=UTF8')
            def resultAsText = (result as JSON).toString()
            render resultAsText
        }
    }

    /**
     * Proxies to the eco data document controller to delete the document with the supplied documentId.
     * @param documentId the documentId of the document to delete.
     * @return the result of the deletion.
     */
    @Secured(role = ROLE_PROFILE_EDITOR)
    def documentDelete(String documentId) {
        log.debug("In documentUpdate for ID: ${documentId}")

        String opusId = params.opusId
        String profileId = params.profileId

        log.debug("opusId: ${opusId}")
        log.debug("profileId: ${profileId}")

        def result = documentResourceService.delete(opusId, profileId, documentId)
        render status: result.statusCode
    }
}
