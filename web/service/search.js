'use strict';

app.factory('searchService', [
  'routerService', 'modelService',
  function (router, models) {

    ///////////////////////////////// Party

    function Party(init) {
      models.Party.call(this, init);
    }

    Party.prototype = Object.create(models.Party.prototype);
    Party.prototype.constructor = Party;
    Party.prototype.fields = angular.extend({
      orcid: false,
      email: false,
      url: false,
    }, Party.prototype.fields);

    function partyMake(init) {
      return models.Party.peek(init.id) || new Party(init);
    }

    function partyMakeArray(l) {
      if (l) for (var i = 0; i < l.length; i ++)
        l[i] = partyMake(l[i]);
      return l;
    }

    ///////////////////////////////// Volume

    function Volume(init) {
      if (!init.owners) {
        init.owners = {};
        for (var i = 0; i < init.owner_ids.length; i++)
          init.owners[init.owner_ids[i]] = init.owner_names[i];
      }
      models.Volume.call(this, init);
    }

    Volume.prototype = Object.create(models.Volume.prototype);
    Volume.prototype.constructor = Volume;
    Volume.prototype.fields = angular.extend({
      alias: false,
      doi: false,
      creation: false,
    }, Volume.prototype.fields);

    function volumeMake(init) {
      return models.Volume.cache.get(init.id) || new Volume(init);
    }

    function volumeMakeArray(l) {
      for (var i = 0; i < l.length; i ++)
        l[i] = volumeMake(l[i]);
      return l;
    }

    ///////////////////////////////// search

    Party.limit = 24;
    Party.volume = 'false';
    Party.search = function (params) {
      if (params.volume && params.volume !== Party.volume)
        return;
      if (!params.volume) {
        params.volume = false;
        params.limit = 12;
      } else
        params.limit = Party.limit;
      return router.http(router.controllers.postSearch, params)
        .then(function (res) {
          partyMakeArray(res.data.response.docs);
          return res.data;
        });
    };

    Volume.limit = 8;
    Volume.volume = true;
    Volume.search = function (params) {
      if (params.volume === Party.volume)
        return;
      if (!params.volume) {
        params.volume = true;
        params.limit = 4;
      } else
        params.limit = Volume.limit;
      return router.http(router.controllers.postSearch, params)
        .then(function (res) {
          volumeMakeArray(res.data.response.docs);
          return res.data;
        });
    };

    return {
      Party: Party,
      Volume: Volume
    };
  }
]);
