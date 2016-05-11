<div class="col-md-6 col-sm-12" ng-controller="ImagesController as imageCtrl"
     ng-init="imageCtrl.init('${edit}')" ng-show="imageCtrl.primaryImage" ng-cloak>
    <div id="primaryImage" class="col-md-12">
        <div class="primary-image col-md-12">
            <div class="thumbnail pull-left">
                <ala-link href="${grailsApplication.config.biocache.base.url}${grailsApplication.config.biocache.occurrence.record.path}{{imageCtrl.primaryImage.occurrenceId}}"
                   target="_blank" ng-show="imageCtrl.primaryImage.largeImageUrl" disable="{{imageCtrl.primaryImage.type.name != OPEN}}" ng-cloak>
                    <img ng-src="${request.contextPath}{{imageCtrl.primaryImage.thumbnailUrl}}"
                         ng-if="imageCtrl.primaryImage.thumbnailUrl && imageCtrl.primaryImage.type.name != 'OPEN'"/>
                    <img ng-src="{{imageCtrl.primaryImage.largeImageUrl}}"
                         ng-if="imageCtrl.primaryImage.largeImageUrl && imageCtrl.primaryImage.type.name == 'OPEN'"/>
                </ala-link>
            </div>
        </div>

        <div class="col-md-12" ng-show="imageCtrl.primaryImage">
            <a target="_self" href="" class="padding-left-1 margin-top-1 inline-block"
               du-smooth-scroll="{{profileCtrl.readonly() ? 'view_' : 'edit_'}}images">View all images</a>
        </div>
    </div>
</div>