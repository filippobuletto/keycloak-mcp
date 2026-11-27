package io.github.filippobuletto.keycloakmcp.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.representations.idm.GroupRepresentation;

import io.github.filippobuletto.keycloakmcp.model.GroupDetail;
import io.github.filippobuletto.keycloakmcp.model.GroupSummary;
import io.github.filippobuletto.keycloakmcp.model.UserSummary;

@Singleton
public class GroupService {

    private static final int PAGE_SIZE = 100;

    @Inject
    @Caller
    Keycloak keycloak;

    public List<GroupSummary> list(String realm) {
        return KeycloakErrors.call(() -> {
            GroupsResource groups = keycloak.realm(realm).groups();
            List<GroupSummary> flat = new ArrayList<>();
            collect(groups, allPages((first, max) -> groups.groups(null, first, max, true), PAGE_SIZE), flat);
            return flat;
        });
    }

    public GroupDetail get(String realm, String path) {
        return KeycloakErrors.call(() -> detail(keycloak.realm(realm).getGroupByPath(path)));
    }

    public List<UserSummary> members(String realm, String path, int first, int max) {
        String id = groupId(realm, path);
        return KeycloakErrors.call(() -> keycloak.realm(realm).groups().group(id).members(first, max, true).stream()
                .map(UserService::summary)
                .toList());
    }

    public GroupDetail create(String realm, String name, String parentPath) {
        GroupRepresentation group = new GroupRepresentation();
        group.setName(name);
        GroupsResource groups = keycloak.realm(realm).groups();
        if (parentPath == null) {
            KeycloakErrors.checked(() -> groups.add(group));
            return get(realm, "/" + name);
        }
        String parentId = groupId(realm, parentPath);
        KeycloakErrors.checked(() -> groups.group(parentId).subGroup(group));
        return get(realm, parentPath + "/" + name);
    }

    public void addMember(String realm, String userId, String path) {
        String id = groupId(realm, path);
        KeycloakErrors.run(() -> keycloak.realm(realm).users().get(userId).joinGroup(id));
    }

    public void removeMember(String realm, String userId, String path) {
        String id = groupId(realm, path);
        KeycloakErrors.run(() -> keycloak.realm(realm).users().get(userId).leaveGroup(id));
    }

    public void delete(String realm, String path) {
        String id = groupId(realm, path);
        KeycloakErrors.run(() -> keycloak.realm(realm).groups().group(id).remove());
    }

    private String groupId(String realm, String path) {
        return KeycloakErrors.call(() -> keycloak.realm(realm).getGroupByPath(path).getId());
    }

    /** Keycloak returns subgroups lazily, so the tree is walked one level at a time. */
    private static void collect(GroupsResource groups, List<GroupRepresentation> level, List<GroupSummary> flat) {
        for (GroupRepresentation group : level) {
            flat.add(summary(group));
            if (count(group) > 0) {
                collect(groups, allPages((first, max) -> groups.group(group.getId()).getSubGroups(first, max, true),
                        PAGE_SIZE), flat);
            }
        }
    }

    /** Calls {@code fetch(first, max)} page by page until a page comes back short. */
    static <T> List<T> allPages(BiFunction<Integer, Integer, List<T>> fetch, int pageSize) {
        List<T> all = new ArrayList<>();
        List<T> page;
        do {
            page = fetch.apply(all.size(), pageSize);
            all.addAll(page);
        } while (page.size() == pageSize);
        return all;
    }

    static GroupSummary summary(GroupRepresentation group) {
        return new GroupSummary(group.getId(), group.getName(), group.getPath(), group.getSubGroupCount());
    }

    static GroupDetail detail(GroupRepresentation group) {
        return new GroupDetail(
                group.getId(),
                group.getName(),
                group.getPath(),
                count(group),
                group.getAttributes() == null ? Map.of() : group.getAttributes(),
                group.getRealmRoles() == null ? List.of() : group.getRealmRoles(),
                group.getClientRoles() == null ? Map.of() : group.getClientRoles());
    }

    private static long count(GroupRepresentation group) {
        return group.getSubGroupCount() == null ? 0 : group.getSubGroupCount();
    }
}
