var profiles = {
    urls: {
        biocacheOccurrenceSearchUrl: null,
        biocacheOccurrenceRecordUrl: null,
        biocacheOccurrenceInfoUrl: null,
        biocacheWMSUrl: null
    },

    maps: {
        mapBaseLayerAttribution: "Map data &copy; <a href=\"http://openstreetmap.org\">OpenStreetMap</a> contributors, <a href=\"http://creativecommons.org/licenses/by-sa/2.0/\">CC-BY-SA</a>, Imagery © <a href=\"http://mapbox.com\">Mapbox</a>"
    },

    init: function (options) {
        profiles.urls = options.urls;
        profiles.urls.biocacheSearchUrl = options.urls.biocacheBaseUrl + options.urls.biocacheSearchPath;
        profiles.urls.biocacheRecordUrl = options.urls.biocacheBaseUrl + options.urls.biocacheRecordPath;
        profiles.urls.biocacheInfoUrl = options.urls.biocacheBaseUrl + options.urls.biocacheInfoPath;
        profiles.urls.biocacheWMSUrl = options.urls.biocacheBaseUrl + options.urls.biocacheWmsPath;
    },

    addImages: function (imagesQuery) {
        $.ajax({
            url: profiles.urls.biocacheSearchUrl,
            jsonp: "callback",
            dataType: "jsonp",
            data: {
                q: imagesQuery,
                fq: "multimedia:Image",
                format: 'json'
            },
            success: function (response) {
                if (response.totalRecords > 0) {
                    console.log("number of records with images: " + response.totalRecords);

                    var firstImage = response.occurrences[0];

                    var firstImageDiv = $("#firstImage")
                    firstImageDiv.append('<div class="imgConXXX"><a href="' + profiles.urls.biocacheRecordUrl + firstImage.uuid + '"><img src="' + firstImage.largeImageUrl + '"/></a> <div class="meta">' + firstImage.dataResourceName + '</div></div>');
                    firstImageDiv.show();

                    $.each(response.occurrences, function (key, record) {
                        $('#browse_images').append('<div class="imgCon"><a href="' + profiles.urls.biocacheRecordUrl + record.uuid + '"><img src="' + record.largeImageUrl + '"/></a> <div class="meta">' + record.dataResourceName + '</div></div>');
                    });
                    $('#browse_images').show();
                }
            },
            error: function (jqXHR, textStatus, errorThrown) {
                console.log("Error completing images - JSON - " + errorThrown + ", status = " + jqXHR.status);
            }
        });
    },

    addTaxonMap: function (opus, profile, occurrenceQuery) {
        opus = $.parseJSON(opus)
        profile = $.parseJSON(profile)

        var wmsLayer = profiles.urls.biocacheWMSUrl + occurrenceQuery;

        var speciesLayer = L.tileLayer.wms(wmsLayer, {
            layers: 'ALA:occurrences',
            format: 'image/png',
            transparent: true,
            attribution: opus.mapAttribution,
            bgcolor: "0x000000",
            outline: "true",
            ENV: "color:" + opus.mapPointColour + ";name:circle;size:4;opacity:1"
        });

        var speciesLayers = new L.LayerGroup();
        speciesLayer.addTo(speciesLayers);

        var map = L.map('map', {
            center: [opus.mapDefaultLatitude, opus.mapDefaultLongitude],
            zoom: opus.mapZoom,
            layers: [speciesLayers]
        });

        var streetView = L.tileLayer(opus.mapBaseLayer, {
            maxZoom: 18,
            attribution: profiles.maps.mapBaseLayerAttribution,
            id: 'examples.map-i875mjb7'
        }).addTo(map);

        var baseLayers = {
            "Street": streetView
        };

        var layerTitle = profile.scientificName;

        var overlays = {};
        overlays[layerTitle] = speciesLayer;

        L.control.layers(baseLayers, overlays).addTo(map);

        map.on('click', function (event) {
            profiles.onMapClick(event, occurrenceQuery)
        });
    },

    onMapClick: function (e, occurrenceQuery) {
        console.log(profiles.urls.biocacheInfoUrl + occurrenceQuery)
        $.ajax({
            url: profiles.urls.biocacheInfoUrl,
            jsonp: "callback",
            dataType: "jsonp",
            data: {
                q: occurrenceQuery,
                zoom: "6",
                lat: e.latlng.lat,
                lon: e.latlng.lng,
                radius: 20,
                format: "json"
            },
            success: function (response) {
                var popup = L.popup()
                    .setLatLng(e.latlng)
                    .setContent("<h3>Test</h3>Occurrences at this point: " + response.count)
                    .openOn(map);
            },
            error: function (jqXHR, textStatus, errorThrown) {
                console.log("Error completing images - JSON - " + errorThrown + ", status = " + jqXHR.text);
            }
        })
    },

    addLists: function (profile) {
        $.ajax({
            url: profiles.urls.listsBaseUrl + "/ws/species/" + profile.lsid,
            jsonp: "callback",
            dataType: "jsonp",
            data: {format: 'json'},
            success: function (response) {
                if (response instanceof Array && response.length > 0) {
                    console.log("number of list entries: " + response.length);
                    for (var i = 0; i < response.length; i++) {
                        $('#browse_lists ul').append('<li><a href="' + profile.urls.listsBaseUrl + "/speciesListItem/list/" + response[i].dataResourceUid + '">' + response[i].list.listName + '</a></li>');
                    }
                    $('#browse_lists').show();
                }
            },
            error: function (jqXHR, textStatus, errorThrown) {
                console.log("Error collecting lists JSON - " + errorThrown);
            }
        });
    }
};


var profileEditor = angular.module('profileEditor', ['ui.bootstrap']);

profileEditor.config(function ($locationProvider) {
    // This disables 'hashbang' mode and removes the need to specify <base href="/my-base"> in the views.
    // This makes AngularJS take control of all links on the page: if you do not want Angular to control a particular
    // link, add target="_self".
    $locationProvider.html5Mode({enabled: true, requireBase: false});
    console.log("HTML 5 mode enabled");

    // TODO remove this when all JS has been converted to Angular
    initialiseUrls();
});









