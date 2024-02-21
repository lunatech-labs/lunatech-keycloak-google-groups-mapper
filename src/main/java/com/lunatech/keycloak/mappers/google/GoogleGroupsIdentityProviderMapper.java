package com.lunatech.keycloak.mappers.google;

import com.github.slugify.Slugify;
import org.keycloak.Config;
// import org.keycloak.broker.oidc.OIDCIdentityProviderFactory;
import org.keycloak.social.google.GoogleIdentityProviderFactory;
import org.keycloak.broker.provider.AbstractIdentityProviderMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.ProviderConfigProperty;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;

public class GoogleGroupsIdentityProviderMapper extends AbstractIdentityProviderMapper {
    protected static final List<ProviderConfigProperty> configProperties = new ArrayList<>();
    private static final String CONFIG_KEY_SERVICE_ACCOUNT_USER = "service-account-user";
    private static final String CONFIG_KEY_APPLICATION_NAME = "application-name";

    private static final String MAPPER_MODEL_KEY_PARENT_GROUP = "parentGroup";
    public static final String PROVIDER_ID = "google-groups-idp-mapper";

    private static final Set<IdentityProviderSyncMode> IDENTITY_PROVIDER_SYNC_MODES = new HashSet<>(Arrays.asList(IdentityProviderSyncMode.IMPORT, IdentityProviderSyncMode.FORCE));

    private GoogleClient googleClient;
    private Slugify slugify;

    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName(MAPPER_MODEL_KEY_PARENT_GROUP);
        property.setLabel("Parent Group");
        property.setHelpText("All imported groups will be created under this parent group.");
        property.setType(ProviderConfigProperty.GROUP_TYPE);
        configProperties.add(property);
    }

    public GoogleGroupsIdentityProviderMapper() {}

    public void init(Config.Scope config) {
        String serviceAccountUser = config.get(CONFIG_KEY_SERVICE_ACCOUNT_USER);

        if(serviceAccountUser == null) {
            // TODO, can we determine the key in a better way? Should we throw a different exception?
            throw new RuntimeException("Missing configuration key spi-identity-provider-mapper-" + PROVIDER_ID + "-" + CONFIG_KEY_SERVICE_ACCOUNT_USER);
        }

        String applicationName = config.get(CONFIG_KEY_APPLICATION_NAME, "keycloak");
        try {
            this.googleClient = new GoogleClient(applicationName, serviceAccountUser);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }

        this.slugify = Slugify.builder().build();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String[] getCompatibleProviders() {
        //return new String[]{ OIDCIdentityProviderFactory.PROVIDER_ID };
        return new String[]{ GoogleIdentityProviderFactory.PROVIDER_ID };
    }

    @Override
    public boolean supportsSyncMode(IdentityProviderSyncMode syncMode) {
        return IDENTITY_PROVIDER_SYNC_MODES.contains(syncMode);
    }

    @Override
    public String getDisplayCategory() {
        return "Google Workspace";
    }

    @Override
    public String getDisplayType() {
        return "Google Groups Importer";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public void importNewUser(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel, IdentityProviderMapperModel identityProviderMapperModel, BrokeredIdentityContext brokeredIdentityContext) {
        updateUserGroups(realmModel, userModel, identityProviderMapperModel);
    }

    @Override
    public void updateBrokeredUser(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel, IdentityProviderMapperModel identityProviderMapperModel, BrokeredIdentityContext brokeredIdentityContext) {
        updateUserGroups(realmModel, userModel, identityProviderMapperModel);
    }

    private void updateUserGroups(RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel) {
        GroupModel parentGroup = getParentGroup(realm, mapperModel);

        List<String> userGroupNames = googleClient.getUsergroupNames(user.getEmail());

        Set<String> targetGroups = userGroupNames.stream()
                .map(slugify::slugify)
                .collect(Collectors.toSet());

        HashSet<String> groupsToJoin = new HashSet<>(targetGroups);
        user.getGroupsStream().forEach(currentGroup -> {
            if(parentGroup.equals(currentGroup.getParent())) {
                if(targetGroups.contains(currentGroup.getName())) {
                    groupsToJoin.remove(currentGroup.getName());
                } else {
                    user.leaveGroup(currentGroup);
                }
            }
        });

        if(!groupsToJoin.isEmpty()) {
            Map<String, GroupModel> existingGroups = parentGroup.getSubGroupsStream()
                    .collect(Collectors.toMap(GroupModel::getName, identity()));

            groupsToJoin.forEach(groupName -> {
                GroupModel group = existingGroups.get(groupName);
                if(group == null) {
                    group = realm.createGroup(groupName, parentGroup);
                }
                user.joinGroup(group);
            });
        }
    }

    @Override
    public void updateBrokeredUserLegacy(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel, IdentityProviderMapperModel identityProviderMapperModel, BrokeredIdentityContext brokeredIdentityContext) {}

    @Override
    public String getHelpText() {
        return "Adds the user to all groups that the user is a member of in Google";
    }

    public GroupModel getParentGroup(RealmModel realm, IdentityProviderMapperModel mapperModel) {
        String groupPath = mapperModel.getConfig().get(MAPPER_MODEL_KEY_PARENT_GROUP);
        if(groupPath == null) {
            throw new RuntimeException("No parent group configured.");
        }
        return KeycloakModelUtils.findGroupByPath(realm, groupPath);
    }

}
