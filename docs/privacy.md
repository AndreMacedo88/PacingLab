# PacingLab privacy policy

Last updated: 30 October 2026

PacingLab is a training analysis app for Android. This policy explains what data it uses and where that
data goes.

## Data PacingLab reads

With your permission, PacingLab reads health and fitness data:

- From Health Connect on your phone: workouts, heart rate, speed, distance, steps, routes, heart rate
  variability, resting heart rate, breathing rate, oxygen saturation, VO2max, weight and sleep.
- From the Google Health API, only if you connect it in Settings > Data sources and sign in with your
  Google account: the nightly HRV, oxygen saturation, skin temperature, breathing rate and resting
  heart rate that the Fitbit app derives from your device, and its daily VO2max (cardio fitness
  score), read only.

PacingLab uses this data only to show you analyses of your own training, recovery and sleep.

## Where your data is kept

- Everything PacingLab reads or calculates is stored on your phone only.
- PacingLab has no server and no account, and it does not collect analytics or crash reports.
- Your health data is never sold, shared with third parties or used for advertising.
- Data from the Google Health API travels directly from Google to your phone.

PacingLab's use and transfer of information received from Google APIs adheres to the
[Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy),
including the Limited Use requirements.

## Network requests

Besides the Google Health API when you use it, PacingLab makes three kinds of requests, none of which
contains health data:

- Map tiles from [OpenFreeMap](https://openfreemap.org) to draw route maps. These requests reveal the
  map area being shown, as any map does.
- Weather forecasts from [Open-Meteo](https://open-meteo.com), only if you show the weather card and
  allow approximate location. The request contains your approximate position rounded to two decimal
  places (about 1 km) and nothing else. Your location is not stored, apart from the last forecast,
  which is kept on the phone.
- The weather during a session, from Open-Meteo, when you open a session with a route and unless you
  switch it off in Settings > Data. The request contains the start of the route rounded to two decimal
  places (about 1 km) and the session's date, once per session; the result is kept on the phone.

## Your control

- You can review or revoke PacingLab's access at any time in Health Connect's settings, and for the
  Google Health API in your Google account's security settings. Location access can be revoked in
  Android's app settings. Weather for sessions can be switched off in Settings > Data.
- The morning reminder is off until you switch it on in Settings > Sleep; it is a notification on
  your phone and sends nothing anywhere.
- Settings > Back up your entries saves what you entered by hand (heart rate values, tests, ratings
  and settings) to a file you choose; PacingLab never sends it anywhere.
- Uninstalling PacingLab deletes everything it stored on your phone.

## Contact

Questions about this policy: open an issue at
[github.com/AndreMacedo88/PacingLab/issues](https://github.com/AndreMacedo88/PacingLab/issues).
