# Changelog

## [1.1.0](https://github.com/derekwinters/roadtrip-android/compare/v1.0.0...v1.1.0) (2026-07-20)


### Features

* **games:** add per-profile, per-game-type confirm-move toggle ([#126](https://github.com/derekwinters/roadtrip-android/issues/126)) ([95b2458](https://github.com/derekwinters/roadtrip-android/commit/95b2458b23d94cb63acff7fc211a743c2d110471))
* **games:** always confirm before resigning or ending a game ([#127](https://github.com/derekwinters/roadtrip-android/issues/127)) ([9fb8f7e](https://github.com/derekwinters/roadtrip-android/commit/9fb8f7e3bcfe89d795bc76ffb6a6cc0488280dee))
* **games:** highlight the last move on all game boards ([#128](https://github.com/derekwinters/roadtrip-android/issues/128)) ([0ff6b27](https://github.com/derekwinters/roadtrip-android/commit/0ff6b27c5da576b43f6a9d150331ee8c18b8e9ae))
* **map:** manual End-leg control + keep destinations live during a trip ([#135](https://github.com/derekwinters/roadtrip-android/issues/135)) ([a53ce89](https://github.com/derekwinters/roadtrip-android/commit/a53ce894e290f0fb9a7397063f0cf1174bb03b5c))


### Bug Fixes

* **map:** parse geocode response as a bare array ([#85](https://github.com/derekwinters/roadtrip-android/issues/85)) ([4c2271a](https://github.com/derekwinters/roadtrip-android/commit/4c2271a5a44b36692498c692c26ca69676088d67))

## 1.0.0 (2026-07-19)


### ⚠ BREAKING CHANGES

* **trips:** TripSummary drops winsByProfile/journalPostsByProfile.

### Features

* add family members from the picker; parent switch for open creation ([#73](https://github.com/derekwinters/roadtrip-android/issues/73)) ([0316de5](https://github.com/derekwinters/roadtrip-android/commit/0316de52088cbda3d24f5d86879606adc634bf05)), closes [#71](https://github.com/derekwinters/roadtrip-android/issues/71)
* Family Road Trip Android app (phones + tablets, offline-first, games, tracking) ([#52](https://github.com/derekwinters/roadtrip-android/issues/52)) ([d9f0280](https://github.com/derekwinters/roadtrip-android/commit/d9f0280e7dd920b17cb0cce823df0f532d4856b8))
* family setup, address search, itinerary planner, bingo — plus installer-crash fix ([#66](https://github.com/derekwinters/roadtrip-android/issues/66)) ([64067fd](https://github.com/derekwinters/roadtrip-android/commit/64067fd476317279a4d2d7fc857804ca4cbaea46))
* **games:** add X/O player-identity legend with turn highlight ([#113](https://github.com/derekwinters/roadtrip-android/issues/113)) ([2c9db92](https://github.com/derekwinters/roadtrip-android/commit/2c9db921183c4a2d114fc51578fca6b4bcc7f622))
* **games:** attribute lobby rows, add refresh, clear the FAB inset ([#83](https://github.com/derekwinters/roadtrip-android/issues/83), [#94](https://github.com/derekwinters/roadtrip-android/issues/94), [#96](https://github.com/derekwinters/roadtrip-android/issues/96)) ([affcc2c](https://github.com/derekwinters/roadtrip-android/commit/affcc2cbf875ece6cb529f5b74862af1ded13dd0))
* **games:** fix piece colors/fill, scale glyphs, fit boards on tablet, highlight wins ([#75](https://github.com/derekwinters/roadtrip-android/issues/75), [#76](https://github.com/derekwinters/roadtrip-android/issues/76), [#77](https://github.com/derekwinters/roadtrip-android/issues/77), [#85](https://github.com/derekwinters/roadtrip-android/issues/85), [#86](https://github.com/derekwinters/roadtrip-android/issues/86), [#87](https://github.com/derekwinters/roadtrip-android/issues/87)) ([039419e](https://github.com/derekwinters/roadtrip-android/commit/039419e060a3e70be4ef908bcc0f912960c4b13d))
* **games:** only the setter can end a hangman game ([#82](https://github.com/derekwinters/roadtrip-android/issues/82)) ([0678644](https://github.com/derekwinters/roadtrip-android/commit/067864418ed295ac258e0c5b1d931e32eee95765))
* **games:** show hangman masked progress in lobby titles ([#81](https://github.com/derekwinters/roadtrip-android/issues/81)) ([b9e332d](https://github.com/derekwinters/roadtrip-android/commit/b9e332df751519274a3d90c9e2efe19d848a9d74))
* **games:** split lobby into Active/Finished tabs ([#117](https://github.com/derekwinters/roadtrip-android/issues/117)) ([cc1033d](https://github.com/derekwinters/roadtrip-android/commit/cc1033dd854efbe899c25a35e3849e973da1b5b2))
* **location:** keep the tracker alive across dismissal, Doze, and reboot ([#90](https://github.com/derekwinters/roadtrip-android/issues/90)) ([4bbb326](https://github.com/derekwinters/roadtrip-android/commit/4bbb32601434d7d1e31f3937b620ee723cf511f2))
* **map:** car icon for current position, red/green dot markers ([#91](https://github.com/derekwinters/roadtrip-android/issues/91), [#92](https://github.com/derekwinters/roadtrip-android/issues/92)) ([f90ddea](https://github.com/derekwinters/roadtrip-android/commit/f90ddeaabb6a9145ce24f62ea0c8aadabf026fe1))
* **setup:** prompt for server address on first launch ([#111](https://github.com/derekwinters/roadtrip-android/issues/111)) ([7f9b94e](https://github.com/derekwinters/roadtrip-android/commit/7f9b94e57d534b43fdfdc31fb4db8dd386ec652b))
* **sync:** foreground live-refresh so screens update in place ([#95](https://github.com/derekwinters/roadtrip-android/issues/95)) ([1e5e769](https://github.com/derekwinters/roadtrip-android/commit/1e5e769f18af39edcebd2586042bb4162744931c))
* trips client + any-device tracker with parent attribution ([#62](https://github.com/derekwinters/roadtrip-android/issues/62)) ([69488be](https://github.com/derekwinters/roadtrip-android/commit/69488be95d4852ddbc693d9983ec0e2d086825cb))
* **trips:** drop per-person wins/journal-post stats from trip summary ([#88](https://github.com/derekwinters/roadtrip-android/issues/88)) ([8a52d96](https://github.com/derekwinters/roadtrip-android/commit/8a52d9619f6335af20dc20e841e72cbc1820052c))
* **ui:** Material nav motion, trip name in app bar, clearer selection controls ([#78](https://github.com/derekwinters/roadtrip-android/issues/78), [#84](https://github.com/derekwinters/roadtrip-android/issues/84)) ([329ba37](https://github.com/derekwinters/roadtrip-android/commit/329ba37cd3be208107c326d3d42654062a27494b))


### Bug Fixes

* **games:** hangman turn stays with the guesser; drive board from server view ([#79](https://github.com/derekwinters/roadtrip-android/issues/79), [#80](https://github.com/derekwinters/roadtrip-android/issues/80)) ([50139ee](https://github.com/derekwinters/roadtrip-android/commit/50139ee494f33b68e90fa522e950fc9cd138fd9b))
* **games:** use wrapping choice chips for single-select selectors ([#110](https://github.com/derekwinters/roadtrip-android/issues/110)) ([026764b](https://github.com/derekwinters/roadtrip-android/commit/026764b4563a2b8e501b4e035947395134c73b86))
* **journal:** page load-older from the client_ts-oldest cached entry ([#114](https://github.com/derekwinters/roadtrip-android/issues/114)) ([3b81766](https://github.com/derekwinters/roadtrip-android/commit/3b817667204b946f2a7fc5f48d177c0cb28f1975))
* **journal:** render non-linkable posts at full emphasis, not disabled ([#118](https://github.com/derekwinters/roadtrip-android/issues/118)) ([f467284](https://github.com/derekwinters/roadtrip-android/commit/f467284b44354695a74d9bb647b176af4f97935f))
* **journal:** resolve author avatar/name from current profiles cache ([#112](https://github.com/derekwinters/roadtrip-android/issues/112)) ([c9a2488](https://github.com/derekwinters/roadtrip-android/commit/c9a2488db67aec34a90492396173f01649cd56e8))
* make the server address editable before sign-in on the profile picker ([#68](https://github.com/derekwinters/roadtrip-android/issues/68)) ([6a6e043](https://github.com/derekwinters/roadtrip-android/commit/6a6e0434e97750a02f4b2d67b036915eb5c590a3)), closes [#67](https://github.com/derekwinters/roadtrip-android/issues/67)
* **map:** distinguish offline vs geocoder-down in address search ([#93](https://github.com/derekwinters/roadtrip-android/issues/93)) ([a7b437b](https://github.com/derekwinters/roadtrip-android/commit/a7b437b38c3637a98d3ffe2363b2dacf243e7cc2))
* publish RCs on opaque tags so release-please only sees final versions ([#70](https://github.com/derekwinters/roadtrip-android/issues/70)) ([db097e9](https://github.com/derekwinters/roadtrip-android/commit/db097e9eb946e48e584780f2454ebaba3c9ea579)), closes [#69](https://github.com/derekwinters/roadtrip-android/issues/69)
* recover the family wizard from a closed bootstrap; drop avatar input ([#72](https://github.com/derekwinters/roadtrip-android/issues/72)) ([9b6a37e](https://github.com/derekwinters/roadtrip-android/commit/9b6a37e36e17e0809d35bba9901158bca5b70300)), closes [#71](https://github.com/derekwinters/roadtrip-android/issues/71)
* **trips:** collapse no-active-trip strip to a single line with New trip link ([#119](https://github.com/derekwinters/roadtrip-android/issues/119)) ([7b7cb75](https://github.com/derekwinters/roadtrip-android/commit/7b7cb75ed8db9d75d7cabf9c1f22b28bb6b575b8))


### Performance Improvements

* **ui:** move shell trip-state reads off the main thread ([#74](https://github.com/derekwinters/roadtrip-android/issues/74), AND-012) ([9284154](https://github.com/derekwinters/roadtrip-android/commit/9284154bcbb0402731dab3050b3cc5fb7fb21f18))
