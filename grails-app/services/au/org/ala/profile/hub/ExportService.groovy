package au.org.ala.profile.hub

import au.org.ala.profile.hub.reports.JasperExportFormat
import au.org.ala.profile.hub.reports.JasperReportDef
import au.org.ala.profile.hub.util.HubConstants
import au.org.ala.ws.service.WebService
import grails.converters.JSON
import grails.transaction.NotTransactional
import net.glxn.qrgen.QRCode
import net.glxn.qrgen.image.ImageType
import net.sf.jasperreports.engine.data.JsonDataSource
import net.sf.jasperreports.engine.util.SimpleFileResolver
import org.apache.commons.io.IOUtils
import org.springframework.scheduling.annotation.Async
import org.springframework.web.context.request.RequestContextHolder

import java.util.concurrent.ConcurrentLinkedQueue

import static groovyx.gpars.GParsPool.withPool
import static org.owasp.html.Sanitizers.BLOCKS
import static org.owasp.html.Sanitizers.FORMATTING
import static org.owasp.html.Sanitizers.IMAGES
import static org.owasp.html.Sanitizers.LINKS
import static org.owasp.html.Sanitizers.STYLES
import static org.owasp.html.Sanitizers.TABLES

class ExportService {

    private static final int THREAD_COUNT = 10
    static transactional = false
    static final String LOCAL_IMAGE_THUMBNAIL_REGEX = "http.*?/image/thumbnail/(${Utils.UUID_REGEX_PATTERN}).*"

    ProfileService profileService
    BiocacheService biocacheService
    ImageService imageService
    WebService webService
    EmailService emailService
    NslService nslService
    KeybaseService keybaseService
    JasperService jasperService
    def grailsApplication

    Map statusRegions = [
            ""                            : "IUCN",
            "IUCN"                        : "IUCN",
            "Australia"                   : "AU",
            "Australian Capital Territory": "ACT",
            "New South Wales"             : "NSW",
            "Northern Territory"          : "NT",
            "Queensland"                  : "QLD",
            "South Australia"             : "SA",
            "Tasmania"                    : "TAS",
            "Victoria"                    : "VIC",
            "Western Australia"           : "WA"
    ]


    @Async
    void createPdfAsych(Map params, boolean latest = false) {
        try {
            byte[] pdf = createPdf(params, latest)

            String filename = UUID.randomUUID()
            File f = new File("${grailsApplication.config.temp.file.location}/${filename}.pdf")
            f << pdf
            String url = "${grailsApplication.config.grails.serverURL}/opus/${params.opusId}/profile/${params.profileId}/file/${filename}.pdf"
            emailService.sendEmail(params.email, "${params.opusTitle}<no-reply@ala.org.au>", "Your file is ready to download",
                    """<html><body>
                        <p>The PDF you requested from ${params.opusTitle} can now be downloaded from <a href='${url}'>this url</a>.</p>
                        <p>Please note that this file will only remain on the server for a few days.</p>
                        <p>This is an automated email. Please do not reply.</p>
                        </body></html>""")
        } catch (Exception e) {
            log.error("Failed to generated PDF", e)
            emailService.sendEmail(params.email, "${params.opusTitle}<no-reply@ala.org.au>", "PDF generation failed", "We are sorry to inform you that an error occurred while generating the PDF you requested. Please try again.")
        }
    }

    @NotTransactional
    byte[] createPdf(Map params, boolean latest = false) {

        // Create curated report model
        Map curatedModel = getCurateReportModel(params, latest)

        // Transform curated model to JSON format input stream
        InputStream inputStream = IOUtils.toInputStream((curatedModel as JSON).toString(), "UTF-8")

        // Runtime classpath directory where the reports are available
        File reportsDir = new File(grailsApplication.mainContext.getResource('classpath:reports/profiles/PROFILES.jrxml').URL.file.replaceFirst("/[\\w_]+.jrxml\$", ""))

        // Generate QR code with profile URL
        InputStream qrCodeInputStream = new ByteArrayInputStream(QRCode.from(curatedModel?.colophon?.profileLink).withSize(150, 150).to(ImageType.JPG).stream().toByteArray())

        // Generate report and return byte array


        JasperReportDef reportDef = new JasperReportDef(
                name: 'profiles/PROFILES.jrxml',
                fileFormat: JasperExportFormat.PDF_FORMAT,
                dataSource: new JsonDataSource(inputStream),
                parameters: [
                        'PROFILES_REPORT_OPTIONS': curatedModel.options,
                        'REPORT_FILE_RESOLVER'   : new SimpleFileResolver(reportsDir),
                        'QR_CODE'                : qrCodeInputStream
                ]
        )

        return jasperService.generateReport(reportDef).toByteArray()
    }

    /**
     * Retrieve profile/s data in multiple threads and other required info. Put them all together in a the required data structure for the report template ingestion
     * @param params
     * @param latest (optional) = defaults to false
     * @return
     */
    private Map getCurateReportModel(Map params, boolean latest = false) {
        def curatedModel = [
                options : params,
                profiles: [] as ConcurrentLinkedQueue
        ]

        def opus = webService.get("${grailsApplication.config.profile.service.url}/opus/${URLEncoder.encode(params.opusId, "UTF-8")}")?.resp
        curatedModel.profiles << loadProfileData(params.profileId as String, opus, params, latest)

        if (params.children && curatedModel.profiles[0].profile.rank) {
            def children = profileService.findByNameAndTaxonLevel(params.opusId, curatedModel.profiles[0].profile.rank, curatedModel.profiles[0].profile.scientificName, "99999", "0", "taxonomy", false)?.resp

            // By default, the RequestAttributes thread local used by Grails/Spring is not inheritable, so new threads
            // will not have access to the request context when calling web services. This line works around this issue
            // by resetting the request attributes with the inheritable flag set to true, meaning spawned threads will
            // inherit the state.
            RequestContextHolder.setRequestAttributes(RequestContextHolder.getRequestAttributes(), true)

            withPool(THREAD_COUNT) {
                children.eachParallel {
                    if (it.profileId != params.profileId && it.scientificName != params.profileId) {
                        Map profile = loadProfileData(it.profileId, opus, params, latest)
                        profile.taxonomicOrder = it.taxonomicOrder
                        curatedModel.profiles << profile
                    }
                }
            }

            // the findByNameAndTaxonLevel operation returns the profiles in the correct taxonomic order (genera inside
            // families, species inside genera, etc), so ensure that the order is correct after the multithreaded processing
            curatedModel.profiles = curatedModel.profiles.sort { it.taxonomicOrder }
        }

        curatedModel.options << [
                allowFineGrainedAttribution: opus.allowFineGrainedAttribution,
                displayToc                 : curatedModel.profiles?.size() > 1
        ]

        def firstProfile = curatedModel.profiles[0]?.profile

        // Generate  citation
        String citation = null
        if (opus.citationHtml) {
            Date now = new Date()
            citation = "${firstProfile?.authorship} (${now[Calendar.YEAR]}) ${firstProfile?.fullName}. In: ${stripTextFromNonFormattingHtmlTags(opus.citationHtml)}. ${grailsApplication.config.grails.serverURL}/opus/${opus.uuid}. ${now.format('dd MMMM yyyy - hh:mm')}"
        }

        curatedModel << [
                cover   : [
                        title       : opus.title,
                        subtitle    : firstProfile?.fullName ?: firstProfile?.scientificName,
                        logo        : opus.logoUrl,
                        banner      : opus.bannerUrl,
                        primaryImage: firstProfile?.primaryImage ?: (firstProfile?.images?.size() > 0 ? firstProfile?.images[0].leftImage.largeImageUrl : '')
                ],
                colophon: [
                        collectionCopyright: "&copy; ${opus.copyrightText}",
                        genericCopyright   : HubConstants.PDF_COPYRIGHT_TEXT,
                        logo               : opus.logoUrl,
                        profileLink        : "${grailsApplication.config.grails.serverURL}/opus/${opus.uuid}/profile/${firstProfile?.uuid}",
                        citation           : citation,
                        version            : firstProfile.version ?: null
                ]
        ]

        return curatedModel
    }

    /**
     * Custom ETL process to retrieve and format the profile data required to render the report
     * @param profileId
     * @param opus
     * @param params
     * @param latest (optional) = defaults to false
     * @return
     */
    private Map loadProfileData(String profileId, opus, Map params, boolean latest = false) {

        Map model = [:]
        model.profile = webService.get("${grailsApplication.config.profile.service.url}/opus/${opus.uuid}/profile/${URLEncoder.encode(profileId, "UTF-8")}?latest=${false}")?.resp

        if (params.taxonomy || params.conservation) {
            model.profile.speciesProfile = profileService.getSpeciesProfile(model.profile.guid)?.resp

            model.profile.speciesProfile?.conservationStatuses?.each {
                it.colour = getColourForStatus(it.status)
                it.regionAbbrev = statusRegions[it.region]
            }

            model.profile.classifications = profileService.getClassification(opus.uuid, params.profileId, model.profile.guid)?.resp
            if (model.profile.classifications && model.profile.taxonomyTree) {
                model.profile.classifications.add(0, [rank: "Source", scientificName: model.profile.taxonomyTree])
            }
        }

        if (params.key) {
            model.profile.bracketedKey = keybaseService.getPrintableBracketedKey(opus.keybaseProjectId, model.profile.scientificName)
        }

        if (params.specimens) {
            model.profile.specimens = model.profile.specimenIds?.collect {
                def spec = biocacheService.lookupSpecimen(it)?.resp
                [
                        institutionName: spec?.processed?.attribution?.institutionName,
                        collectionName : spec?.processed?.attribution?.collectionName,
                        catalogNumber  : spec?.raw?.occurrence?.catalogNumber,
                ]
            }
        }

        // Retrieve image and get primary image. This has to be done always, regardless of the user choosing to display the images section or not
        String searchIdentifier = model.profile.guid ? "lsid:" + model.profile.guid : model.profile.scientificName

        List<String> imageSources = opus.imageSources ?: []
        imageSources << opus.dataResourceUid

        // incrementing number to be displayed against all images in the report (e.g. Fig 1,...)
        int figureNumber = 1

        model.profile.images = imageService.retrieveImages(opus.uuid, profileId, latest, imageSources.join(","), searchIdentifier, true)?.resp
        List<Map> images = model.profile.images

        def replaceTitleWithOptionalCaption = { Map m -> m?.metadata?.title = m?.caption ? m?.caption : m?.metadata?.title }
        images?.each {
            replaceTitleWithOptionalCaption(it)
        }

        // Format profile attributes text
        if (params.attributes) {
            model.profile.attributes.each { attribute ->
                attribute.text = convertTagsForJasper(sanitizeHtml(formatAttributeText(attribute.text, attribute.title)))
                List<Map> attributeImages = extractImagesFromAttributeText(attribute.text, images)

                Map pairs = groupImagesIntoPairs(model.profile.scientificName, attributeImages, figureNumber)
                attribute.images = pairs.imagePairs
                figureNumber = pairs.figureNumber
            }
        }

        model.profile.primaryImage = images.find { it.imageId == model.profile.primaryImage }

        model.profile.images = groupImagesIntoPairs(model.profile.scientificName, images, figureNumber).imagePairs

        // Retrieve and format profile statuses
        if (params.status) {
            model.profile.status = []
            profileService.getFeatureLists(opus.uuid, model.profile.uuid)?.each { list ->
                list.items.each { item ->
                    model.profile.status << item
                }
            }
            model.profile.status.each { singleStatus ->
                singleStatus.key = formatStatusText(singleStatus.key)
                singleStatus.value = formatStatusText(singleStatus.value)
            }
        }

        // Retrieve occurrences-map image url
        String occurrenceQuery = model.profile.occurrenceQuery
        model.profile.mapImageUrl = createMapImageUrl(opus, occurrenceQuery)
        model.profile.occurrencesUrl = createOccurrencesUrl(opus, occurrenceQuery)

        // Don't make them a String if you want the groovy truth to work (Check Bootstrap.groovy for null values workaround)
        def nslNameIdentifier = model.profile.nslNameIdentifier
        def nslNomenclatureIdentifier = model.profile.nslNomenclatureIdentifier
        // Format nomenclature references
        if (params.nomenclature && nslNameIdentifier && nslNomenclatureIdentifier) {
            model.profile.nomenclature = nslService.getConcept(nslNameIdentifier, nslNomenclatureIdentifier)

            if (model.profile.nomenclature) {
                model.profile.nomenclature.details.each { detail ->
                    detail.text = stripTextFromNonFormattingHtmlTags(detail.text)
                }
            }
        }

        // Filter authors and contributors
        model.profile.acknowledgements = model.profile.authorship?.findAll { it.category != 'Author' }
        model.profile.authorship = model.profile.authorship?.findAll { it.category == 'Author' }.collect {
            it.text
        }.join(", ")

        // Format creators and editors
        model.profile.attributes.each { attribute ->
            attribute.creators = attribute?.creators && opus.allowFineGrainedAttribution ? attribute.creators.toArray().join(', ') : ''
            attribute.editors = attribute?.editors && opus.allowFineGrainedAttribution ? attribute.editors.toArray().join(', ') : ''
        }

        // Format conservation status
        if (params.conservation && model.profile.speciesProfile?.conservationStatuses) {
            model.profile.hasConservationStatus = true
            model.profile.speciesProfile.conservationStatuses = model.profile.speciesProfile?.conservationStatuses.sort {
                it.region
            }
        } else {
            model.profile.hasConservationStatus = false
        }

        // Calculate version number
        if (params.printVersion) {
            model.profile.version = model.profile.publications?.size() > 0 ? model.profile.publications.collect {
                (it.version ?: 1) as Integer
            }.max() + 1 : 1
        }

        return model
    }

    /**
     *
     * @param text
     * @param tiltle
     * @return
     */
    static String formatAttributeText(String text, String title) {
        if (text.startsWith('<p>')) {
            text = text.replaceFirst('<p>', "<p><b>${title}:</b> ")
        } else if (text.startsWith('<')) {
            text = "<p><b>${title}:</b></p>${text}"
        } else {
            text = "<b>${title}:</b> ${text}"
        }

        return text
    }

    // Applies all the default HTML sanitizers to convert text
    static String sanitizeHtml(String html) {
        // Note that the FORMATTING pre defined policy allows font tags but does not allow the attributes on them,
        // so font tags are effectively stripped out of the output.
        FORMATTING.and(STYLES).and(LINKS).and(BLOCKS).and(IMAGES).and(TABLES).sanitize(html)
    }

    /**
     * As of JasperReports v6.2.0, HTML5 tags like strong, em and s are not supported.  This function converts
     * such tags to the older, similar and JasperReports supported b, i and strike.
     *
     * @param text The text to convert.
     * @return The converted text
     */
    static String convertTagsForJasper(String text) {
        convertTags(convertTags(convertTags(text, "strong", "b"), "em", "i"), "s", "strike")
    }

    /**
     * Converts empty tags from one element name to another using regex
     * @param text the text to find tags in
     * @param from the tag to find
     * @param to the tag to convert to
     * @return the converted string
     */
    private static String convertTags(String text, String from, String to) {
        text.replaceAll("<$from>", "<$to>").replaceAll("</$from>", "</${to.split('\\s')[0]}>")
    }

    /**
     * Replaces underscore with spaces and capitalize
     * @param text
     * @return
     */
    static String formatStatusText(String text) {
        text.replaceAll('_', ' ').capitalize()
    }

    def createOccurrencesUrl = { opus, occurrenceQuery ->
        return opus.biocacheUrl ? "${(opus.biocacheUrl as String).replaceAll('/$', '')}/occurrences/search?q=${occurrenceQuery}" : ""
    }

    def createMapImageUrl = { opus, occurrenceQuery ->
        Map p = [
                extents      : "96.173828125,-47.11468820158343,169.826171875,-2.5694811631203973",
                outlineColour: 0x000000,
                dpi          : 300,
                scale        : "on",
                baselayer    : "aus1",
                fileName     : "occurrencemap.jpg",
                format       : "jpg",
                outline      : true,
                popacity     : 1,
                pradiuspx    : 5,
                pcolour      : "00ff85"
        ]

        String url = "${grailsApplication.config.biocache.base.url}${grailsApplication.config.biocache.wms.image.path}${occurrenceQuery}&"
        p.each { k, v -> url += "${k}=${v}&" }
        if (url.charAt(url.length() - 1) == "&") {
            url = url.substring(0, url.length() - 1)
        }
        url
    }

    def getColourForStatus(status) {
        String colour;

        if (status =~ /extinct$/ || status =~ /wild/) {
            colour = "red";
        } else if (status =~ /Critically/ || status =~ /^Endangered/ || status =~ /Vulnerable/) {
            colour = "yellow";
        } else {
            colour = "green";
        }

        return colour;
    };

    List<Map> extractImagesFromAttributeText(String text, List<Map> images) {
        List attributeImages = []

        List<String> imageUrls = text.findAll("<img.*/>")?.collect { it.findAll("https?[^\"]*") }?.flatten()
        imageUrls.each { url ->
            String imageId

            String remoteImageIdPrefix = "imageId"

            if (url.contains(remoteImageIdPrefix)) {
                imageId = url.find(Utils.UUID_REGEX_PATTERN)
            } else {
                // url must be in the form http://.../opus/id/profile/id/image/thumbnail/id.ext
                if (url =~ LOCAL_IMAGE_THUMBNAIL_REGEX) {
                    imageId = url.replaceAll(LOCAL_IMAGE_THUMBNAIL_REGEX, '$1')
                }
            }

            Map image = images.find { it.imageId == imageId }

            if (image) {
                attributeImages << image
            }
        }

        attributeImages
    }

    Map groupImagesIntoPairs(String profileName, List<Map> images, int figureNumber) {
        List<Map> imagePairs = []

        images.eachWithIndex { image, i ->
            if (i % 2 == 0) {
                Map left = [:]
                left.putAll(image)
                left << [
                        imageNumber    : figureNumber++,
                        scientificName : profileName,
                        imageDetailsUrl: "${grailsApplication.config.images.service.url}/image/details?imageId=${left.imageId}",
                        licenceIcon    : Utils.getCCLicenceIcon(left?.metadata?.licence)
                ]
                Map right = [:]
                right.putAll((i + 1 < images.size()) ? images[i + 1] : [:])
                if (right) {
                    right << [
                            imageNumber    : figureNumber++,
                            scientificName : profileName,
                            imageDetailsUrl: "${grailsApplication.config.images.service.url}/image/details?imageId=${right.imageId}",
                            licenceIcon    : Utils.getCCLicenceIcon(right?.metadata?.licence)
                    ]
                }
                imagePairs << ["leftImage": left, "rightImage": right]
            }
        }

        [imagePairs: imagePairs, figureNumber: figureNumber]
    }

    /**
     * Removes all XML/HTML tags that are semantic and not for text formatting
     * At the moment only <i> and <b> are supported. Adding additional ones is trivial now
     * @param html
     * @return
     */
    static String stripTextFromNonFormattingHtmlTags(String html) {
        def regex = ~/<\/?(?!i>)(?!b>)([A-Za-z0-9.'"()+,=:\s-])+>/
        return html ? html.replaceAll(regex, "") : ""
    }
}
