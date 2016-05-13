package au.org.ala.profile.hub

import static au.org.ala.profile.hub.Utils.enc
import au.org.ala.ws.service.WebService
import groovy.json.JsonSlurper
import org.springframework.web.multipart.MultipartFile

import java.nio.file.Files
import java.nio.file.Path

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;


class BiocacheService {

    static final int DEFAULT_BIOCACHE_PAGE_SIZE = 50 // the biocache default is only 10, we want more than that

    WebService webService
    def grailsApplication


    def retrieveImagesPaged(String searchIdentifier, Map opus, String pageSize, String startIndex) {
        if (!searchIdentifier) {
            return [:]
        }

        String imagesQuery = constructQueryString(searchIdentifier, opus)
        log.debug("Fetching images for ${searchIdentifier} using query ${imagesQuery}")
        String biocacheImageSearchUrl = "${grailsApplication.config.image.search.url}${grailsApplication.config.biocache.occurrence.search.path}"

        def numberOfImages = webService.get("${biocacheImageSearchUrl}?q=${imagesQuery}&fq=multimedia:Image&format=json&im=true&pageSize=0")
        def response = webService.get("${biocacheImageSearchUrl}?q=${imagesQuery}&fq=multimedia:Image&format=json&im=true&pageSize=${pageSize}&startIndex=${startIndex}")
        return response
    }

    def retrieveImages(String searchIdentifier, Map opus) {
        if (!searchIdentifier) {
            return [:]
        }

        String imagesQuery = constructQueryString(searchIdentifier, opus)
        log.debug("Fetching images for ${searchIdentifier} using query ${imagesQuery}")
        String biocacheImageSearchUrl = "${grailsApplication.config.image.search.url}${grailsApplication.config.biocache.occurrence.search.path}"

        log.debug("Image query = ${imagesQuery}")

        webService.get("${biocacheImageSearchUrl}?q=${imagesQuery}&fq=multimedia:Image&format=json&im=true&pageSize=${DEFAULT_BIOCACHE_PAGE_SIZE}")
    }

    String constructQueryString(String searchIdentifier, Map opus) {
        String query
        if (opus?.dataResourceConfig?.imageResourceOption) {
            Map config = opus.dataResourceConfig
            switch (config.imageResourceOption as DataResourceOption) {
                case DataResourceOption.ALL:
                    query = "${searchIdentifier}"
                    break
                case DataResourceOption.NONE:
                    query = "${searchIdentifier} AND data_resource_uid:${opus.dataResourceUid}"
                    break
                case DataResourceOption.HUBS:
                    query = "${searchIdentifier} AND (data_resource_uid:${opus.dataResourceUid} OR data_hub_uid:${config.imageSources?.join(" OR data_hub_uid:")})"
                    break
                case DataResourceOption.RESOURCES:
                    query = "${searchIdentifier} AND (data_resource_uid:${opus.dataResourceUid} OR data_resource_uid:${config.imageSources?.join(" OR data_resource_uid:")})"
                    break
                default:
                    query = "${searchIdentifier}"
            }
        } else if (opus?.dataResourceUid) {
            query = "${searchIdentifier} AND data_resource_uid:${opus.dataResourceUid}"
        } else {
            query = "${searchIdentifier}"
        }

        enc(query)
    }

    String copyFileForUpload(String imageId, def file, File tempDir) {
        String filename = ''
        //Images sent directly to central service on upload
        if (file instanceof MultipartFile) {
            String extension = file.originalFilename.substring(file.originalFilename.lastIndexOf("."))
            filename = "${imageId}${extension}"
            file.transferTo(new File(tempDir, "/${filename}"))
            //Private and staged images are stored relative to this application, and may be sent to central image service later
        } else if (file instanceof File) {
            filename = file.getName()
            //Make defensive copy in case fails
            File fileCopy = new File(tempDir, "/${filename}")
            Path target = fileCopy.toPath()
            Path source = file.toPath()
            Files.copy(source, target, REPLACE_EXISTING, COPY_ATTRIBUTES)
        }
        filename
    }

    /**
     * Upload works by sending metadata to a central image service which then comes back to fetch the image based
     *  on information in this metadata??
     *
     * @param opusId - collection
     * @param profileId - the item within the collection
     * @param dataResourceId
     * @param file - can be a File from disk or a MultipartFile streamed directly
     * @param metadata - information about the image
     * @return Map response from the webservice including statusCode and resp
     */
    def uploadImage(String opusId, String profileId, String dataResourceId, file, Map metadata) {
        String imageId = UUID.randomUUID()
        File tempDir = new File("${grailsApplication.config.temp.file.location}")
        String filename = copyFileForUpload(imageId, file, tempDir)

        metadata.multimedia[0].identifier = "${grailsApplication.config.grails.serverURL}/opus/${enc(opusId)}/profile/${enc(profileId)}/file/${enc(filename)}"

        // make sure the spelling of licenSe is US to match the Darwin Core standard
        if (metadata.multimedia[0].containsKey("licence")) {
            metadata.multimedia[0].license = metadata.licence
            metadata.multimedia[0].remove("licence")
        }

        log.debug("Uploading image ${metadata.multimedia[0].identifier} to ${grailsApplication.config.image.upload.url}${dataResourceId} with metadata ${metadata}")

        // TODO: REMOVE THIS - IT IS FOR TESTING ONLY!!!!
        String hostname = InetAddress.getLocalHost().hostName
        if ((hostname != 'nci-profiles-prod' || hostname == "nci-profiles" || hostname == "nci-profiles-dev" || hostname == "maccy-bm")
                && grailsApplication.config.test.collectory.drid.mappings) {
            Map drIdMapping = new JsonSlurper().parseText(grailsApplication.config.test.collectory.drid.mappings)

            log.debug "Mapping prod drId ${dataResourceId} to collectory-dev drId ${drIdMapping[dataResourceId]}"
            dataResourceId = drIdMapping[dataResourceId]
        }

        webService.post("${grailsApplication.config.image.upload.url}${dataResourceId}?apiKey=${grailsApplication.config.image.upload.apiKey}", metadata)
    }

    def lookupSpecimen(String specimenId) {
        webService.get("${grailsApplication.config.biocache.base.url}/ws/occurrences/${specimenId}")
    }
}
