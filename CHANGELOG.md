# Changelog

Note the first digit of every adapter version corresponds to the major version of the Chartboost Mediation SDK compatible with that adapter. 
Adapters are compatible with any Chartboost Mediation SDK version within that major version.

All official releases can be found on this repository's [releases page](https://github.com/ChartBoost/chartboost-mediation-android-adapter-chartboost/releases).

## Table of Contents
- [Mediation 5](#mediation-5)
- [Mediation 4](#mediation-4)

## Mediation 5

### 5.10.0.0.0
- Update the adapter to use the new Chartboost SDK 10 APIs.
- GDPR consent is forwarded through the `CHB_GDPR_CONSENT` default SharedPreferences key the Chartboost SDK reads. It is written only when Chartboost Core reports a change and persists until the next change, so a publisher may also set that key directly. LGPD consent is not forwarded: Chartboost SDK 10 takes Brazilian consent from the IAB GPP string.
- The IAB TCF, GPP and US Privacy strings are no longer forwarded. Chartboost SDK 10 reads them from the default SharedPreferences file, where consent management platforms store them. Consents held only in memory by Chartboost Core do not reach it, so without such a platform the publisher must write `IABTCF_TCString`, `IABGPP_HDR_GppString`, `IABGPP_GppSID` and `IABUSPrivacy_String` there directly.
- The Chartboost Core `ccpa_opt_in` consent is no longer forwarded, since Chartboost SDK 10 only reads the IAB US Privacy string.
- Adaptive banners are requested with the given width and height limits, a height of 0 meaning no height limit. The Chartboost SDK validates the size on load, so an adaptive size with a width outside 50 to 1800 dp, or a height from 1 to 49 dp or above 1800 dp, now fails with an invalid banner size error where it used to load, and resolves the loaded size from the ad response, which the adapter reports as the partner banner size. The banner type follows the fit type of the loaded ad: fixed is a banner, aspect or fill is an adaptive banner.
- The underage flag from the Chartboost Mediation configuration is applied before the Chartboost SDK starts.
- The adapter no longer sets the Chartboost SDK log level to `ALL` on setup, so the level set through `Chartboost.setLoggingLevel` is kept.
- Load logs carry the load id the Chartboost SDK reports, and a successful load also logs the ad's price and currency.
- Fullscreen ads are destroyed once they close or expire, releasing their resources.
- Rewarded placements load and fetch bid tokens through the Chartboost SDK rewarded requests, so they load as rewarded ads and grant rewards.
- Bid tokens are fetched with the one synchronous `Chartboost.fetchBidToken` call, passing the interstitial, rewarded or banner request built from the ad format.
- Banners render on their own once Chartboost Mediation attaches them to a window, so the adapter no longer shows a banner after it loads.
- The new banner will appear, modal presented, modal dismissed and app left callbacks are logged and not forwarded, since Chartboost Mediation has no matching events.
- This version of the adapter has been certified with Chartboost SDK 10.0.0.

### 5.9.14.1.0
- This version of the adapter has been certified with Chartboost SDK 9.14.1.

### 5.9.14.0.0
- This version of the adapter has been certified with Chartboost SDK 9.14.0.
- Minimum Kotlin version bumped to 2.3.0.

### 5.9.13.0.0
- This version of the adapter has been certified with Chartboost SDK 9.13.0.

### 5.9.12.1.0
- This version of the adapter has been certified with Chartboost SDK 9.12.1.

### 5.9.12.0.0
- This version of the adapter has been certified with Chartboost SDK 9.12.0.

### 5.9.11.1.0
- This version of the adapter has been certified with Chartboost SDK 9.11.1.

### 5.9.11.0.0
- This version of the adapter has been certified with Chartboost SDK 9.11.0.

### 5.9.10.2.0
- This version of the adapter has been certified with Chartboost SDK 9.10.2.

### 5.9.10.1.0
- This version of the adapter has been certified with Chartboost SDK 9.10.1.

### 5.9.10.0.0
- This version of the adapter has been certified with Chartboost SDK 9.10.0.

### 5.9.9.3.0
- This version of the adapter has been certified with Chartboost SDK 9.9.3.

### 5.9.9.2.0
- This version of the adapter has been certified with Chartboost SDK 9.9.2.

### 5.9.9.1.0
- This version of the adapter has been certified with Chartboost SDK 9.9.1.
- `onAdExpired()` added to ad callbacks.

### 5.9.9.0.0
- This version of the adapter has been certified with Chartboost SDK 9.9.0.

### 5.9.8.3.0
- This version of the adapter has been certified with Chartboost SDK 9.8.3.

### 5.9.8.2.0
- This version of the adapter has been certified with Chartboost SDK 9.8.2.

### 5.9.8.1.0
- This version of the adapter has been certified with Chartboost SDK 9.8.1.

### 5.9.8.0.0
- This version of the adapter has been certified with Chartboost SDK 9.8.0.

### 5.9.7.0.0
- This version of the adapter has been certified with Chartboost SDK 9.7.0.
- This version of the adapter supports Chartboost Mediation SDK version 5.+.

## Mediation 4

### 4.9.8.0.0
- This version of the adapter has been certified with Chartboost SDK 9.8.0.

### 4.9.7.0.1
- Runs setUp() on IO context to reduce possibility of ANR terminations.

### 4.9.7.0.0
- This version of the adapter has been certified with Chartboost SDK 9.7.0.

### 4.9.6.1.1
- Fix memory leaks that could occur when fullscreen ads are shown from an `Activity`.

### 4.9.6.1.0
- This version of the adapter has been certified with Chartboost SDK 9.6.1.

### 4.9.6.0.0
- This version of the adapter has been certified with Chartboost SDK 9.6.0.

### 4.9.5.0.0
- This version of the adapter has been certified with Chartboost SDK 9.5.0.

### 4.9.4.1.2
- Updated to handle recent AdFormat changes.

### 4.9.4.1.1
- Guard against multiple continuation resumes.

### 4.9.4.1.0
- This version of the adapter has been certified with Chartboost SDK 9.4.1.

### 4.9.4.0.0
- This version of the adapter has been certified with Chartboost SDK 9.4.0.

### 4.9.3.1.1
- Guard against multiple continuation resumes.

### 4.9.3.1.0
- This version of the adapter has been certified with Chartboost SDK 9.3.1.

### 4.9.3.0.1
- Added ProGuard rules.

### 4.9.3.0.0
- This version of the adapter has been certified with Chartboost SDK 9.3.0.

### 4.9.2.1.2
- Prevents banner ads from getting stuck. Does not solve the underlying issue with Chartboost 9.2.1 that doesn't load a second bidding banner.

### 4.9.2.1.1
- Updated the dependency on Chartboost Mediation SDK to 4.0.0.

### 4.9.2.1.0
- This version of the adapter has been certified with Chartboost SDK 9.2.1.

### 4.9.2.0.1
- Updated the dependency on Chartboost Mediation SDK to 4.0.0.

### 4.9.2.0.0
- This version of the adapter has been certified with Chartboost SDK 9.2.0.
