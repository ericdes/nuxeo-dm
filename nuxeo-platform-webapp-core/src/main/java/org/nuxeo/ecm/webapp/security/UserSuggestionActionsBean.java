/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 *
 * $Id:  $
 */

package org.nuxeo.ecm.webapp.security;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.faces.application.FacesMessage;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.annotation.ejb.SerializedConcurrentAccess;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.web.RequestParameter;
import org.jboss.seam.faces.FacesMessages;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.directory.SizeLimitExceededException;
import org.nuxeo.ecm.platform.ui.web.component.list.UIEditableList;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.webapp.helpers.ResourcesAccessor;

/**
 * Methods to get user/groups suggestions from searches
 *
 * @author Anahide Tchertchian
 *
 */
@Name("userSuggestionActions")
@SerializedConcurrentAccess
public class UserSuggestionActionsBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(UserSuggestionActionsBean.class);

    public static final String USER_TYPE = "USER_TYPE";

    public static final String GROUP_TYPE = "GROUP_TYPE";

    public static final String TYPE_KEY_NAME = "type";

    public static final String PREFIXED_ID_KEY_NAME = "prefixed_id";

    public static final String ID_KEY_NAME = "id";

    public static final String ENTRY_KEY_NAME = "entry";

    @In(create = true)
    protected transient UserManager userManager;

    @In(create = true, required = false)
    protected transient FacesMessages facesMessages;

    @In(create = true)
    protected transient ResourcesAccessor resourcesAccessor;

    @RequestParameter
    protected String userSuggestionSearchType;

    @RequestParameter
    protected Integer userSuggestionMaxSearchResults;

    @RequestParameter
    protected String userSuggestionMessageId;

    /**
     * Id of the editable list component where selection ids are put.
     * <p>
     * Component must be an instance of {@link UIEditableList}
     */
    @RequestParameter
    protected String suggestionSelectionListId;

    protected void addSearchOverflowMessage() {
        if (userSuggestionMessageId != null) {
            facesMessages.addToControl(userSuggestionMessageId,
                    FacesMessage.SEVERITY_ERROR,
                    resourcesAccessor.getMessages().get(
                            "label.security.searchOverFlow"));
        } else {
            log.error("Search overflow");
        }
    }

    public List<DocumentModel> getGroupsSuggestions(Object input)
            throws ClientException {
        try {
            String pattern = (String) input;
            // XXX: this doesn't fetch group members (references)
            Map<String, Serializable> filter = new HashMap<String, Serializable>();
            if (pattern != null && pattern != "") {
                filter.put(userManager.getGroupIdField(), pattern);
            }
            return userManager.searchGroups(filter, (HashSet<String>) filter.keySet());
        } catch (SizeLimitExceededException e) {
            addSearchOverflowMessage();
            return Collections.emptyList();
        } catch (Exception e) {
            throw new ClientException("error searching for groups", e);
        }
    }

    public List<DocumentModel> getUserSuggestions(Object input)
            throws ClientException {
        try {
            String searchPattern = (String) input;
            return userManager.searchUsers(searchPattern);
        } catch (SizeLimitExceededException e) {
            addSearchOverflowMessage();
            return Collections.emptyList();
        } catch (Exception e) {
            throw new ClientException("error searching for principals", e);
        }
    }

    public Object getSuggestions(Object input) throws ClientException {
        List<DocumentModel> users = Collections.emptyList();
        if (USER_TYPE.equals(userSuggestionSearchType)
                || StringUtils.isEmpty(userSuggestionSearchType)) {
            users = getUserSuggestions(input);
        }

        List<DocumentModel> groups = Collections.emptyList();
        if (GROUP_TYPE.equals(userSuggestionSearchType)
                || StringUtils.isEmpty(userSuggestionSearchType)) {
            groups = getGroupsSuggestions(input);
        }

        int userSize = users.size();
        int groupSize = groups.size();
        int totalSize = userSize + groupSize;

        if (userSuggestionMaxSearchResults != null
                && userSuggestionMaxSearchResults > 0) {
            if (userSize > userSuggestionMaxSearchResults
                    || groupSize > userSuggestionMaxSearchResults
                    || totalSize > userSuggestionMaxSearchResults) {
                addSearchOverflowMessage();
                return null;
            }
        }

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(
                totalSize);

        for (DocumentModel user : users) {
            Map<String, Object> entry = new HashMap<String, Object>();
            entry.put(TYPE_KEY_NAME, USER_TYPE);
            entry.put(ENTRY_KEY_NAME, user);
            String userId = user.getId();
            entry.put(ID_KEY_NAME, userId);
            entry.put(PREFIXED_ID_KEY_NAME, NuxeoPrincipal.PREFIX + userId);
            result.add(entry);
        }

        for (DocumentModel group : groups) {
            Map<String, Object> entry = new HashMap<String, Object>();
            entry.put(TYPE_KEY_NAME, GROUP_TYPE);
            entry.put(ENTRY_KEY_NAME, group);
            String groupId = group.getId();
            entry.put(ID_KEY_NAME, groupId);
            entry.put(PREFIXED_ID_KEY_NAME, NuxeoGroup.PREFIX + groupId);
            result.add(entry);
        }

        return result;
    }

    // XXX: needs optimisation
    public Map<String, Object> getPrefixedUserInfo(String id)
            throws ClientException {
        Map<String, Object> res = new HashMap<String, Object>();
        res.put(PREFIXED_ID_KEY_NAME, id);
        if (id != null) {
            if (id.startsWith(NuxeoPrincipal.PREFIX)) {
                res.put(TYPE_KEY_NAME, USER_TYPE);
                String username = id.substring(NuxeoPrincipal.PREFIX.length());
                res.put(ID_KEY_NAME, username);
                res.put(ENTRY_KEY_NAME, userManager.getUserModel(username));
            } else if (id.startsWith(NuxeoGroup.PREFIX)) {
                res.put(TYPE_KEY_NAME, GROUP_TYPE);
                String groupname = id.substring(NuxeoGroup.PREFIX.length());
                res.put(ID_KEY_NAME, groupname);
                res.put(ENTRY_KEY_NAME, userManager.getGroupModel(groupname));
            } else {
                res.put(ID_KEY_NAME, id);
            }
        }
        return res;
    }

    // XXX: needs optimisation
    public Map<String, Object> getUserInfo(String id) throws ClientException {
        Map<String, Object> res = new HashMap<String, Object>();
        res.put(ID_KEY_NAME, id);
        if (userManager.getGroup(id) != null) {
            // group
            res.put(PREFIXED_ID_KEY_NAME, NuxeoGroup.PREFIX + id);
            res.put(TYPE_KEY_NAME, GROUP_TYPE);
            res.put(ENTRY_KEY_NAME, userManager.getGroupModel(id));
        } else {
            // user
            res.put(PREFIXED_ID_KEY_NAME, NuxeoPrincipal.PREFIX + id);
            res.put(TYPE_KEY_NAME, USER_TYPE);
            res.put(ENTRY_KEY_NAME, userManager.getUserModel(id));
        }
        return res;
    }

}
