<div id="opusInfo" class="well" ng-form="StyleForm">
    <h4>Styling</h4>
    <p>
        <label>Banner image:</label>
        <input type="text" class="input-xxlarge" name="bannerUrl" ng-model="opusCtrl.opus.bannerUrl"/>
    </p>
    <p>
        <label>Logo:</label>
        <input type="text" class="input-xxlarge" name="logoUrl" ng-model="opusCtrl.opus.logoUrl"/>
    </p>
    <button class="btn btn-primary" ng-click="opusCtrl.saveOpus(StyleForm)">
        <span ng-show="!opusCtrl.saving" id="saved"><span ng-show="StyleForm.$dirty">*</span> Save</span>
        <span ng-show="opusCtrl.saving" id="saving">Saving....</span>
    </button>
</div>