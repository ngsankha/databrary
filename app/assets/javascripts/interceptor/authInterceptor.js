module.factory('authInterceptor', [
	'$rootScope', '$q', '$location', function ($rootScope, $q, $location) {
		return {
			responseError: function (response) {
				console.log(response);
				if (response.status == 403) {
					$location.url('/login');
				}

				return $q.reject(response);
			}
		}
	}
]);

module.config([
	'$httpProvider', function ($httpProvider) {
		$httpProvider.interceptors.push('authInterceptor');
	}
]);
