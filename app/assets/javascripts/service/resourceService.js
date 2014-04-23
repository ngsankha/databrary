module.factory('resourceService', [
	'$http', '$q', 'cacheService', function ($http, $q, cacheService) {
		var $resourceMinErr = angular.$$minErr('$resource');

		var MEMBER_NAME_REGEX = /^(\.[a-zA-Z_$][0-9a-zA-Z_$]*)+$/;

		function isValidDottedPath(path) {
			return (path != null && path !== '' && path !== 'hasOwnProperty' &&
				MEMBER_NAME_REGEX.test('.' + path));
		}

		function lookupDottedPath(obj, path) {
			if (!isValidDottedPath(path)) {
				throw $resourceMinErr('badmember', 'Dotted member path "@{0}" is invalid.', path);
			}
			var keys = path.split('.');
			for (var i = 0, ii = keys.length; i < ii && obj !== undefined; i++) {
				var key = keys[i];
				obj = (obj !== null) ? obj[key] : undefined;
			}
			return obj;
		}

		function shallowClearAndCopy(src, dst) {
			dst = dst || {};

			angular.forEach(dst, function (value, key) {
				delete dst[key];
			});

			for (var key in src) {
				if (src.hasOwnProperty(key) && !(key.charAt(0) === '$' && key.charAt(1) === '$')) {
					dst[key] = src[key];
				}
			}

			return dst;
		}

		var DEFAULT_ACTIONS = {
			'get': {method: 'GET'},
			'save': {method: 'POST'},
			'query': {method: 'GET', isArray: true},
			'remove': {method: 'DELETE'},
			'delete': {method: 'DELETE'}
		};
		var noop = angular.noop,
			forEach = angular.forEach,
			extend = angular.extend,
			copy = angular.copy,
			isFunction = angular.isFunction;

		function encodeUriSegment(val) {
			return encodeUriQuery(val, true).
				replace(/%26/gi, '&').
				replace(/%3D/gi, '=').
				replace(/%2B/gi, '+');
		}

		function encodeUriQuery(val, pctEncodeSpaces) {
			return encodeURIComponent(val).
				replace(/%40/gi, '@').
				replace(/%3A/gi, ':').
				replace(/%24/g, '$').
				replace(/%2C/gi, ',').
				replace(/%20/g, (pctEncodeSpaces ? '%20' : '+'));
		}

		function Route(template, defaults) {
			this.template = template;
			this.defaults = defaults || {};
			this.urlParams = {};
		}

		Route.prototype = {
			setUrlParams: function (config, params, actionUrl) {
				var self = this,
					url = actionUrl || self.template,
					val,
					encodedVal;

				var urlParams = self.urlParams = {};
				forEach(url.split(/\W/), function (param) {
					if (param === 'hasOwnProperty') {
						throw $resourceMinErr('badname', "hasOwnProperty is not a valid parameter name.");
					}
					if (!(new RegExp("^\\d+$").test(param)) && param &&
						(new RegExp("(^|[^\\\\]):" + param + "(\\W|$)").test(url))) {
						urlParams[param] = true;
					}
				});
				url = url.replace(/\\:/g, ':');

				params = params || {};
				forEach(self.urlParams, function (_, urlParam) {
					val = params.hasOwnProperty(urlParam) ? params[urlParam] : self.defaults[urlParam];
					if (angular.isDefined(val) && val !== null) {
						encodedVal = encodeUriSegment(val);
						url = url.replace(new RegExp(":" + urlParam + "(\\W|$)", "g"), function (match, p1) {
							return encodedVal + p1;
						});
					} else {
						url = url.replace(new RegExp("(\/?):" + urlParam + "(\\W|$)", "g"), function (match, leadingSlashes, tail) {
							if (tail.charAt(0) == '/') {
								return tail;
							} else {
								return leadingSlashes + tail;
							}
						});
					}
				});

				url = url.replace(/\/+$/, '') || '/';
				url = url.replace(/\/\.(?=\w+($|\?))/, '.');
				config.url = url.replace(/\/\\\./, '/.');

				forEach(params, function (value, key) {
					if (!self.urlParams[key]) {
						config.params = config.params || {};
						config.params[key] = value;
					}
				});
			}
		};

		function resourceFactory(cacheID, url, paramDefaults, actions) {
			var route = new Route(url);
			var cache = cacheService(cacheID);

			actions = extend({}, DEFAULT_ACTIONS, actions);

			function extractParams(data, actionParams) {
				var ids = {};
				actionParams = extend({}, paramDefaults, actionParams);
				forEach(actionParams, function (value, key) {
					if (isFunction(value)) {
						value = value();
					}
					ids[key] = value && value.charAt && value.charAt(0) == '@' ?
						lookupDottedPath(data, value.substr(1)) : value;
				});
				return ids;
			}

			function defaultResponseInterceptor(response) {
				return response.resource;
			}

			function Resource(value) {
				shallowClearAndCopy(value || {}, this);
			}

			forEach(actions, function (action, name) {
				var hasBody = /^(POST|PUT|PATCH)$/i.test(action.method);

				Resource[name] = function (a1, a2, a3, a4) {
					var params = {}, data, success, error;

					switch (arguments.length) {
						case 4:
							error = a4;
							success = a3;
						//fallthrough
						case 3:
						case 2:
							if (isFunction(a2)) {
								if (isFunction(a1)) {
									success = a1;
									error = a2;
									break;
								}

								success = a2;
								error = a3;
								//fallthrough
							} else {
								params = a1;
								data = a2;
								success = a3;
								break;
							}
						case 1:
							if (isFunction(a1)) {
								success = a1;
							}
							else if (hasBody) {
								data = a1;
							}
							else {
								params = a1;
							}
							break;
						case 0:
							break;
						default:
							throw $resourceMinErr('badargs',
								"Expected up to 4 arguments [params, data, success, error], got {0} arguments",
								arguments.length);
					}

					var isInstanceCall = this instanceof Resource;
					var value = isInstanceCall ? data : (action.isArray ? [] : new Resource(data));
					var httpConfig = {};
					var responseInterceptor = action.interceptor && action.interceptor.response ||
						defaultResponseInterceptor;
					var responseErrorInterceptor = action.interceptor && action.interceptor.responseError ||
						undefined;

					forEach(action, function (value, key) {
						if (key != 'params' && key != 'isArray' && key != 'interceptor') {
							httpConfig[key] = copy(value);
						}
					});

					if (hasBody) {
						httpConfig.data = data;
					}
					route.setUrlParams(httpConfig,
						extend({}, extractParams(data, action.params || {}), params),
						action.url);

					var promise;
					var cached = cache.get(extend({}, extractParams(data, action.params || {}), params).id, params);

					if (cached) {
						shallowClearAndCopy(cached, value);
						promise = $q.when({
							resource: value
						});
					} else {
						promise = $http(httpConfig).then(function (response) {
							var data = response.data,
								promise = value.$promise;

							if (data) {
								if (angular.isArray(data) !== (!!action.isArray)) {
									throw $resourceMinErr('badcfg', 'Error in resource configuration. Expected ' +
											'response to contain an {0} but got an {1}',
										action.isArray ? 'array' : 'object', angular.isArray(data) ? 'array' : 'object');
								}

								if (action.isArray) {
									value.length = 0;
									forEach(data, function (item) {
										value.push(new Resource(item));
										cache.set(item, params);
									});
								} else {
									shallowClearAndCopy(data, value);
									value.$promise = promise;
									cache.set(data, params);
								}
							}

							value.$resolved = true;

							response.resource = value;

							return response;
						}, function (response) {
							value.$resolved = true;

							(error || noop)(response);

							return $q.reject(response);
						});
					}

					promise = promise.then(function (response) {
						var value = responseInterceptor(response);
						(success || noop)(value, response.headers);
						return value;
					}, responseErrorInterceptor);

					if (!isInstanceCall) {
						value.$promise = promise;
						value.$resolved = false;

						return value;
					}

					return promise;
				};

				Resource.prototype['$' + name] = function (params, success, error) {
					if (isFunction(params)) {
						error = success;
						success = params;
						params = {};
					}
					var result = Resource[name].call(this, params, this, success, error);
					return result.$promise || result;
				};
			});

			Resource.bind = function (additionalParamDefaults) {
				return resourceFactory(url, extend({}, paramDefaults, additionalParamDefaults), actions);
			};

			Resource.cache = cache;

			return Resource;
		}

		return resourceFactory;
	}
]);
