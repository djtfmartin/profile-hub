package au.org.ala.profile.hub

import groovy.json.JsonSlurper

class ProfileController {

    def index() {}

    def edit(){
        def model = buildProfile(params.uuid)
        //need CAS check here
        model.put("edit", true)
        render(view: "show", model: model)
    }

    def save(){

    }

    def show(){
        buildProfile(params.uuid)
    }

    private def buildProfile(String uuid){

        println("getProfile " + uuid )

        def js = new JsonSlurper()

        def profile = js.parseText(new URL("http://localhost:8081/profile-service/profile/" + URLEncoder.encode(uuid, "UTF-8")).text)

        def opus = js.parseText(new URL("http://localhost:8081/profile-service/opus/${profile.opusId}").text)

        def query = ""

        if(profile.guid){
            query = "lsid:" + profile.guid
        } else {
            query = profile.scientificName
        }

        def occurrenceQuery = query

        if(opus.recordSources){
            occurrenceQuery = query + " AND (data_resource_uid:" + opus.recordSources.join(" OR data_resource_uid:") + ")"
        }

        def imagesQuery = query
        if(opus.imageSources){
            imagesQuery = query + " AND (data_resource_uid:" + opus.imageSources.join(" OR data_resource_uid:") + ")"
        }

        //WMS URL
        def listsURL = "http://lists.ala.org.au/ws/species/${profile.guid}"
        [
                occurrenceQuery: occurrenceQuery,
                imagesQuery: imagesQuery,
                opus: opus,
                profile: profile,
                lists: []
        ]
    }
}
