package au.org.ala.profile.hub

import grails.test.mixin.TestFor
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by shi131 on 18/05/2016.
 */
@TestFor(ImageService)
class ImagePagingSpec extends Specification {

    @Shared
    def mockRequest
    @Shared
    File profileDir
    @Shared
    File tempDir
    @Shared
    BiocacheService biocacheServiceMockNoImages
    @Shared
    BiocacheService biocacheServiceMockImages
    @Shared
    ProfileService profileService
    @Shared
    ImageService imageService
    @Shared
    Integer pageSize
    @Shared
    List privateImages = ["privateImage1", "privateImage2", "privateImage3", "privateImage4", "privateImage5", "privateImage6", "privateImage7", "privateImage8", "privateImage9", "privateImage10", "privateImage11", "privateImage12"]
    @Shared
    List privateImagesComplex = [[imageId: 'imageId1'], [imageId: 'imageId2'], [imageId: 'imageId3'], [imageId: 'imageId4'], [imageId: 'imageId5'], [imageId: 'imageId6'], [imageId: 'imageId7'], [imageId: 'imageId8'], [imageId: 'imageId9'], [imageId: 'imageId10'], [imageId: 'imageId11'], [imageId: 'imageId12']]
    //List [[:],[:],[:],[:]]
    @Shared
    List publishedImages = [[image: "published1"], [image: "published2"], [image: "published3"], [image: "published4"], [image: "published5"], [image: "published6"], [image: "published7"], [image: "published8"], [image: "published9"], [image: "published10"], [image: "published11"]]


    def setupSpec() {
        imageService = new ImageService()
        pageSize = 5
        profileService = Mock(ProfileService)
        profileService.getProfile(_, _, _) >> [profile: [scientificName: 'Olympia', uuid: 'profile1', privateImages: privateImages], opus: [keepImagesPrivate: true, uuid: 'collection1', title: 'FindPuffins']]
        imageService.setProfileService(profileService)
        biocacheServiceMockNoImages = Mock(BiocacheService)
        biocacheServiceMockNoImages.retrieveImagesPaged(_, _, _, _) >> [:]
        biocacheServiceMockNoImages.imageCount(*_) >> [resp: [totalRecords: 0]]
        biocacheServiceMockImages = Mock(BiocacheService)
        biocacheServiceMockImages.retrieveImagesPaged(_, _, '3', '0') >> [statusCode: 200, resp: [occurrences: [[image: "published1"], [image: "published2"], [image: "published3"]]]]
        biocacheServiceMockImages.retrieveImagesPaged(_, _, '5', '3') >> [statusCode: 200, resp: [occurrences: [[image: "published4"], [image: "published5"], [image: "published6"], [image: "published7"], [image: "published8"]]]]
        biocacheServiceMockImages.retrieveImagesPaged(_, _, '5', '8') >> [statusCode: 200, resp: [occurrences: publishedImages[-1..-3]]]
        biocacheServiceMockImages.imageCount(*_) >> [resp: [totalRecords: 11]]

    }

    def cleanupSpec() {

    }

    def "A private image is mapped for display"() {
        given: "incoming private image metadata"
        Map privateImage = [class: "au.org.ala.profile.LocalImage", description: "AngularJS, SpringBoot, Bootstrap", imageId: "7178340a-9c91-4d6b-af70-7dbe7905ae09", licence: "Creative Commons Attribution", originalFileName: "logo-jhipster2x.png", rightsHolder: "FindPuffins", title: "JHipster application stack", contentType: null, created: '', creator: '', id: '', rights: '']
        imageService.setProfileService(profileService)
        Map model = imageService.profileService.getProfile('profile1', 'collection1', false)
        Map profile = model.profile
        Map opus = model.opus
        when: "the image is converted for display"
        List convertedImageList = imageService.convertLocalImages([privateImage], opus, profile, ImageType.PRIVATE, false, true)
        Map convertedImage = convertedImageList.get(0)
        then: "the image is mapped correctly"
        assert convertedImage.size() == 10
        assert convertedImage.imageId == '7178340a-9c91-4d6b-af70-7dbe7905ae09'
        assert convertedImage.thumbnailUrl == '/opus/collection1/profile/profile1/image/thumbnail/7178340a-9c91-4d6b-af70-7dbe7905ae09.png?type=PRIVATE'
        assert convertedImage.largeImageUrl == '/opus/collection1/profile/profile1/image/7178340a-9c91-4d6b-af70-7dbe7905ae09.png?type=PRIVATE'
        assert convertedImage.dataResourceName == 'FindPuffins'
        assert convertedImage.metadata.size() == 12
        assert convertedImage.excluded == false
        assert convertedImage.primary == false
        assert convertedImage.type == ImageType.PRIVATE
    }


    def "Private Image Paging - page 1 is returned"() {
        given: "a profile containing private images"
        Map profile = imageService.profileService.getProfile('profile1', 'collection1', false)
        List privateImages = profile.profile.privateImages
        Integer offset = pageSize * 0
        when: "the images are displayed"
        List imagesPaged = imageService.pageImages(privateImages, offset, pageSize)
        def numberOfImages = imagesPaged.size()
        then: "the number displayed is one page worth"
        assert (numberOfImages == pageSize)
        and: "the first image displayed is offset"
        assert (imagesPaged[0] == 'privateImage1')
        and: "the last image displayed is offset plus page size"
        assert (imagesPaged[-1] == 'privateImage5')
    }

    def "Private Image Paging - page 2 is returned"() {
        given: "a profile containing private images"
        Map profile = imageService.profileService.getProfile('profile1', 'collection1', false)
        List privateImages = profile.profile.privateImages
        Integer offset = pageSize * 1
        when: "the images are displayed"
        List imagesPaged = imageService.pageImages(privateImages, offset, pageSize)
        def numberOfImages = imagesPaged.size()
        then: "the number displayed is one page worth"
        assert (numberOfImages == pageSize)
        and: "the first image displayed is offset"
        assert (imagesPaged[0] == 'privateImage6')

    }

    def "Private Image Paging - edge case: offset equals number of images"() {
        given: "a profile containing private images"
        Map profile = imageService.profileService.getProfile('profile1', 'collection1', false)
        List privateImages = profile.profile.privateImages
        Integer offset = privateImages.size()
        when: "the images are displayed"
        List imagesPaged = imageService.pageImages(privateImages, offset, pageSize)
        def numberOfImages = imagesPaged.size()
        then: "only return the last image"
        assert (numberOfImages == 0)
    }

    def "Private Image Paging - offset exceeds number of images"() {
        given: "a profile containing private images"
        Map profile = imageService.profileService.getProfile('profile1', 'collection1', false)
        List privateImages = profile.profile.privateImages
        Integer offset = privateImages.size()
        when: "the images are displayed"
        List imagesPaged = imageService.pageImages(privateImages, offset, pageSize)
        def numberOfImages = imagesPaged.size()
        then: "only return the last image"
        assert (numberOfImages == 0)

    }

    def "Private Image Paging - last page doesn't contain duplicates"() {
        given: "a profile containing private images"
        Map profile = imageService.profileService.getProfile('profile1', 'collection1', false)
        List privateImages = profile.profile.privateImages
        Integer offset = pageSize * 2
        Integer lastPageSize = privateImages.size() - offset
        when: "the images are displayed"
        List imagesPaged = imageService.pageImages(privateImages, offset, pageSize)
        def numberOfImages = imagesPaged.size()
        then: "the number displayed is one page worth"
        assert (numberOfImages == lastPageSize)
        and: "the first image displayed is offset"
        assert (imagesPaged[0] == 'privateImage11')
        and: "the last image is the last image"
        assert (imagesPaged[-1] == 'privateImage12')

    }

    def "Private Image Paging - multiple pages when there are no published images, only private images"() {
        given: "a profile containing private images"
        profileService = Mock(ProfileService)
        profileService.getProfile(_, _, _) >> [profile: [scientificName: 'Olympia', uuid: 'profile1', privateImages: privateImagesComplex], opus: [keepImagesPrivate: true, uuid: 'collection1']]
        imageService.setProfileService(profileService)
        imageService.setBiocacheService(biocacheServiceMockNoImages)
        Integer offset = pageSize * 0
        Integer offset2 = pageSize * 1
        Integer offset3 = pageSize * 2
        Integer offset4 = pageSize * 3
        when: "we request pages of all images sequentially"
        def alaResponse = imageService.retrieveImagesPaged('collection1', 'profile1', true, '', false, true, pageSize.toString(), String.valueOf(offset))
        List imagesPage1 = alaResponse.resp.images
        def alaResponse2 = imageService.retrieveImagesPaged('collection1', 'profile1', true, '', false, true, pageSize.toString(), String.valueOf(offset2))
        List imagesPage2 = alaResponse2.resp.images
        def alaResponse3 = imageService.retrieveImagesPaged('collection1', 'profile1', true, '', false, true, pageSize.toString(), String.valueOf(offset3))
        List imagesPage3 = alaResponse3.resp.images
        def alaResponse4 = imageService.retrieveImagesPaged('collection1', 'profile1', true, '', false, true, pageSize.toString(), String.valueOf(offset4))
        List imagesPage4 = alaResponse4.resp.images
        Integer count = alaResponse.resp.count
        then: "the first page has the correct number of images"
        assert (imagesPage1.size() == pageSize)
        and: "the first image of the first page is the first image available"
        assert imagesPage1[0].imageId == 'imageId1'
        and: "the second page has the correct number of images"
        assert (imagesPage2.size() == pageSize)
        and: "the third page has the correct number of images"
        assert (imagesPage3.size() == privateImagesComplex.size() - offset3)
        and: "there is no fourth page"
        assert (imagesPage4.size() == 0)
        and: "the first image on the second page is the image immediately after the last image on the first page"
        assert (Integer.valueOf(imagesPage2[0].imageId[-1]) == Integer.valueOf(imagesPage1[-1].imageId[-1]) + 1)
        and: "the first image on the third page is the image immediately after the last image on the second page"
        assert (Integer.valueOf(imagesPage3[0].imageId[-1]) == Integer.valueOf(imagesPage2[-1].imageId[-1]) + 1)
        and: "the count of total available images is accurate"
        assert (count == privateImagesComplex.size())
        and: "all images are accounted for"
        assert (count == (imagesPage1.size() + imagesPage2.size() + imagesPage3.size() + imagesPage4.size()))
    }


    def "Private Image and Published Images paging"() {
        given: "a profile containing private and published images"
        profileService = Mock(ProfileService)
        profileService.getProfile(_, _, _) >> [profile: [scientificName: 'Olympia', uuid: 'profile1', privateImages: privateImagesComplex], opus: [keepImagesPrivate: true, uuid: 'collection1']]
        imageService.setProfileService(profileService)
        imageService.setBiocacheService(biocacheServiceMockImages)
        Integer offset3 = pageSize * 2
        Integer offset4 = pageSize * 3
        Integer offset5 = pageSize * 4
        when: "we request pages of all images sequentially"
        def alaResponse3 = imageService.retrieveImagesPaged('collection1', 'profile1', true, '', false, true, pageSize.toString(), String.valueOf(offset3))
        List imagesPage3 = alaResponse3.resp.images
        def alaResponse4 = imageService.retrieveImagesPaged('collection1', 'profile1', true, '', false, true, pageSize.toString(), String.valueOf(offset4))
        List imagesPage4 = alaResponse4.resp.images
        def alaResponse5 = imageService.retrieveImagesPaged('collection1', 'profile1', true, '', false, true, pageSize.toString(), String.valueOf(offset5))
        List imagesPage5 = alaResponse5.resp.images
        Integer count = alaResponse3.resp.count
        then: "the third page has the correct number of images"
        assert (imagesPage3.size() == pageSize)
        and: "the third page consists of private and published images"
        assert (imagesPage3[0].imageId == 'imageId11')
        assert (imagesPage3[2].imageId == 'published1')
        and: "the fourth page has the correct number of images"
        assert (imagesPage4.size() == 5)
        and: "the last page has the correct number of images"
        assert (imagesPage5.size() == 3)
        and: "all images are accounted for"
        assert (count == (imagesPage3.size() + imagesPage4.size()) + imagesPage5.size() + 10)


    }


}
