package com.lunatech.keycloak.mappers.google;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.DirectoryScopes;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Groups;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

class GoogleClient {

    private final Directory directory;

    public GoogleClient(String applicationName, String delegateUser) throws IOException {
        directory = new Directory.Builder(new NetHttpTransport(), new JacksonFactory(), credential(delegateUser)).setApplicationName(applicationName).build();
    }

    private static HttpRequestInitializer credential(String delegateUser) throws IOException {
        List<String> scopes = List.of(DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY);

        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(scopes)
                .createDelegated(delegateUser);

        return new HttpCredentialsAdapter(credentials);
    }

    public List<String> getUsergroupNames(String userKey) {
        try {
            Groups groups = directory.groups().list().setUserKey(userKey).execute();
            return groups.getGroups().stream().map(Group::getName).collect(Collectors.toList());
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

}
