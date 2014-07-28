'use strict';

module.directive('player', [
	'pageService', function (page) {
		var isNothing = function (val) {
			return angular.isUndefined(val) || val === null;
		};

		var controller = [
			'$scope', '$element', '$attrs', function ($scope, $element, $attrs) {
				var player = this;
				var $player = $element.find('.player');
				var $list = $player.find('.player-list');
				var $items = $player.find('.player-list-items');
				var $resize = $player.find('.player-resize-main');

				player.slot = page.$parse($attrs.slot)($scope);
				player.main = [];
				player.playable = [];
				player.unplayable = [];

				var makeUnplayableLists = function () {
					if (player.unplayable.length === 0)
						return;

					var i = 0;
					var l = player.unplayable.length;
					var stack = Math.floor($items.outerHeight() / 24) || 1;

					player.unplayableList = [];

					while (i < l) {
						player.unplayableList.push(player.unplayable.slice(i, i + stack));
						i = i + stack;
					}
				};

				player.isPlayable = function (asset) {
					return ['video', 'image'].indexOf(page.types.assetMimeArray(asset, true)[0]) > -1;
				};

				player.slot.assets.map(function (asset) {
					return asset;
				}).sort(function (a, b) {
					if (isNothing(a.segment) && isNothing(b.segment)) {
						return 0;
					} else if (isNothing(a.segment)) {
						return 1;
					} else if (isNothing(b.segment)) {
						return -1;
					}

					var aSeg = angular.isArray(a.segment) ? a.segment[0] : a.segment;
					var bSeg = angular.isArray(b.segment) ? b.segment[0] : b.segment;
					return aSeg < bSeg;
				}).forEach(function (asset) {
					// TODO: Type and TypeList classes that do this sort of thing
					asset.container = player.slot.container;

					if (asset.segment === null) {
						return;
					}

					if (player.isPlayable(asset)) {
						player.playable.push(asset);
					} else {
						player.unplayable.push(asset);
					}

					makeUnplayableLists();
				});

				player.main.push(player.playable[0] || player.unplayable[0]);

				//

				player.select = function (media) {
					player.main[0] = media;
				};

				//

				player.listScroll = function (forward) {
					var listW = $list.outerWidth();
					var itemsW = $items.outerWidth();
					var pos = parseInt($items.css('left'));
					var next;

					if (forward) {
						next = pos - (listW * 0.8);

						if (-next + listW > itemsW) {
							next = listW - itemsW;
						}
					} else {
						next = pos + (listW * 0.8);
					}

					if (next > 0) {
						next = 0;
					}

					$items.animate({
						left: next,
					}, {
						duration: 150,
						always: function () {
							page.$timeout(updateScroll, 175);
						}
					});
				};

				var updateScroll = function () {
					var listW = $list.outerWidth();
					var itemsW = $items.outerWidth();
					var pos = parseInt($items.css('left'));

					player.canScrollBack = pos < 0;
					player.canScrollForward = listW - pos < itemsW;
				};

				var updateScrollEvent = function () {
					$scope.$apply(function () {
						updateScroll();
					});
				};

				page.$w
					.resize(updateScrollEvent)
					.load(updateScrollEvent);

				//

				var resizeStart = 0;

				player.resize = function (e) {
					page.$d
						.bind('mousemove.playerResize', resizeMove)
						.bind('mouseup.playerResize', resizeUp);

					resizeStart = e.clientY;
					e.stopPropagation();
				};

				var resizeMove = function (e) {
					var $main = $player.find('.player-main');
					var playerH = $player.outerHeight();
					var playerT = $player.offset().top;
					var resizeH = $resize.outerHeight(true);
					var mainH = e.clientY - playerT;
					var listH = playerH - resizeH - mainH;

					if (mainH < 48) {
						mainH = 48;
						listH = playerH - resizeH - mainH;
					} else if (listH < 36) {
						mainH = playerH - 36 - resizeH;
						listH = playerH - resizeH - mainH;
					}

					$main.height(mainH);
					$list.height(listH);

					page.$timeout(function () {
						updateScroll();

						$main.find('.player-main-media img, .player-main-media video').each(function () {
							$(this).width(mainH * (this.naturalWidth / this.naturalHeight));
						});

						$list.find('img, video').each(function () {
							$(this).width(this.height * (this.naturalWidth / this.naturalHeight));
						});

						makeUnplayableLists();
					});

					e.preventDefault();
				};

				var resizeUp = function (e) {
					page.$d.unbind('mousemove.playerResize mouseup.playerResize');
					e.preventDefault();
				};
			}
		];

		//

		return {
			restrict: 'E',
			scope: true,
			templateUrl: 'player.html',
			controller: controller,
			controllerAs: 'player',
		};
	}
]);
