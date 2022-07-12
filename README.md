# Keycloak Google Groups Identity Provider Mapper

This is a Keycloak extension that fetches Google Groups membership information from a user that authenticates through Google. 

## Usage

This identity provider mapper requires the following config in `keycloak.conf`:

    spi-identity-provider-mapper-google-groups-idp-mapper-service-account-user=<<email-address-of-user-to-impersonate>>

Additionally, it uses the _default_ authentication mechanism of the Google client libraries, which essentially means that you want to set an environment variable `GOOGLE_APPLICATION_CREDENTIALS` to the location of the JSON file you can download for a configured service client in the Google Developer console.

## Building

    mvn clean package

## Running locally

* Get a keycloak zip release, unpack it.
* Configure `keycloak.conf`, see *Usage*
* Run with `GOOGLE_APPLICATION_CREDENTIALS=<<path-to-json-credentials-file>> DEBUG=true DEBUG_SUSPEND=n ./kc.sh start-dev`

And then check out http://localhost:8080/. You can login with admin/admin.

## Releasing

We use the `maven-release-plugin` to publish to Github Packages, and then JReleaser to create a 'release' on GitHub.

Typically, you would run the following:

    mvn release:prepare release:perform
    mvn release:perform
    git checkout <<the release version>>
    JRELEASER_GITHUB_TOKEN=<<A GH Token>> mvn jreleaser:release

This is not really a great flow; properly combining these into one jrelease config would probably be better.